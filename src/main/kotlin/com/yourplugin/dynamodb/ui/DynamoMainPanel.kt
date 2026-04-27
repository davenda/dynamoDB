package com.yourplugin.dynamodb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.yourplugin.dynamodb.editor.DynamoTableVirtualFile
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode

class DynamoMainPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val registry
        get() = ApplicationManager.getApplication().getService(DynamoConnectionRegistry::class.java)

    private lateinit var sidebar: SidebarPanel

    init {
        sidebar = SidebarPanel(
            project      = project,
            registry     = registry,
            scope        = scope,
            onTableSelected    = { tableName, connName -> openTableInEditor(tableName, connName) },
            onContextMenu      = { node, x, y -> showContextMenu(node, x, y) },
            onAddConnection    = { showConnectDialog() },
            onEditConnection   = { sidebar.selectedConnectionName()?.let { editConnection(it) } },
            onRemoveConnection = { sidebar.selectedConnectionName()?.let { removeConnection(it) } },
            onRefreshSelected  = {
                sidebar.selectedConnectionNode()?.let { sidebar.loadTables(it) } ?: sidebar.refreshTree()
            },
            onCreateTable      = {
                val conn = sidebar.selectedConnectionName()
                    ?: registry.allConnections().firstOrNull()?.name
                    ?: run { Messages.showInfoMessage(project, "Connect to AWS first.", "No Connection"); return@SidebarPanel }
                showCreateTableDialog(conn)
            },
            onDeleteTable      = {
                val tbl  = sidebar.selectedTableName()
                val conn = sidebar.selectedConnectionName()
                if (tbl != null && conn != null) deleteTable(tbl, conn)
            },
        )

        val wrapper = JPanel(java.awt.BorderLayout())
        wrapper.add(JSeparator(SwingConstants.HORIZONTAL), java.awt.BorderLayout.NORTH)
        wrapper.add(sidebar, java.awt.BorderLayout.CENTER)
        setContent(wrapper)
    }

    // ── Open table in editor ──────────────────────────────────────────────────

    private fun openTableInEditor(tableName: String, connName: String) {
        val file = DynamoTableVirtualFile.findOrCreate(project, connName, tableName)
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    // ── Right-click context menu ──────────────────────────────────────────────

    private fun showContextMenu(node: DefaultMutableTreeNode, x: Int, y: Int) {
        val menu = JPopupMenu()
        when (val ud = node.userObject) {
            is SidebarPanel.ConnectionNode -> {
                menu.add(item("New connection…")   { showConnectDialog() })
                menu.add(item("Edit connection…")  { editConnection(ud.name) })
                menu.add(item("Refresh")           { sidebar.loadTables(node) })
                menu.addSeparator()
                menu.add(item("Create table…")     { showCreateTableDialog(ud.name) })
                menu.addSeparator()
                menu.add(item("Remove connection") { removeConnection(ud.name) })
            }
            is SidebarPanel.TableNode -> {
                menu.add(item("Open")          { openTableInEditor(ud.tableName, ud.connectionName) })
                menu.addSeparator()
                menu.add(item("Delete table…") { deleteTable(ud.tableName, ud.connectionName) })
            }
        }
        menu.show(sidebar.tree, x, y)
    }

    private fun item(text: String, action: () -> Unit) =
        JMenuItem(text).also { it.addActionListener { action() } }

    // ── Connection actions ────────────────────────────────────────────────────

    private fun showConnectDialog() {
        if (ConnectDialog(project, registry).showAndGet()) sidebar.refreshTree()
    }

    private fun editConnection(connName: String) {
        val config = registry.allConnections().firstOrNull { it.name == connName } ?: return
        if (EditConnectionDialog(project, registry, config).showAndGet()) sidebar.refreshTree()
    }

    private fun removeConnection(connName: String) {
        val ok = Messages.showYesNoDialog(
            project,
            "Remove connection \"$connName\"?\nThis only removes it from the plugin.",
            "Remove Connection", Messages.getQuestionIcon()
        )
        if (ok != Messages.YES) return
        registry.removeConnection(connName)
        closeEditorsForConnection(connName)
        sidebar.refreshTree()
    }

    private fun showCreateTableDialog(connName: String) {
        CreateTableDialog(project, registry.clientFor(connName)) { newTableName ->
            SwingUtilities.invokeLater { sidebar.addTableNode(connName, newTableName) }
        }.show()
    }

    private fun deleteTable(tableName: String, connName: String) {
        val ok = Messages.showYesNoDialog(
            project,
            "Permanently delete \"$tableName\" from AWS?\n\nThis cannot be undone.",
            "Delete Table", Messages.getWarningIcon()
        )
        if (ok != Messages.YES) return
        val typed = Messages.showInputDialog(
            project, "Type the table name to confirm:", "Confirm Delete", Messages.getWarningIcon()
        )
        if (typed != tableName) { Messages.showInfoMessage(project, "Cancelled.", "Cancelled"); return }

        scope.launch {
            val result = runCatching {
                registry.clientFor(connName)
                    .deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
                    .await()
            }
            withContext(Dispatchers.Swing) {
                if (result.isSuccess) {
                    sidebar.removeTableNode(connName, tableName)
                    closeEditorForTable(connName, tableName)
                } else {
                    Messages.showErrorDialog(project, "Failed: ${result.exceptionOrNull()?.message}", "Error")
                }
            }
        }
    }

    // ── Editor cleanup ────────────────────────────────────────────────────────

    private fun closeEditorsForConnection(connName: String) {
        val em = FileEditorManager.getInstance(project)
        em.openFiles.filterIsInstance<DynamoTableVirtualFile>()
            .filter { it.connectionName == connName }
            .forEach(em::closeFile)
    }

    private fun closeEditorForTable(connName: String, tableName: String) {
        val em = FileEditorManager.getInstance(project)
        em.openFiles.filterIsInstance<DynamoTableVirtualFile>()
            .firstOrNull { it.connectionName == connName && it.tableName == tableName }
            ?.let(em::closeFile)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun expandAll()   = sidebar.expandAll()
    fun collapseAll() = sidebar.collapseAll()
    fun dispose()     = scope.cancel()
}
