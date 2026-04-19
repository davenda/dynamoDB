package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.IconLoader
import com.yourplugin.dynamodb.editor.DynamoTableVirtualFile
import icons.DatabaseIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.*

class DynamoMainPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    companion object {
        private const val TOOLBAR_LEADING_GAP = 4
        private const val TOOLBAR_ICON_GAP = 2
        private const val TOOLBAR_ICON_SIZE = 22
        private const val TOOLBAR_DIVIDER_WIDTH = 1
        private const val TOOLBAR_DIVIDER_HEIGHT = 16
        private const val TOOLBAR_DIVIDER_SIDE_GAP = 4
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val registry get() = ApplicationManager.getApplication()
        .getService(DynamoConnectionRegistry::class.java)

    // ── Toolbar buttons ───────────────────────────────────────────────────────
    private lateinit var editConnectionButton: JButton
    private lateinit var removeConnectionButton: JButton
    private lateinit var refreshConnectionButton: JButton
    private lateinit var createTableButton: JButton
    private lateinit var openInEditorButton: JButton

    // ── Tree model ────────────────────────────────────────────────────────────
    private val treeRoot  = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = DynamoTreeRenderer()
        addTreeSelectionListener { updateToolbarState() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) onTreeDoubleClick()
            }
            override fun mousePressed(e: MouseEvent)  { if (e.isPopupTrigger) showContextMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showContextMenu(e) }
        })
    }

    init {
        val wrapper = JPanel(BorderLayout())
        wrapper.add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH)
        wrapper.add(buildLeftPanel(), BorderLayout.CENTER)
        setContent(wrapper)
        refreshTree()
        updateToolbarState()
    }

    // ── Left panel ────────────────────────────────────────────────────────────

    private fun buildLeftPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(buildToolbar(), BorderLayout.NORTH)
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        return panel
    }

    private fun buildToolbar(): JComponent {
        val toolbar = JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

            add(Box.createHorizontalStrut(TOOLBAR_LEADING_GAP))
            add(iconBtn(AllIcons.General.Add, "New connection")       { showConnectDialog() })
            add(solidDivider())

            editConnectionButton = iconBtn(AllIcons.Actions.Edit, "Edit connection") { editSelectedConnection() }
            add(editConnectionButton)

            add(Box.createHorizontalStrut(TOOLBAR_ICON_GAP))
            removeConnectionButton = iconBtn(AllIcons.General.Remove, "Remove connection") { removeSelectedConnection() }
            add(removeConnectionButton)

            add(Box.createHorizontalStrut(TOOLBAR_ICON_GAP))
            refreshConnectionButton = iconBtn(AllIcons.Actions.Refresh, "Refresh") { refreshSelectedConnection() }
            add(refreshConnectionButton)

            add(solidDivider())
            createTableButton = iconBtn(AllIcons.Actions.NewFolder, "Create table") { showCreateTableDialog() }
            add(createTableButton)

            add(solidDivider())
            openInEditorButton = iconBtn(AllIcons.Actions.EditSource, "Open table in editor") { openSelectedTableInEditor() }
            add(openInEditorButton)
        }
        return toolbar
    }

    private fun iconBtn(icon: Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            disabledIcon = IconLoader.getDisabledIcon(icon)
            disabledSelectedIcon = disabledIcon
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            margin = java.awt.Insets(0, 0, 0, 0)
            preferredSize = java.awt.Dimension(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE)
            maximumSize = java.awt.Dimension(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE)
            minimumSize = java.awt.Dimension(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE)
            addActionListener { action() }
        }

    private fun solidDivider(): JComponent {
        val sep = JSeparator(SwingConstants.VERTICAL)
        val totalWidth = TOOLBAR_DIVIDER_SIDE_GAP * 2 + TOOLBAR_DIVIDER_WIDTH
        sep.preferredSize = java.awt.Dimension(totalWidth, TOOLBAR_ICON_SIZE)
        sep.minimumSize  = java.awt.Dimension(totalWidth, TOOLBAR_ICON_SIZE)
        sep.maximumSize  = java.awt.Dimension(totalWidth, TOOLBAR_ICON_SIZE)
        return sep
    }

    // ── Tree helpers ──────────────────────────────────────────────────────────

    /** Selected connection node, or null */
    private fun selectedConnectionNode(): DefaultMutableTreeNode? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return when (val ud = node.userObject) {
            is ConnectionNode -> node
            is TableNode      -> node.parent as? DefaultMutableTreeNode
            else              -> null
        }
    }

    private fun selectedConnectionName(): String? =
        (selectedConnectionNode()?.userObject as? ConnectionNode)?.name

    private fun selectedTableNode(): DefaultMutableTreeNode? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return if (node.userObject is TableNode) node else null
    }

    // ── Tree data ─────────────────────────────────────────────────────────────

    /** Full rebuild of tree from registry */
    private fun refreshTree() {
        treeRoot.removeAllChildren()
        registry.allConnections().forEach { conn ->
            val connNode = DefaultMutableTreeNode(ConnectionNode(conn.name, loading = true))
            treeRoot.add(connNode)
        }
        treeModel.reload()
        // Expand all connection nodes and load their tables
        for (i in 0 until treeRoot.childCount) {
            val connNode = treeRoot.getChildAt(i) as DefaultMutableTreeNode
            val path = TreePath(arrayOf(treeRoot, connNode))
            tree.expandPath(path)
            loadTablesIntoNode(connNode)
        }
    }

    private fun loadTablesIntoNode(connNode: DefaultMutableTreeNode) {
        val connName = (connNode.userObject as? ConnectionNode)?.name ?: return
        scope.launch {
            val tables = runCatching {
                TableSchemaService.listTables(registry.clientFor(connName))
            }.getOrElse {
                withContext(Dispatchers.Swing) {
                    connNode.userObject = ConnectionNode(connName, error = true)
                    treeModel.nodeChanged(connNode)
                }
                return@launch
            }

            withContext(Dispatchers.Swing) {
                connNode.removeAllChildren()
                connNode.userObject = ConnectionNode(connName)
                tables.forEach { tableName ->
                    connNode.add(DefaultMutableTreeNode(TableNode(tableName, connName)))
                }
                treeModel.reload(connNode)
                tree.expandPath(TreePath(arrayOf<Any>(treeRoot, connNode)))
            }
        }
    }

    // ── Tree interaction ──────────────────────────────────────────────────────

    private fun onTreeDoubleClick() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        when (val ud = node.userObject) {
            is TableNode -> openTableInEditor(ud.tableName, ud.connectionName)
            is ConnectionNode -> {
                // Toggle expand/collapse on double-click
                val path = tree.selectionPath ?: return
                if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
            }
        }
    }

    private fun showContextMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        tree.selectionPath = path
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val menu = JPopupMenu()

        when (val ud = node.userObject) {
            is ConnectionNode -> {
                menu.add(JMenuItem("New connection…").apply   { addActionListener { showConnectDialog() } })
                menu.add(JMenuItem("Edit connection…").apply  { addActionListener { editSelectedConnection() } })
                menu.add(JMenuItem("Refresh").apply           { addActionListener { loadTablesIntoNode(node) } })
                menu.addSeparator()
                menu.add(JMenuItem("Create table…").apply     { addActionListener { showCreateTableDialog() } })
                menu.addSeparator()
                menu.add(JMenuItem("Remove connection").apply { addActionListener { removeSelectedConnection() } })
            }
            is TableNode -> {
                menu.add(JMenuItem("Open").apply { addActionListener { openTableInEditor(ud.tableName, ud.connectionName) } })
                menu.addSeparator()
                menu.add(JMenuItem("Delete table from AWS…").apply { addActionListener { deleteTable(ud.tableName, ud.connectionName) } })
            }
        }
        menu.show(tree, e.x, e.y)
    }

    // ── Connection actions ────────────────────────────────────────────────────

    private fun showConnectDialog() {
        if (ConnectDialog(project, registry).showAndGet()) refreshTree()
    }

    private fun editSelectedConnection() {
        val connName = selectedConnectionName() ?: return
        val config = registry.allConnections().firstOrNull { it.name == connName } ?: return
        if (EditConnectionDialog(project, registry, config).showAndGet()) refreshTree()
    }

    private fun removeSelectedConnection() {
        val connName = selectedConnectionName() ?: return
        val confirm = Messages.showYesNoDialog(
            project,
            "Remove connection \"$connName\"?\nThis only removes it from the plugin.",
            "Remove Connection", Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return
        registry.removeConnection(connName)
        closeEditorsForConnection(connName)
        refreshTree()
    }

    private fun refreshSelectedConnection() {
        val node = selectedConnectionNode() ?: run {
            // No selection — refresh all
            refreshTree(); return
        }
        loadTablesIntoNode(node)
    }

    private fun showCreateTableDialog() {
        val connName = selectedConnectionName()
            ?: registry.allConnections().firstOrNull()?.name
            ?: run { Messages.showInfoMessage(project, "Connect to AWS first.", "No Connection"); return }
        val connNode = (0 until treeRoot.childCount)
            .map { treeRoot.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { (it.userObject as? ConnectionNode)?.name == connName } ?: return
        CreateTableDialog(project, registry.clientFor(connName)) { newTableName ->
            SwingUtilities.invokeLater {
                connNode.add(DefaultMutableTreeNode(TableNode(newTableName, connName)))
                treeModel.reload(connNode)
            }
        }.show()
    }

    private fun openSelectedTableInEditor() {
        val tableNode = (selectedTableNode()?.userObject as? TableNode) ?: return
        openTableInEditor(tableNode.tableName, tableNode.connectionName)
    }

    private fun openTableInEditor(tableName: String, connName: String) {
        val file = DynamoTableVirtualFile.findOrCreate(project, connName, tableName)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    // ── Table actions ─────────────────────────────────────────────────────────

    private fun deleteTable(tableName: String, connName: String) {
        val confirm = Messages.showYesNoDialog(
            project, "Permanently delete \"$tableName\" from AWS?\n\nThis cannot be undone.",
            "Delete Table", Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return
        val typed = Messages.showInputDialog(project, "Type the table name to confirm:", "Confirm Delete", Messages.getWarningIcon())
        if (typed != tableName) { Messages.showInfoMessage(project, "Cancelled.", "Cancelled"); return }

        scope.launch {
            val result = runCatching {
                registry.clientFor(connName).deleteTable(DeleteTableRequest.builder().tableName(tableName).build()).await()
            }
            withContext(Dispatchers.Swing) {
                if (result.isSuccess) {
                    // Remove from tree
                    val connNode = (0 until treeRoot.childCount)
                        .map { treeRoot.getChildAt(it) as DefaultMutableTreeNode }
                        .firstOrNull { (it.userObject as? ConnectionNode)?.name == connName }
                    connNode?.let { cn ->
                        (0 until cn.childCount)
                            .map { cn.getChildAt(it) as DefaultMutableTreeNode }
                            .firstOrNull { (it.userObject as? TableNode)?.tableName == tableName }
                            ?.let { treeModel.removeNodeFromParent(it) }
                    }
                    closeEditorForTable(connName, tableName)
                } else {
                    Messages.showErrorDialog(project, "Failed: ${result.exceptionOrNull()?.message}", "Error")
                }
            }
        }
    }

    // ── Tree renderer ─────────────────────────────────────────────────────────

    private inner class DynamoTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            isOpaque = false
            background = null
            backgroundNonSelectionColor = null
            backgroundSelectionColor = null
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val ud = node.userObject) {
                is ConnectionNode -> {
                    text = ud.name
                    icon = when {
                        ud.error   -> AllIcons.General.Error
                        ud.loading -> AllIcons.General.InspectionsEye
                        else       -> DatabaseIcons.Database
                    }
                    font = font.deriveFont(Font.BOLD)
                }
                is TableNode -> {
                    text = ud.tableName
                    icon = DatabaseIcons.Table
                    font = font.deriveFont(Font.PLAIN)
                }
            }
            return this
        }
    }

    // ── Node data classes ─────────────────────────────────────────────────────

    data class ConnectionNode(val name: String, val loading: Boolean = false, val error: Boolean = false)
    data class TableNode(val tableName: String, val connectionName: String)

    // ── Expand / Collapse all ─────────────────────────────────────────────────

    fun expandAll() {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i)
            i++
        }
    }

    fun collapseAll() {
        for (i in treeRoot.childCount - 1 downTo 0) {
            val connNode = treeRoot.getChildAt(i) as DefaultMutableTreeNode
            tree.collapsePath(TreePath(arrayOf<Any>(treeRoot, connNode)))
        }
    }

    fun dispose() = scope.cancel()

    private fun updateToolbarState() {
        val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val hasConnectionSelection = selectedNode?.userObject is ConnectionNode
        val hasTableSelection = selectedNode?.userObject is TableNode

        editConnectionButton.isEnabled = hasConnectionSelection
        removeConnectionButton.isEnabled = hasConnectionSelection
        refreshConnectionButton.isEnabled = hasConnectionSelection
        createTableButton.isEnabled = hasConnectionSelection
        openInEditorButton.isEnabled = hasTableSelection
    }

    private fun closeEditorsForConnection(connectionName: String) {
        val editorManager = FileEditorManager.getInstance(project)
        editorManager.openFiles
            .filterIsInstance<DynamoTableVirtualFile>()
            .filter { it.connectionName == connectionName }
            .forEach(editorManager::closeFile)
    }

    private fun closeEditorForTable(connectionName: String, tableName: String) {
        val editorManager = FileEditorManager.getInstance(project)
        editorManager.openFiles
            .filterIsInstance<DynamoTableVirtualFile>()
            .firstOrNull { it.connectionName == connectionName && it.tableName == tableName }
            ?.let(editorManager::closeFile)
    }
}
