package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.MatteBorder
import javax.swing.tree.*

class SidebarPanel(
    private val project: Project,
    private val registry: DynamoConnectionRegistry,
    private val scope: CoroutineScope,
    val onTableSelected: (tableName: String, connectionName: String) -> Unit,
    val onContextMenu: (node: DefaultMutableTreeNode, x: Int, y: Int) -> Unit,
    val onAddConnection:    () -> Unit = {},
    val onEditConnection:   () -> Unit = {},
    val onRemoveConnection: () -> Unit = {},
    val onRefreshSelected:  () -> Unit = {},
    val onCreateTable:      () -> Unit = {},
    val onDeleteTable:      () -> Unit = {},
) : JPanel(BorderLayout()) {

    companion object { const val WIDTH = 280 }

    // ── Icons ─────────────────────────────────────────────────────────────────
    /** DynamoDB lightning-bolt icon scaled to 16×16 and tinted to match the UI theme. */
    private val connIcon: Icon = object : Icon {
        private val cached = runCatching {
            val url = SidebarPanel::class.java.getResource("/icons/dynamodb.png")!!
            val raw = javax.imageio.ImageIO.read(url)
            val scaled = raw.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH)
            val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(scaled, 0, 0, null); g.dispose()
            img
        }.getOrNull()

        override fun getIconWidth()  = 16
        override fun getIconHeight() = 16
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            if (cached == null) { AllIcons.Nodes.DataSchema.paintIcon(c, g, x, y); return }
            val out = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g2  = out.createGraphics()
            g2.drawImage(cached, 0, 0, null)
            // tint: paint the foreground colour over the icon's opaque pixels
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1f)
            g2.color = DColors.fg1
            g2.fillRect(0, 0, 16, 16)
            g2.dispose()
            g.drawImage(out, x, y, c)
        }
    }

    /** Table icon tinted with the accent colour. */
    private val tableIcon: Icon = object : Icon {
        private val base = AllIcons.Nodes.DataTables
        override fun getIconWidth()  = base.iconWidth
        override fun getIconHeight() = base.iconHeight
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val img   = java.awt.image.BufferedImage(iconWidth, iconHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val ig    = img.createGraphics()
            base.paintIcon(c, ig, 0, 0)
            ig.composite = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.85f)
            ig.color = DColors.accent
            ig.fillRect(0, 0, iconWidth, iconHeight)
            ig.dispose()
            g.drawImage(img, x, y, c)
        }
    }

    // ── Active / recent state ─────────────────────────────────────────────────
    var activeTable:      String? = null
    var activeConnection: String? = null
    private val recentlyOpened = mutableListOf<Pair<String, String>>()

    data class HistoryEntry(
        val query: String, val tableName: String,
        val ms: Long, val ts: Long = System.currentTimeMillis(),
    )

    // ── Node data classes ─────────────────────────────────────────────────────
    data class ConnectionNode(val name: String, val region: String = "", val label: String = "",
                              val loading: Boolean = false, val error: Boolean = false)
    data class TableNode(val tableName: String, val connectionName: String, val itemCount: Long? = null)

    private fun connectionLabel(conn: DynamoConnectionRegistry.ConnectionConfig): String {
        conn.endpointOverride?.let { ep ->
            return runCatching {
                val u = java.net.URI.create(ep)
                val port = if (u.port > 0) ":${u.port}" else ""
                "${u.host}$port"
            }.getOrElse { ep }
        }
        conn.profileName?.let { return it }
        return conn.region
    }

    // ── Data model (JTree hidden — used only for data storage & context menus) ─
    private val treeRoot  = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(treeRoot)
    val tree = JTree(treeModel).apply { isVisible = false }

    // ── Visual list (replaces JTree rendering) ────────────────────────────────
    private val listPanel = JPanel().apply {
        layout     = BoxLayout(this, BoxLayout.Y_AXIS)
        background = DColors.bg1
        alignmentX = Component.LEFT_ALIGNMENT
    }

    // Track which connections are expanded and which node is selected
    private val expandedConns  = mutableSetOf<String>()
    private var selConnNode: DefaultMutableTreeNode? = null

    // ── Recently-opened panel ─────────────────────────────────────────────────
    private val recentPanel = JPanel().apply {
        layout     = BoxLayout(this, BoxLayout.PAGE_AXIS)
        background = DColors.bg1
        isVisible  = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    // ── Search ────────────────────────────────────────────────────────────────
    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", "Search tables…")
        font       = Font(font.name, Font.PLAIN, 12)
        foreground = DColors.fg0
        border     = BorderFactory.createEmptyBorder(0, 4, 0, 4)
        isOpaque   = false
        background = null
    }
    private val searchClearBtn = JButton("×").apply {
        font               = font.deriveFont(Font.PLAIN, 13f)
        foreground         = DColors.fg3
        isBorderPainted    = false
        isContentAreaFilled = false
        isFocusPainted     = false
        preferredSize      = Dimension(20, 20)
        maximumSize        = Dimension(20, 20)
        isVisible          = false
        addActionListener  { searchField.text = ""; isVisible = false }
    }

    private val tableCache    = mutableMapOf<String, List<String>>()
    private val connCountPill = object : JLabel(" 0 conn ") {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = DColors.bg3; g2.fillRoundRect(0, 1, width, height - 2, 10, 10)
            super.paintComponent(g)
        }
    }.apply { font = font.deriveFont(Font.PLAIN, 10f); foreground = DColors.fg2; isOpaque = false }

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        background    = DColors.bg1
        preferredSize = Dimension(WIDTH, 0)
        minimumSize   = Dimension(120, 0)
        border        = MatteBorder(0, 0, 0, 1, DColors.line)

        listPanel.alignmentX = Component.LEFT_ALIGNMENT
        recentPanel.alignmentX = Component.LEFT_ALIGNMENT

        val scrollContent = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = DColors.bg1
            add(listPanel)
            add(recentPanel)
        }
        val treeScroll = JScrollPane(scrollContent).apply {
            border = null; background = DColors.bg1; viewport.background = DColors.bg1
        }
        val body = JPanel(BorderLayout()).apply {
            background = DColors.bg1
            add(buildSearch(), BorderLayout.NORTH)
            add(treeScroll,    BorderLayout.CENTER)
        }
        add(buildTwToolbar(), BorderLayout.NORTH)
        add(body,             BorderLayout.CENTER)

        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent)  = onSearchChanged()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent)  = onSearchChanged()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onSearchChanged()
        })

        refreshTree()
    }

    // ── List rebuild ──────────────────────────────────────────────────────────

    private fun rebuildList(filter: String = "") {
        listPanel.removeAll()
        for (i in 0 until treeRoot.childCount) {
            val connNode = treeRoot.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val ud = connNode.userObject as? ConnectionNode ?: continue

            // Filter: skip connections with no matching tables (when filtering)
            val tables = if (filter.isEmpty()) {
                (0 until connNode.childCount).mapNotNull {
                    (connNode.getChildAt(it) as? DefaultMutableTreeNode)?.userObject as? TableNode
                }
            } else {
                (0 until connNode.childCount).mapNotNull {
                    (connNode.getChildAt(it) as? DefaultMutableTreeNode)?.userObject as? TableNode
                }.filter { it.tableName.contains(filter, ignoreCase = true) }
            }
            if (filter.isNotEmpty() && tables.isEmpty()) continue

            listPanel.add(connRow(connNode, ud))

            if (ud.name in expandedConns) {
                for (j in 0 until connNode.childCount) {
                    val tn  = connNode.getChildAt(j) as? DefaultMutableTreeNode ?: continue
                    val tud = tn.userObject as? TableNode ?: continue
                    if (filter.isNotEmpty() && !tud.tableName.contains(filter, ignoreCase = true)) continue
                    listPanel.add(tableRow(tn, tud))
                }
            }
        }
        listPanel.revalidate()
        listPanel.repaint()
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    private fun connRow(node: DefaultMutableTreeNode, ud: ConnectionNode): JPanel {
        val isSelected = selConnNode == node
        val isExpanded = ud.name in expandedConns
        var hovered = false

        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = DColors.bg1
                g.fillRect(0, 0, width, height)
                val bg: Color? = when {
                    isSelected -> DColors.accentSoft
                    hovered    -> DColors.bg2
                    else       -> null
                }
                if (bg != null) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = bg
                    g2.fillRoundRect(4, 2, width - 8, height - 4, 8, 8)
                }
            }
        }.apply {
            isOpaque   = false
            border      = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            alignmentX  = Component.LEFT_ALIGNMENT

            // WEST: chevron + icon + name
            add(JPanel().apply {
                layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque = false

                // ▾ / ▸ expander
                add(JLabel(if (isExpanded) "▾" else "▸").apply {
                    font       = font.deriveFont(Font.PLAIN, 11f)
                    foreground = DColors.fg2
                    preferredSize = Dimension(14, 16)
                    maximumSize   = Dimension(14, 16)
                    alignmentY = Component.CENTER_ALIGNMENT
                })
                add(Box.createHorizontalStrut(4))

                // connection icon
                add(JLabel(when {
                    ud.error   -> AllIcons.General.Error
                    ud.loading -> AllIcons.General.Gear
                    else       -> connIcon
                }).apply { alignmentY = Component.CENTER_ALIGNMENT })
                add(Box.createHorizontalStrut(6))

                // name
                add(JLabel(when {
                    ud.error   -> "⚠ ${ud.name}"
                    ud.loading -> "${ud.name}  …"
                    else       -> ud.name
                }).apply {
                    font       = font.deriveFont(Font.BOLD, 12f)
                    foreground = if (ud.error) DColors.bad else DColors.fg0
                    alignmentY = Component.CENTER_ALIGNMENT
                })
            }, BorderLayout.WEST)

            // EAST: ● label
            if (!ud.error && !ud.loading && ud.label.isNotEmpty()) {
                add(JPanel().apply {
                    layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
                    isOpaque = false
                    add(JLabel("●").apply {
                        font       = font.deriveFont(7f)
                        foreground = DColors.good
                        alignmentY = Component.CENTER_ALIGNMENT
                    })
                    add(Box.createHorizontalStrut(4))
                    add(JLabel(ud.label).apply {
                        font       = Font(Font.MONOSPACED, Font.PLAIN, 10)
                        foreground = DColors.fg3
                        alignmentY = Component.CENTER_ALIGNMENT
                    })
                }, BorderLayout.EAST)
            }

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isPopupTrigger) return
                    selConnNode = node
                    // toggle expand / collapse
                    if (ud.name in expandedConns) expandedConns.remove(ud.name)
                    else expandedConns.add(ud.name)
                    rebuildList(searchField.text.trim())
                }
                override fun mousePressed(e: MouseEvent)  { maybeMenu(e) }
                override fun mouseReleased(e: MouseEvent) { maybeMenu(e) }
                private fun maybeMenu(e: MouseEvent) {
                    if (!e.isPopupTrigger) return
                    selConnNode = node; onContextMenu(node, e.x, e.y)
                }
                override fun mouseEntered(ev: MouseEvent) { hovered = true;  repaint() }
                override fun mouseExited(ev:  MouseEvent) { hovered = false; repaint() }
            })
        }
    }

    private fun tableRow(node: DefaultMutableTreeNode, ud: TableNode): JPanel {
        val isActive = ud.tableName == activeTable && ud.connectionName == activeConnection
        var hovered = false

        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = DColors.bg1
                g.fillRect(0, 0, width, height)
                val bg: Color? = when {
                    isActive -> DColors.accentRow
                    hovered  -> DColors.bg2
                    else     -> null
                }
                if (bg != null) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = bg
                    // left edge indented to match the 36px text indent (start at x=26)
                    g2.fillRoundRect(26, 2, width - 30, height - 4, 8, 8)
                }
            }
        }.apply {
            isOpaque   = false
            border      = BorderFactory.createEmptyBorder(6, 36, 6, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            alignmentX  = Component.LEFT_ALIGNMENT

            // WEST: icon + name
            add(JPanel().apply {
                layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque = false
                add(JLabel(tableIcon).apply {
                    alignmentY = Component.CENTER_ALIGNMENT
                })
                add(Box.createHorizontalStrut(6))
                add(JLabel(ud.tableName).apply {
                    font       = if (isActive) font.deriveFont(Font.BOLD, 12f)
                                 else          font.deriveFont(Font.PLAIN, 12f)
                    foreground = if (isActive) DColors.accent else DColors.fg1
                    alignmentY = Component.CENTER_ALIGNMENT
                })
            }, BorderLayout.WEST)

            // EAST: item count
            if (ud.itemCount != null) {
                add(JLabel(formatCount(ud.itemCount)).apply {
                    font       = font.deriveFont(Font.PLAIN, 10.5f)
                    foreground = DColors.fg3
                    alignmentY = Component.CENTER_ALIGNMENT
                }, BorderLayout.EAST)
            }

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isPopupTrigger) return
                    if (e.clickCount == 2) {
                        activeTable = ud.tableName; activeConnection = ud.connectionName
                        trackRecent(ud.tableName, ud.connectionName)
                        rebuildList(searchField.text.trim())
                        onTableSelected(ud.tableName, ud.connectionName)
                    }
                }
                override fun mousePressed(e: MouseEvent)  { maybeMenu(e) }
                override fun mouseReleased(e: MouseEvent) { maybeMenu(e) }
                private fun maybeMenu(e: MouseEvent) {
                    if (!e.isPopupTrigger) return
                    selConnNode = node.parent as? DefaultMutableTreeNode
                    onContextMenu(node, e.x, e.y)
                }
                override fun mouseEntered(ev: MouseEvent) { hovered = true;  repaint() }
                override fun mouseExited(ev:  MouseEvent) { hovered = false; repaint() }
            })
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun onSearchChanged() {
        val q = searchField.text.trim()
        searchClearBtn.isVisible = q.isNotEmpty()
        rebuildList(q)
    }

    // kept for compatibility — delegates to rebuildList
    private fun filterTree(q: String) = rebuildList(q)
    private fun refreshTreeFromCache() = rebuildList(searchField.text.trim())

    // ── Tree management ───────────────────────────────────────────────────────

    fun refreshTree() {
        tableCache.clear()
        expandedConns.clear()
        treeRoot.removeAllChildren()
        val conns = registry.allConnections()
        connCountPill.text = " ${conns.size} conn "
        conns.forEach { conn ->
            expandedConns.add(conn.name)
            val node = DefaultMutableTreeNode(
                ConnectionNode(conn.name, conn.region, label = connectionLabel(conn), loading = true))
            treeRoot.add(node)
        }
        treeModel.reload()
        rebuildList()
        for (i in 0 until treeRoot.childCount) {
            loadTables(treeRoot.getChildAt(i) as DefaultMutableTreeNode)
        }
    }

    fun loadTables(connNode: DefaultMutableTreeNode) {
        val conn = (connNode.userObject as? ConnectionNode) ?: return
        scope.launch {
            val tables = runCatching {
                TableSchemaService.listTables(registry.clientFor(conn.name))
            }.getOrElse {
                withContext(Dispatchers.Swing) {
                    connNode.userObject = ConnectionNode(conn.name, conn.region, label = conn.label, error = true)
                    treeModel.nodeChanged(connNode)
                    rebuildList(searchField.text.trim())
                }
                return@launch
            }
            withContext(Dispatchers.Swing) {
                tableCache[conn.name] = tables
                connNode.removeAllChildren()
                connNode.userObject = ConnectionNode(conn.name, conn.region, label = conn.label)
                tables.forEach { connNode.add(DefaultMutableTreeNode(TableNode(it, conn.name))) }
                treeModel.reload(connNode)
                rebuildList(searchField.text.trim())
            }
            tables.forEach { tableName ->
                launch {
                    val count = runCatching {
                        registry.clientFor(conn.name)
                            .describeTable(DescribeTableRequest.builder().tableName(tableName).build())
                            .await().table().itemCount()
                    }.getOrNull()
                    if (count != null) withContext(Dispatchers.Swing) {
                        val tn = (0 until connNode.childCount)
                            .mapNotNull { connNode.getChildAt(it) as? DefaultMutableTreeNode }
                            .firstOrNull { (it.userObject as? TableNode)?.tableName == tableName }
                        if (tn != null) {
                            tn.userObject = TableNode(tableName, conn.name, count)
                            treeModel.nodeChanged(tn)
                            rebuildList(searchField.text.trim())
                        }
                    }
                }
            }
        }
    }

    fun addTableNode(connName: String, tableName: String) {
        tableCache[connName] = (tableCache[connName] ?: emptyList()) + tableName
        connNodeFor(connName)?.let { cn ->
            cn.add(DefaultMutableTreeNode(TableNode(tableName, connName)))
            treeModel.reload(cn)
        }
        rebuildList(searchField.text.trim())
    }

    fun removeTableNode(connName: String, tableName: String) {
        tableCache[connName] = (tableCache[connName] ?: emptyList()) - tableName
        connNodeFor(connName)?.let { cn ->
            (0 until cn.childCount).map { cn.getChildAt(it) as DefaultMutableTreeNode }
                .firstOrNull { (it.userObject as? TableNode)?.tableName == tableName }
                ?.let { treeModel.removeNodeFromParent(it) }
        }
        rebuildList(searchField.text.trim())
    }

    private fun connNodeFor(name: String) =
        (0 until treeRoot.childCount).map { treeRoot.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { (it.userObject as? ConnectionNode)?.name == name }

    // ── Recently opened ───────────────────────────────────────────────────────

    private fun trackRecent(tableName: String, connName: String) {
        recentlyOpened.removeAll { it.first == tableName && it.second == connName }
        recentlyOpened.add(0, tableName to connName)
        if (recentlyOpened.size > 5) recentlyOpened.removeAt(recentlyOpened.size - 1)
        trackRecentTime(tableName)
        rebuildRecentPanel()
    }

    private fun rebuildRecentPanel() {
        recentPanel.removeAll()
        if (recentlyOpened.isEmpty()) { recentPanel.isVisible = false; return }

        recentPanel.add(JPanel(BorderLayout()).apply {
            background = DColors.bg1
            border = BorderFactory.createCompoundBorder(
                MatteBorder(1, 0, 0, 0, DColors.line),
                BorderFactory.createEmptyBorder(5, 10, 4, 8))
            add(JLabel("RECENTLY OPENED").apply {
                foreground = DColors.fg3; font = font.deriveFont(Font.PLAIN, 9.5f)
            }, BorderLayout.WEST)
            maximumSize = Dimension(Int.MAX_VALUE, 24)
            alignmentX  = LEFT_ALIGNMENT
        })

        recentlyOpened.forEach { (tableName, connName) ->
            recentPanel.add(recentRow(tableName, connName, recentAgo(tableName)))
        }
        recentPanel.isVisible = true
        recentPanel.revalidate(); recentPanel.repaint()
    }

    private val recentTimestamps = mutableMapOf<String, Long>()
    private fun trackRecentTime(tableName: String) { recentTimestamps[tableName] = System.currentTimeMillis() }
    private fun recentAgo(tableName: String): String {
        val ms = System.currentTimeMillis() - (recentTimestamps[tableName] ?: System.currentTimeMillis())
        return when {
            ms < 60_000     -> "just now"
            ms < 3_600_000  -> "${ms / 60_000}m"
            ms < 86_400_000 -> "${ms / 3_600_000}h"
            else            -> "${ms / 86_400_000}d"
        }
    }

    private fun recentRow(tableName: String, connName: String, ago: String) = JPanel(BorderLayout()).apply {
        background = DColors.bg1
        border     = BorderFactory.createEmptyBorder(3, 28, 3, 8)
        maximumSize = Dimension(Int.MAX_VALUE, 26)
        alignmentX  = LEFT_ALIGNMENT
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(JLabel(AllIcons.Nodes.DataTables).apply { foreground = DColors.fg3 })
            add(JLabel(tableName).apply { foreground = DColors.fg2; font = font.deriveFont(Font.PLAIN, 12f) })
        }, BorderLayout.WEST)
        add(JLabel(ago).apply {
            foreground = DColors.fg3; font = font.deriveFont(Font.PLAIN, 10.5f)
            border     = BorderFactory.createEmptyBorder(0, 0, 0, 2)
        }, BorderLayout.EAST)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                activeTable = tableName; activeConnection = connName
                rebuildList(searchField.text.trim()); onTableSelected(tableName, connName)
            }
            override fun mouseEntered(ev: MouseEvent) { background = DColors.bg2; repaint() }
            override fun mouseExited(ev:  MouseEvent) { background = DColors.bg1; repaint() }
        })
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun selectedConnectionNode(): DefaultMutableTreeNode? = selConnNode

    fun selectedConnectionName() =
        (selConnNode?.userObject as? ConnectionNode)?.name

    fun selectedTableName(): String? = activeTable

    fun expandAll() {
        expandedConns.addAll(
            (0 until treeRoot.childCount)
                .mapNotNull { (treeRoot.getChildAt(it) as? DefaultMutableTreeNode)?.userObject as? ConnectionNode }
                .map { it.name }
        )
        rebuildList(searchField.text.trim())
    }

    fun collapseAll() {
        expandedConns.clear()
        rebuildList(searchField.text.trim())
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun buildTwToolbar(): JComponent = JPanel().apply {
        layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
        background    = DColors.bg2
        border        = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, DColors.line),
            BorderFactory.createEmptyBorder(2, 6, 2, 6))
        preferredSize = Dimension(0, 30)
        maximumSize   = Dimension(Int.MAX_VALUE, 30)
        minimumSize   = Dimension(0, 30)

        fun sep() = JSeparator(SwingConstants.VERTICAL).apply {
            maximumSize = Dimension(1, 14); preferredSize = Dimension(1, 14)
            foreground  = DColors.lineStrong
        }
        add(twBtn(AllIcons.General.Add,        "New connection")   { onAddConnection() })
        add(Box.createHorizontalStrut(2))
        add(twBtn(AllIcons.Actions.Edit,        "Edit connection")  { onEditConnection() })
        add(Box.createHorizontalStrut(2))
        add(twBtn(AllIcons.General.Remove,      "Remove connection"){ onRemoveConnection() })
        add(Box.createHorizontalStrut(2))
        add(twBtn(AllIcons.Actions.Refresh,     "Refresh")          { onRefreshSelected() })
        add(Box.createHorizontalStrut(4))
        add(sep())
        add(Box.createHorizontalStrut(4))
        add(twBtn(AllIcons.Actions.NewFolder,   "Create table")     { onCreateTable() })
        add(Box.createHorizontalStrut(2))
        add(twBtn(AllIcons.General.Remove,      "Delete table")     { onDeleteTable() })
        add(Box.createHorizontalGlue())
        add(twBtn(AllIcons.Actions.Collapseall, "Collapse all")     { collapseAll() })
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    private fun buildSearch(): JComponent = JPanel(BorderLayout()).apply {
        background    = DColors.bg1
        border        = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, DColors.line),
            BorderFactory.createEmptyBorder(5, 8, 5, 8))
        preferredSize = Dimension(0, 38)
        maximumSize   = Dimension(Int.MAX_VALUE, 38)
        minimumSize   = Dimension(0, 38)

        val pill = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DColors.bg3
                g2.fillRoundRect(0, 0, width, height, height, height)
            }
        }.apply {
            layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(0, 8, 0, 4)
        }

        searchField.alignmentY    = Component.CENTER_ALIGNMENT
        searchClearBtn.alignmentY = Component.CENTER_ALIGNMENT

        pill.add(JLabel(AllIcons.Actions.Search).apply {
            foreground = DColors.fg3; alignmentY = Component.CENTER_ALIGNMENT
        })
        pill.add(Box.createHorizontalStrut(6))
        pill.add(searchField)
        pill.add(searchClearBtn)
        add(pill, BorderLayout.CENTER)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}k"
        else           -> n.toString()
    }

    private fun twBtn(icon: Icon, tip: String, action: () -> Unit) =
        JButton(icon).apply {
            toolTipText         = tip
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            preferredSize       = Dimension(22, 22)
            maximumSize         = Dimension(22, 22)
            minimumSize         = Dimension(22, 22)
            addActionListener   { action() }
        }
}
