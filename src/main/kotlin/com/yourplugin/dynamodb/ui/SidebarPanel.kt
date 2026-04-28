package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.event.MouseInputAdapter
import javax.swing.JWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import javax.swing.*
import javax.swing.border.MatteBorder
import javax.swing.tree.*

/**
 * Right-side tool window: action toolbar + search + connection tree.
 *
 * Updated to match the HTML mock — applies items 3, 4, 6–12, 14–19, 22–34, 38, 40, 44, 45.
 * Skipped per request: 1, 2, 5, 13, 20, 21, 35, 36, 37, 39, 41, 42, 43, 46–49.
 */
class SidebarPanel(
    private val project: Project,
    private val registry: DynamoConnectionRegistry,
    private val scope: CoroutineScope,
    val onTableSelected:    (tableName: String, connectionName: String) -> Unit,
    val onContextMenu:      (node: DefaultMutableTreeNode, x: Int, y: Int) -> Unit,
    val onAddConnection:    () -> Unit = {},
    val onEditConnection:   () -> Unit = {},
    val onRemoveConnection: () -> Unit = {},
    val onRefreshSelected:  () -> Unit = {},
    val onCreateTable:      () -> Unit = {},
    val onDeleteTable:      () -> Unit = {},
) : JPanel(BorderLayout()) {

    // ── Active / recent state ─────────────────────────────────────────────────
    var activeTable:      String? = null
    var activeConnection: String? = null
    private val recentlyOpened = mutableListOf<Pair<String, String>>()

    data class HistoryEntry(
        val query: String, val tableName: String,
        val ms: Long, val ts: Long = System.currentTimeMillis(),
    )

    // ── Node data classes ─────────────────────────────────────────────────────
    data class ConnectionNode(
        val name: String,
        val region: String = "",
        val label: String = "",
        val loading: Boolean = false,
        val error: Boolean = false,
        val errorMessage: String? = null,
    )
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

    // ── Tree data backbone (kept for context-menu callers) ────────────────────
    private val treeRoot  = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(treeRoot)
    val tree = JTree(treeModel).apply { isVisible = false }

    // ── Visual list (replaces JTree rendering) ────────────────────────────────
    // (#3) single tree column hosts both connection rows and the "Recently opened"
    // section header + recent rows; no separate recentPanel anymore.
    private val listPanel = JPanel().apply {
        layout     = BoxLayout(this, BoxLayout.Y_AXIS)
        background = DColors.bg1
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val expandedConns = mutableSetOf<String>()
    private var selConnNode: DefaultMutableTreeNode? = null

    // ── Search ────────────────────────────────────────────────────────────────
    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", "Search tables…")
        font       = Font(font.name, Font.PLAIN, 12)
        foreground = DColors.fg0
        border     = BorderFactory.createEmptyBorder(0, 4, 0, 4)
        isOpaque   = false
        background = null
    }
    // (#16) clear button only visible when query non-empty (already the case)
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

    private val tableCache = mutableMapOf<String, List<String>>()

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        background = DColors.bg1
        // (#4) flexible width — no fixed 280px, no minimum width clamp
        // (#5 SKIPPED) keep right border separating sidebar from editor

        listPanel.alignmentX = Component.LEFT_ALIGNMENT

        val treeScroll = JBScrollPane(listPanel).apply {
            border = null
            background = DColors.bg1
            viewport.background = DColors.bg1
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

            val tables: List<TableNode> = (0 until connNode.childCount).mapNotNull {
                (connNode.getChildAt(it) as? DefaultMutableTreeNode)?.userObject as? TableNode
            }
            val visibleTables = if (filter.isEmpty()) tables
            else tables.filter { it.tableName.contains(filter, ignoreCase = true) }

            // (#38) when filter drops all tables, hide the connection
            if (filter.isNotEmpty() && visibleTables.isEmpty()) continue

            listPanel.add(connRow(connNode, ud))

            if (ud.name in expandedConns) {
                if (ud.error) {
                    // Connection failed — show error row instead of "No tables"
                    listPanel.add(errorRow(ud.errorMessage ?: "Connection failed", connNode))
                } else if (visibleTables.isEmpty() && filter.isEmpty()) {
                    // (#38, design) italic empty-state row
                    listPanel.add(emptyRow())
                } else {
                    visibleTables.forEach { t ->
                        val tn = (0 until connNode.childCount).map {
                            connNode.getChildAt(it) as DefaultMutableTreeNode
                        }.firstOrNull { (it.userObject as? TableNode)?.tableName == t.tableName }
                        if (tn != null) listPanel.add(tableRow(tn, t))
                    }
                }
            }
        }

        // (#3, #40) recently-opened section as part of the same list
        if (recentlyOpened.isNotEmpty()) {
            listPanel.add(sectionHeader("Recently opened"))
            recentlyOpened.forEach { (tableName, connName) ->
                listPanel.add(recentRow(tableName, connName, recentAgo(tableName)))
            }
        }

        listPanel.revalidate()
        listPanel.repaint()
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    /** (#18) SVG-style triangular chevron that rotates 90° when expanded. */
    private class Chevron(var expanded: Boolean) : JComponent() {
        init { preferredSize = Dimension(14, 16); maximumSize = Dimension(14, 16) }
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = DColors.fg2
            g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val cx = width / 2.0; val cy = height / 2.0
            val p = Path2D.Double()
            if (expanded) {
                // ▾ — pointing down
                p.moveTo(cx - 3, cy - 1.5)
                p.lineTo(cx,     cy + 2)
                p.lineTo(cx + 3, cy - 1.5)
            } else {
                // ▸ — pointing right
                p.moveTo(cx - 1.5, cy - 3)
                p.lineTo(cx + 2,   cy)
                p.lineTo(cx - 1.5, cy + 3)
            }
            g2.draw(p)
        }
    }

    /** (#19) Stylized DynamoDB hex glyph drawn fully in vector — no PNG, no tint hacks. */
    private class DynamoGlyph(var tint: Color) : Icon {
        override fun getIconWidth() = 16
        override fun getIconHeight() = 16
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = (g as Graphics2D).create() as Graphics2D
            g2.translate(x, y)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = tint
            g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val outer = Path2D.Double().apply {
                moveTo(8.0, 1.5); lineTo(13.5, 4.7); lineTo(13.5, 11.3)
                lineTo(8.0, 14.5); lineTo(2.5, 11.3); lineTo(2.5, 4.7); closePath()
            }
            val inner = Path2D.Double().apply {
                moveTo(8.0, 5.5); lineTo(11.2, 7.3); lineTo(11.2, 10.7)
                lineTo(8.0, 12.5); lineTo(4.8, 10.7); lineTo(4.8, 7.3); closePath()
            }
            g2.draw(outer); g2.draw(inner); g2.dispose()
        }
    }

    /** (#34) Small monochrome table glyph drawn in vector. */
    private class TableGlyph(var tint: Color) : Icon {
        override fun getIconWidth() = 16
        override fun getIconHeight() = 16
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = (g as Graphics2D).create() as Graphics2D
            g2.translate(x, y)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = tint
            g2.stroke = BasicStroke(1.3f)
            g2.drawRoundRect(2, 3, 11, 10, 2, 2)
            g2.stroke = BasicStroke(1f)
            g2.drawLine(2, 6, 13, 6)
            g2.drawLine(2, 9, 13, 9)
            g2.drawLine(6, 6, 6, 13)
            g2.drawLine(10, 6, 10, 13)
            g2.dispose()
        }
    }

    /** (#24, #30) Selected/active background painted with a 2px accent left rail. */
    private fun rowBg(g: Graphics, w: Int, h: Int, fill: Color, leftRail: Boolean,
                      indent: Int) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = fill
        g2.fillRoundRect(indent + 2, 2, w - indent - 6, h - 4, 6, 6)
        if (leftRail) {
            g2.color = DColors.accent
            g2.fillRect(indent, 2, 2, h - 4)
        }
    }

    private fun connRow(node: DefaultMutableTreeNode, ud: ConnectionNode): JPanel {
        val isSelected = selConnNode == node
        val isExpanded = ud.name in expandedConns
        var hovered = false

        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = DColors.bg1
                g.fillRect(0, 0, width, height)
                when {
                    isSelected -> rowBg(g, width, height, DColors.accentSoft, leftRail = true, indent = 0)
                    hovered    -> rowBg(g, width, height, DColors.bg2,        leftRail = false, indent = 0)
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

                add(Chevron(isExpanded).apply { alignmentY = Component.CENTER_ALIGNMENT })
                add(Box.createHorizontalStrut(4))

                // (#19) vector dynamo glyph; (#22) status colour reflects ok/loading/error
                val tint = when {
                    ud.error   -> DColors.bad
                    ud.loading -> DColors.fg3
                    else       -> DColors.accent
                }
                add(JLabel(DynamoGlyph(tint)).apply { alignmentY = Component.CENTER_ALIGNMENT })
                add(Box.createHorizontalStrut(6))

                // (#28) medium weight (not full bold)
                add(JLabel(when {
                    ud.error   -> "⚠ ${ud.name}"
                    ud.loading -> "${ud.name}  …"
                    else       -> ud.name
                }).apply {
                    font       = font.deriveFont(Font.PLAIN, 12f)
                        .deriveFont(mapOf(java.awt.font.TextAttribute.WEIGHT to java.awt.font.TextAttribute.WEIGHT_MEDIUM))
                    foreground = if (ud.error) DColors.bad else DColors.fg0
                    alignmentY = Component.CENTER_ALIGNMENT
                })
            }, BorderLayout.WEST)

            // EAST: ● standalone right-side dot + meta text
            if (!ud.error && !ud.loading) {
                add(JPanel().apply {
                    layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
                    isOpaque = false
                    if (ud.label.isNotEmpty()) {
                        add(JLabel(ud.label).apply {
                            font       = Font(Font.MONOSPACED, Font.PLAIN, 10)
                            foreground = DColors.fg3
                            alignmentY = Component.CENTER_ALIGNMENT
                        })
                        add(Box.createHorizontalStrut(6))
                    }
                    // (#22) status dot rendered as standalone right element
                    add(JLabel("●").apply {
                        font       = font.deriveFont(8f)
                        foreground = DColors.good
                        alignmentY = Component.CENTER_ALIGNMENT
                    })
                }, BorderLayout.EAST)
            }

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isPopupTrigger) return
                    selConnNode = node
                    if (ud.name in expandedConns) expandedConns.remove(ud.name)
                    else expandedConns.add(ud.name)
                    rebuildList(searchField.text.trim())
                }
                // (#27) right-click context menu
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

        // (#29) shallower indent — was 36, now ~22 (no region tier)
        val leftIndent = 22

        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = DColors.bg1
                g.fillRect(0, 0, width, height)
                when {
                    isActive -> rowBg(g, width, height, DColors.accentSoft,
                        leftRail = true, indent = leftIndent - 4)
                    hovered  -> rowBg(g, width, height, DColors.bg2,
                        leftRail = false, indent = leftIndent - 4)
                }
            }
        }.apply {
            isOpaque    = false
            border      = BorderFactory.createEmptyBorder(6, leftIndent, 6, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            alignmentX  = Component.LEFT_ALIGNMENT

            // WEST: icon + name
            add(JPanel().apply {
                layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque = false
                // (#34) plain mono table glyph in fg2 — no accent tint
                add(JLabel(TableGlyph(DColors.fg2)).apply { alignmentY = Component.CENTER_ALIGNMENT })
                add(Box.createHorizontalStrut(6))
                // (#31) default weight + default fg, even when active
                add(JLabel(ud.tableName).apply {
                    font       = font.deriveFont(Font.PLAIN, 12f)
                    foreground = DColors.fg1
                    alignmentY = Component.CENTER_ALIGNMENT
                })
            }, BorderLayout.WEST)

            // EAST: item count (kept; #35/36 skipped)
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
                    // (#32) single-click activates
                    activeTable = ud.tableName; activeConnection = ud.connectionName
                    trackRecent(ud.tableName, ud.connectionName)
                    rebuildList(searchField.text.trim())
                    onTableSelected(ud.tableName, ud.connectionName)
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

    /** (#38) "No tables" italic placeholder for an expanded empty connection. */
    private fun emptyRow(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(4, 22, 4, 8)
        maximumSize = Dimension(Int.MAX_VALUE, 22)
        alignmentX = Component.LEFT_ALIGNMENT
        add(JLabel("No tables").apply {
            font = font.deriveFont(Font.ITALIC, 11.5f)
            foreground = DColors.fg3
        }, BorderLayout.WEST)
    }

    /**
     * Inline error row shown under a failed connection.
     * Renders the warning glyph, the error message (truncated), and a Retry link.
     */
    private fun errorRow(message: String, connNode: DefaultMutableTreeNode): JPanel {
        val hint = ActionableError.classify(message)
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(6, 22, 6, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 44)
            alignmentX = Component.LEFT_ALIGNMENT
            // Custom tooltip; built lazily on hover via PrettyTooltip
            PrettyTooltip.install(this, message, hint)

            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque = false
                add(JLabel(AllIcons.General.BalloonError).apply {
                    alignmentY = Component.TOP_ALIGNMENT
                })
                add(Box.createHorizontalStrut(6))

                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
                    isOpaque = false
                    alignmentY = Component.TOP_ALIGNMENT

                    add(JLabel("Connection failed").apply {
                        font = font.deriveFont(Font.PLAIN, 11.5f)
                            .deriveFont(mapOf(java.awt.font.TextAttribute.WEIGHT to java.awt.font.TextAttribute.WEIGHT_MEDIUM))
                        foreground = DColors.bad
                        alignmentX = Component.LEFT_ALIGNMENT
                    })
                    add(JLabel(truncateMessage(message)).apply {
                        font = font.deriveFont(Font.PLAIN, 11f)
                        foreground = DColors.fg3
                        alignmentX = Component.LEFT_ALIGNMENT
                    })
                    add(Box.createVerticalStrut(2))
                    add(JLabel("<html><a href='#'>Retry</a></html>").apply {
                        font = font.deriveFont(Font.PLAIN, 11f)
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        alignmentX = Component.LEFT_ALIGNMENT
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                val ud = connNode.userObject as? ConnectionNode ?: return
                                connNode.userObject = ConnectionNode(
                                    ud.name, ud.region, label = ud.label, loading = true)
                                treeModel.nodeChanged(connNode)
                                rebuildList(searchField.text.trim())
                                loadTables(connNode)
                            }
                        })
                    })
                })
            }, BorderLayout.WEST)
        }
    }

    private fun truncateMessage(s: String, max: Int = 80): String =
        if (s.length <= max) s else s.take(max - 1) + "\u2026"

    /** (#40) mixed-case section divider rendered inline in the list. */
    private fun sectionHeader(text: String): JPanel = JPanel(BorderLayout()).apply {
        background = DColors.bg1
        border = BorderFactory.createCompoundBorder(
            MatteBorder(1, 0, 0, 0, DColors.line),
            BorderFactory.createEmptyBorder(6, 10, 4, 8))
        maximumSize = Dimension(Int.MAX_VALUE, 24)
        alignmentX = Component.LEFT_ALIGNMENT
        add(JLabel(text).apply {
            foreground = DColors.fg3
            font = font.deriveFont(Font.PLAIN, 11f)
        }, BorderLayout.WEST)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun onSearchChanged() {
        val q = searchField.text.trim()
        searchClearBtn.isVisible = q.isNotEmpty()
        rebuildList(q)
    }

    private fun filterTree(q: String) = rebuildList(q)
    private fun refreshTreeFromCache() = rebuildList(searchField.text.trim())

    // ── Tree management ───────────────────────────────────────────────────────

    fun refreshTree() {
        tableCache.clear()
        expandedConns.clear()
        treeRoot.removeAllChildren()
        val conns = registry.allConnections()
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
            }.getOrElse { ex ->
                val msg = ex.message?.takeIf { it.isNotBlank() }
                    ?: ex.javaClass.simpleName
                withContext(Dispatchers.Swing) {
                    connNode.userObject = ConnectionNode(
                        conn.name, conn.region, label = conn.label,
                        error = true, errorMessage = msg)
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

    // ── Recently opened (#39, #41, #42, #43 left untouched per skip list) ─────

    private fun trackRecent(tableName: String, connName: String) {
        recentlyOpened.removeAll { it.first == tableName && it.second == connName }
        recentlyOpened.add(0, tableName to connName)
        if (recentlyOpened.size > 5) recentlyOpened.removeAt(recentlyOpened.size - 1)
        trackRecentTime(tableName)
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

    /** (#44, #45) Inline recent row at indent-1 with bg2 hover. */
    private fun recentRow(tableName: String, connName: String, ago: String): JPanel {
        var hovered = false
        return object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                g.color = if (hovered) DColors.bg2 else DColors.bg1
                g.fillRect(0, 0, width, height)
            }
        }.apply {
            isOpaque   = false
            border     = BorderFactory.createEmptyBorder(4, 22, 4, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 24)
            alignmentX  = Component.LEFT_ALIGNMENT

            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JLabel(TableGlyph(DColors.fg3)))
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
                override fun mouseEntered(ev: MouseEvent) { hovered = true;  repaint() }
                override fun mouseExited(ev:  MouseEvent) { hovered = false; repaint() }
            })
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun selectedConnectionNode(): DefaultMutableTreeNode? = selConnNode
    fun selectedConnectionName() = (selConnNode?.userObject as? ConnectionNode)?.name
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

    /**
     * (#6) bg1 to match the panel — was bg2 (slightly elevated) before.
     * (#7) ~28px tall — was 30px.
     * (#8) real vertical separators between groups (kept).
     * (#10) delete-table is disabled when no active table.
     * (#11) collapse-all stays at the far right (kept).
     * (#12) expand-all stays in the tool-window title bar (no change here).
     */
    private val deleteTableBtn: JButton by lazy { mutableListOf<JButton>().let { JButton() } }

    private fun buildTwToolbar(): JComponent {
        val deleteBtn = twBtn(AllIcons.General.Remove, "Delete table") { onDeleteTable() }
        deleteBtn.isEnabled = false   // (#10) disabled until activeTable != null

        // Re-enable based on active selection — repaint via list rebuild.
        // (We toggle it in tableRow's click; cheap to set it here and on every rebuild.)
        addPropertyChangeListener("activeTable") { deleteBtn.isEnabled = activeTable != null }

        return JPanel().apply {
            layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
            background    = DColors.bg1   // #6
            border        = BorderFactory.createCompoundBorder(
                MatteBorder(0, 0, 1, 0, DColors.line),
                BorderFactory.createEmptyBorder(2, 6, 2, 6))
            preferredSize = Dimension(0, 28) // #7
            maximumSize   = Dimension(Int.MAX_VALUE, 28)
            minimumSize   = Dimension(0, 28)

            fun sep() = JSeparator(SwingConstants.VERTICAL).apply {
                maximumSize = Dimension(1, 14); preferredSize = Dimension(1, 14)
                foreground  = DColors.lineStrong
            }

            // (#9) action-icon set
            add(twBtn(AllIcons.General.Add,        "New connection")    { onAddConnection() })
            add(Box.createHorizontalStrut(2))
            add(twBtn(AllIcons.Actions.Edit,        "Edit connection")   { onEditConnection() })
            add(Box.createHorizontalStrut(2))
            add(twBtn(AllIcons.General.Remove,      "Remove connection") { onRemoveConnection() })
            add(Box.createHorizontalStrut(2))
            add(twBtn(AllIcons.Actions.Refresh,     "Refresh")           { onRefreshSelected() })
            add(Box.createHorizontalStrut(4))
            add(sep())
            add(Box.createHorizontalStrut(4))
            add(twBtn(AllIcons.Actions.NewFolder,   "Create table")      { onCreateTable() })
            add(Box.createHorizontalStrut(2))
            add(deleteBtn)                                                   // #10
            add(Box.createHorizontalGlue())
            add(twBtn(AllIcons.Actions.Collapseall, "Collapse all")      { collapseAll() })
        }
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    /**
     * (#14) pill with subtle border, no separator below.
     * (#17) auto-height — no fixed 38px clamp.
     */
    private fun buildSearch(): JComponent = JPanel(BorderLayout()).apply {
        background    = DColors.bg1
        // No bottom-line MatteBorder anymore — just inner padding.
        border        = BorderFactory.createEmptyBorder(5, 8, 5, 8)
        // No fixed preferred/maximum height — let the inner pill define it.

        val pill = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // soft-fill background
                g2.color = DColors.bg3
                g2.fillRoundRect(0, 0, width - 1, height - 1, height, height)
                // subtle 1px border (#14)
                g2.color = DColors.line
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
            }
        }.apply {
            layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(2, 8, 2, 4)
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

// ─────────────────────────────────────────────────────────────────────────────
//  Actionable-error classifier
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a raw AWS / network exception message to a short, human-readable hint.
 * Heuristic — runs case-insensitive substring matches in priority order.
 */
private object ActionableError {

    data class Hint(val title: String, val steps: List<String>)

    private val rules: List<Pair<Regex, Hint>> = listOf(
        Regex("(?i)(ExpiredToken|token has expired|expired credentials)") to Hint(
            "Credentials expired",
            listOf("Run `aws sso login` (or refresh your STS token).",
                "Then click Retry.")),
        Regex("(?i)(InvalidSignature|signature.*does not match|SignatureDoesNotMatch)") to Hint(
            "Invalid signature",
            listOf("Check the access key & secret on this connection.",
                "Verify your system clock — AWS rejects requests >5 min off.")),
        Regex("(?i)(InvalidClientTokenId|UnrecognizedClient|InvalidAccessKeyId)") to Hint(
            "Access key not recognised",
            listOf("Confirm the AWS access-key ID is correct.",
                "If you rotated keys recently, update this connection.")),
        Regex("(?i)(AccessDenied|UnauthorizedOperation|not authorized to perform)") to Hint(
            "Access denied",
            listOf("This identity lacks `dynamodb:ListTables` on the region.",
                "Add the permission to your IAM role/user.")),
        Regex("(?i)(UnableToExecuteHttp|Connection refused|Connection.*timed out|connect timed out)") to Hint(
            "Endpoint unreachable",
            listOf("Check your network / VPN.",
                "If using DynamoDB Local, confirm it's running on the configured port.")),
        Regex("(?i)(UnknownHost|nodename nor servname|Name or service not known)") to Hint(
            "Host not found",
            listOf("DNS can't resolve the endpoint.",
                "Check the region / endpoint override on this connection.")),
        Regex("(?i)(SSL|certificate|PKIX|TrustAnchor)") to Hint(
            "TLS / certificate problem",
            listOf("Your JDK can't validate the endpoint certificate.",
                "If you're behind a corporate proxy, install its CA cert into the JDK truststore.")),
        Regex("(?i)(MissingRegion|region.*could not be determined)") to Hint(
            "Region not set",
            listOf("Edit this connection and set an explicit AWS region.")),
        Regex("(?i)(ProfileNotFound|profile file cannot be null|profile.*not found)") to Hint(
            "AWS profile not found",
            listOf("The named profile isn't in `~/.aws/credentials`.",
                "Create it, or pick a different profile in connection settings.")),
        Regex("(?i)(Throttling|ThrottlingException|RequestLimitExceeded|ProvisionedThroughputExceeded)") to Hint(
            "Throttled by AWS",
            listOf("Wait a few seconds and click Retry.",
                "If this happens often, increase table capacity or back off in your tooling.")),
        Regex("(?i)ResourceNotFound") to Hint(
            "Resource not found",
            listOf("The table or index referenced doesn't exist in this region.")),
    )

    fun classify(message: String): Hint? = rules.firstOrNull { it.first.containsMatchIn(message) }?.second
}

// ─────────────────────────────────────────────────────────────────────────────
//  PrettyTooltip — custom-painted hover popup
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Custom tooltip rendered as a JWindow with a soft shadow, accent border,
 * monospace error block and (optionally) a hint section.
 */
private object PrettyTooltip {

    private const val MAX_W   = 400
    private const val SHOW_MS = 350

    fun install(comp: JComponent, message: String, hint: ActionableError.Hint?) {
        // Disable Swing's default tooltip — we render our own.
        comp.toolTipText = null
        var window: JWindow? = null
        var showTimer: javax.swing.Timer? = null

        val handler = object : MouseInputAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                showTimer?.stop()
                showTimer = javax.swing.Timer(SHOW_MS) {
                    val owner = SwingUtilities.getWindowAncestor(comp) ?: return@Timer
                    val w = buildWindow(owner, message, hint)
                    val pt = comp.locationOnScreen
                    var x = pt.x
                    var y = pt.y + comp.height + 6
                    val screen = comp.graphicsConfiguration.bounds
                    if (x + w.width > screen.x + screen.width)  x = screen.x + screen.width  - w.width - 8
                    if (y + w.height > screen.y + screen.height) y = pt.y - w.height - 6
                    w.setLocation(x, y)
                    w.isVisible = true
                    window = w
                }.apply { isRepeats = false; start() }
            }
            override fun mouseExited(e: MouseEvent) {
                showTimer?.stop()
                window?.dispose(); window = null
            }
        }
        comp.addMouseListener(handler)
    }

    private fun buildWindow(owner: Window, message: String, hint: ActionableError.Hint?): JWindow {
        val win = JWindow(owner)
        win.background = Color(0, 0, 0, 0)

        val card = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // shadow
                for (i in 0..6) {
                    g2.color = Color(0, 0, 0, 12 - i)
                    g2.fillRoundRect(2 - i / 2, 2 + i, width - 4 + i, height - 4 - i, 10, 10)
                }
                // surface
                g2.color = DColors.bg2
                g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10)
                // 1px border
                g2.color = DColors.line
                g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10)
                // accent rail
                g2.color = DColors.bad
                g2.fillRect(0, 8, 3, height - 16)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
            isOpaque = false
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        }

        // Header row — icon + title
        card.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.LINE_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(AllIcons.General.BalloonError))
            add(Box.createHorizontalStrut(8))
            add(JLabel("Connection failed").apply {
                font = font.deriveFont(Font.PLAIN, 13f)
                    .deriveFont(mapOf(java.awt.font.TextAttribute.WEIGHT to java.awt.font.TextAttribute.WEIGHT_MEDIUM))
                foreground = DColors.fg0
            })
        })
        card.add(Box.createVerticalStrut(8))

        // Error message — monospace, wrapped
        card.add(wrappedLabel(message, mono = true, fg = DColors.fg2, sizePx = 11f).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        })

        if (hint != null) {
            card.add(Box.createVerticalStrut(10))
            card.add(JSeparator().apply {
                foreground = DColors.line; alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 1)
            })
            card.add(Box.createVerticalStrut(10))

            // Hint title — accent-coloured "Try this" header
            card.add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
                add(JLabel("\uD83D\uDCA1").apply { font = font.deriveFont(13f) })
                add(Box.createHorizontalStrut(6))
                add(JLabel(hint.title).apply {
                    font = font.deriveFont(Font.PLAIN, 12f)
                        .deriveFont(mapOf(java.awt.font.TextAttribute.WEIGHT to java.awt.font.TextAttribute.WEIGHT_MEDIUM))
                    foreground = DColors.accent
                })
            })
            card.add(Box.createVerticalStrut(4))

            for (step in hint.steps) {
                card.add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = BorderFactory.createEmptyBorder(2, 18, 2, 0)
                    val bullet = JLabel("•").apply {
                        foreground = DColors.fg3
                        font = font.deriveFont(Font.BOLD, 12f)
                        alignmentY = Component.TOP_ALIGNMENT
                    }
                    add(bullet); add(Box.createHorizontalStrut(6))
                    add(wrappedLabel(step, mono = false, fg = DColors.fg1, sizePx = 11.5f).apply {
                        alignmentY = Component.TOP_ALIGNMENT
                    })
                })
            }
        }

        win.contentPane = card
        win.pack()
        // pack() under-estimates width because Swing's HTML renderer uses the CSS
        // width hint only for line-wrapping, not for the JLabel's own preferredSize.
        // Force the window to exactly MAX_W wide so nothing is clipped.
        win.setSize(MAX_W + 28, win.height)   // +28 = 14px left + 14px right padding
        return win
    }

    /** A JLabel that wraps text inside a max width using HTML. */
    private fun wrappedLabel(text: String, mono: Boolean, fg: Color, sizePx: Float): JLabel {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
        val css = if (mono)
            "font-family:'JetBrains Mono','Menlo',monospace;font-size:${sizePx.toInt()}px;width:${MAX_W - 30}px;line-height:1.45;"
        else
            "font-size:${sizePx.toInt()}px;width:${MAX_W - 30}px;line-height:1.45;"
        return JLabel("<html><div style='$css'>$escaped</div></html>").apply {
            foreground = fg
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }
}

