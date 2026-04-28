package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.MatteBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

/**
 * Table browser panel — prototype-matched layout.
 *
 * ┌─ rowToolbar ────────────────────────────────────────┐  38px
 * │  [|◀][◀][20 rows▾][▶][▶|] │ ↺ │ [+][-] · · Export│
 * ├─ filterBar ─────────────────────────────────────────┤  38px
 * │  🔍  TableName · Filter  [_____________________] × │
 * ├─ resultsToolbar ────────────────────────────────────┤  38px
 * │  🗃 TableName  [N rows] [●Nms] [N RCU]   · [⊟][⊞] │
 * ├─ table ──────────────────────┬──────────────────────┤  1fr
 * │  results                     │  inspector           │
 * └──────────────────────────────┴──────────────────────┘
 */
class QueryRunnerPanel(
    private val project: Project,
    val connectionName: String,
    val tableName: String,
    private val onSchemaLoaded: ((TableSchema) -> Unit)? = null,
    private val onHistoryEntry: ((SidebarPanel.HistoryEntry) -> Unit)? = null,
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val registry
        get() = ApplicationManager.getApplication().getService(DynamoConnectionRegistry::class.java)

    // ── Results table ─────────────────────────────────────────────────────────
    private val tableModel = object : DefaultTableModel(0, 0) {
        override fun isCellEditable(r: Int, c: Int) = false
    }
    private val resultsTable = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        background  = DColors.bg1
        foreground  = DColors.fg0
        gridColor   = DColors.line
        rowHeight   = 30
        showHorizontalLines = true
        showVerticalLines   = false
        tableHeader.background = DColors.bg2
        tableHeader.foreground = DColors.fg2
        tableHeader.font = tableHeader.font.deriveFont(Font.PLAIN, 11.5f)
        setDefaultRenderer(Any::class.java, SemanticCellRenderer())
        autoResizeMode    = JTable.AUTO_RESIZE_OFF
        intercellSpacing  = Dimension(0, 0)
        selectionBackground = DColors.accentSoft
        selectionForeground = DColors.fg0
    }

    // ── Session history ───────────────────────────────────────────────────────
    data class ItemHistoryEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val action: String,                      // "Created" | "Edited" | "Deleted"
        val changes: List<String> = emptyList(), // ["field: old → new", …]
    )
    private val itemHistory = mutableMapOf<String, MutableList<ItemHistoryEntry>>()

    private fun itemKey(item: Map<String, AttributeValue>): String {
        val schema = cachedSchema ?: return item.keys.firstOrNull()?.let { item[it]?.displayValue() } ?: "?"
        val pk = item[schema.partitionKey.name]?.displayValue() ?: "?"
        val sk = schema.sortKey?.name?.let { item[it]?.displayValue() }
        return if (sk != null) "$pk|$sk" else pk
    }

    private fun recordHistory(item: Map<String, AttributeValue>, action: String, changes: List<String> = emptyList()) {
        itemHistory.getOrPut(itemKey(item)) { mutableListOf() }
            .add(ItemHistoryEntry(action = action, changes = changes))
    }

    // ── Inspector ─────────────────────────────────────────────────────────────
    private var inspectorVisible    = false
    private var inspectorProportion = 0.65f   // remembered across hide/show
    private val inspector = RowInspectorPanel(
        onClose = { hideInspector() },
        onEdit  = { item -> openItemEditor(item) },
        getHistory = { item -> itemHistory[itemKey(item)] ?: emptyList() },
    )
    private lateinit var resultsSplit: JBSplitter

    private val hiddenColumns     = mutableMapOf<String, javax.swing.table.TableColumn>()
    private var columnPopup: JPopupMenu? = null
    private val columnToggleBtn   = miniBtn(AllIcons.Actions.GroupByModule, "Toggle columns") {
        val popup = columnPopup
        if (popup != null && popup.isVisible) { popup.isVisible = false; columnPopup = null }
        else showColumnPopup()
    }

    /** Toggle button — painted with accent fill when inspector is open. */
    private val inspectorToggleBtn = object : JButton(AllIcons.Actions.PreviewDetails) {
        override fun paintComponent(g: Graphics) {
            if (inspectorVisible) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DColors.accentSoft
                val hh = 22
                val hy = (height - hh) / 2
                g2.fillRoundRect(0, hy, width, hh, 4, 4)
            }
            super.paintComponent(g)
        }
    }.apply {
        toolTipText         = "Toggle inspector"
        isBorderPainted     = false
        isContentAreaFilled = false
        isFocusPainted      = false
        preferredSize       = Dimension(22, 22)
        addActionListener   { toggleInspector() }
    }

    // ── Filter bar input ──────────────────────────────────────────────────────
    private val filterInput = JTextField().apply {
        putClientProperty("JTextField.placeholderText",
            "<Filter Criteria>   e.g.  rating > 4.0 AND city = 'Tampa'")
        font       = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = DColors.bg1
        foreground = DColors.fg0
        border     = BorderFactory.createEmptyBorder(0, 4, 0, 4)
        isOpaque   = false
    }
    private var rowSorter: TableRowSorter<DefaultTableModel>? = null

    // ── Stat pills ────────────────────────────────────────────────────────────
    private val rowCountPill = statPill("—")
    private val timingPill   = statPill("—", DColors.good)
    private val rcuPill      = statPill("—")

    // ── Pagination state ──────────────────────────────────────────────────────
    private var cachedSchema: TableSchema? = null
    private var rawItems     = mutableListOf<Map<String, AttributeValue>>()
    private val pageKeys     = mutableListOf<Map<String, AttributeValue>?>(null)
    private var currentPage  = 1
    private var currentPageSize = 20
    var isRunning = false
        private set

    // ── Row-toolbar navigation buttons ────────────────────────────────────────
    private val firstBtn    = ijBtn(AllIcons.Actions.Play_first,    "First page")   { goToFirstPage() }
    private val prevBtn     = ijBtn(AllIcons.Actions.Back,          "Prev page")    { goToPage(currentPage - 1) }
    private val nextBtn     = ijBtn(AllIcons.Actions.Forward,       "Next page")    { goToPage(currentPage + 1) }
    private val lastBtn     = ijBtn(AllIcons.Actions.Play_last,     "Last page")    { goToLastKnownPage() }
    private val refreshBtn  = ijBtn(AllIcons.Actions.Refresh,       "Reload")       { execute() }
    private val addRowBtn   = ijBtn(AllIcons.General.Add,           "Add row")      { addRow() }
    private val deleteRowBtn= ijBtn(AllIcons.General.Remove,        "Delete selected rows") { batchDelete() }
    private val exportBtn   = ijBtn(DownArrowIcon, "Download selected rows as JSON") { exportSelectedJson() }
    private val moreBtn     = ijBtn(AllIcons.Actions.More,           "More options")   { showMoreMenu() }

    private val pageSizeBtn = object : JButton("$currentPageSize rows  ▾") {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background; g2.fillRoundRect(0, 0, width, height, 4, 4)
            super.paintComponent(g)
        }
    }.apply {
        font               = Font(Font.MONOSPACED, Font.PLAIN, 12)
        foreground         = DColors.fg1
        background         = DColors.bg3
        isBorderPainted    = false
        isContentAreaFilled = false
        isFocusPainted     = false
        border             = BorderFactory.createEmptyBorder(0, 8, 0, 8)
        preferredSize      = Dimension(90, 26)
        isOpaque           = false
        addActionListener {
            val menu = JPopupMenu()
            for (size in listOf(10, 20, 50, 100, 200)) {
                menu.add(JMenuItem("$size rows").also { item ->
                    item.addActionListener {
                        currentPageSize = size
                        text = "$size rows  ▾"
                        execute()
                    }
                })
            }
            menu.show(this, 0, height)
        }
    }

    // Must be before init{} — used by buildFilterBar() which is called from init
    private val filterClearBtn = object : JButton("×") {
        init {
            font               = font.deriveFont(Font.PLAIN, 13f)
            foreground         = DColors.fg3
            isBorderPainted    = false
            isContentAreaFilled = false
            isFocusPainted     = false
            preferredSize      = Dimension(22, 22)
            maximumSize        = Dimension(22, 22)
            minimumSize        = Dimension(22, 22)
            isVisible          = false
            addActionListener  { filterInput.text = ""; isVisible = false }
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        background = DColors.bg1

        // Row click → inspector
        resultsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = resultsTable.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
                val item = rawItems.getOrNull(resultsTable.convertRowIndexToModel(row)) ?: return
                if (e.clickCount == 1) {
                    inspector.showItem(item, cachedSchema)
                    if (!inspectorVisible) showInspector()
                }
            }
        })

        // Filter bar live filter
        filterInput.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent)  = applyFilter()
            override fun removeUpdate(e: DocumentEvent)  = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })

        // Assemble layout
        val topSection = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.PAGE_AXIS)
            background = DColors.bg1
            add(buildRowToolbar())
            add(buildFilterBar())
        }
        val resultsSection = JPanel(BorderLayout()).apply {
            background = DColors.bg1
            add(buildResultsToolbar(), BorderLayout.NORTH)
            add(buildResultsArea(),    BorderLayout.CENTER)
        }
        val main = JPanel(BorderLayout()).apply {
            background = DColors.bg1
            add(topSection,     BorderLayout.NORTH)
            add(resultsSection, BorderLayout.CENTER)
        }
        add(main, BorderLayout.CENTER)

        // Disable nav buttons initially
        updateNavButtons(hasNext = false)

        // Pre-load schema
        scope.launch {
            cachedSchema = runCatching {
                TableSchemaService.describe(registry.clientFor(connectionName), tableName)
            }.getOrNull()
            withContext(Dispatchers.Swing) {
                cachedSchema?.let { schema ->
                    inspector.setSchema(schema)
                    onSchemaLoaded?.invoke(schema)
                }
            }
        }
    }

    // ── Row toolbar ───────────────────────────────────────────────────────────

    private fun buildRowToolbar(): JComponent = JPanel().apply {
        layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
        background    = DColors.bg2
        border        = MatteBorder(0, 0, 1, 0, DColors.line)
        preferredSize = Dimension(Int.MAX_VALUE, 38)
        maximumSize   = Dimension(Int.MAX_VALUE, 38)
        minimumSize   = Dimension(0, 38)

        fun gap(n: Int) = Box.createHorizontalStrut(n)

        add(gap(8))
        add(btnGroup(firstBtn, prevBtn, pageSizeBtn, nextBtn, lastBtn)); add(gap(6))
        add(btnGroup(refreshBtn));                                        add(gap(6))
        add(btnGroup(addRowBtn, deleteRowBtn))
        add(Box.createHorizontalGlue())
        add(btnGroup(exportBtn));  add(gap(6))
    }

    /** Wraps buttons in a rounded-rect group pill (prototype toolbar style). */
    private fun btnGroup(vararg btns: JButton): JComponent {
        return object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DColors.bg3
                g2.fillRoundRect(0, (height - 26) / 2, width, 26, 6, 6)
            }
        }.apply {
            layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(0, 2, 0, 2)
            btns.forEach { btn ->
                btn.isBorderPainted     = false
                btn.isContentAreaFilled = false
                add(btn)
            }
        }
    }

    /** "···" overflow popup — copy JSON, export. */
    private fun showMoreMenu() {
        val menu = JPopupMenu()
        menu.add(JMenuItem("Copy row as JSON").also { mi ->
            mi.addActionListener {
                val r = resultsTable.selectedRow.takeIf { it >= 0 } ?: return@addActionListener
                val item = rawItems.getOrNull(resultsTable.convertRowIndexToModel(r)) ?: return@addActionListener
                val json = buildString {
                    append("{\n")
                    item.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) append(",\n")
                        append("  \"$k\": \"${v.displayValue().replace("\"", "\\\"")}\"")
                    }
                    append("\n}")
                }
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(json), null)
            }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Export as CSV / JSON…").also { mi ->
            mi.addActionListener { showExportDialog() }
        })
        menu.show(moreBtn, 0, moreBtn.height)
    }

    // ── Column visibility ─────────────────────────────────────────────────────

    private fun showColumnPopup() {
        if (tableModel.columnCount == 0) return
        val menu = JPopupMenu()
        val allNames = (0 until tableModel.columnCount)
            .map { tableModel.getColumnName(it) }
            .filter { it != "#" }

        // "Show All" as a regular JMenuItem — clicking it dismissing the popup is acceptable
        menu.add(JMenuItem("Show All").also { it.addActionListener { allNames.forEach { n -> showColumn(n) } } })
        menu.addSeparator()

        // Each column as a raw JPanel+JCheckBox — NOT a JMenuItem, so Swing won't
        // auto-dismiss the popup when the checkbox is toggled
        allNames.forEach { name ->
            val cb = JCheckBox(name, name !in hiddenColumns).apply {
                isOpaque = false
                font = font.deriveFont(Font.PLAIN, 12f)
                addActionListener { if (isSelected) showColumn(name) else hideColumn(name) }
            }
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                isOpaque = false
                add(cb)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) { cb.doClick() }
                })
            }
            menu.add(row)
        }

        menu.addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent) { columnPopup = null }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent) { columnPopup = null }
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent) {}
        })
        columnPopup = menu
        menu.show(columnToggleBtn, 0, columnToggleBtn.height)
    }

    private fun hideColumn(name: String) {
        runCatching {
            val col = resultsTable.columnModel.getColumn(resultsTable.columnModel.getColumnIndex(name))
            hiddenColumns[name] = col
            resultsTable.columnModel.removeColumn(col)
        }
    }

    private fun showColumn(name: String) {
        val col = hiddenColumns.remove(name) ?: return
        resultsTable.columnModel.addColumn(col)
        // Move to model-order position among visible columns
        val modelIdx = (0 until tableModel.columnCount).indexOfFirst { tableModel.getColumnName(it) == name }
        if (modelIdx >= 0) {
            val targetViewIdx = (0 until modelIdx)
                .map { tableModel.getColumnName(it) }
                .count { it !in hiddenColumns }
            val lastIdx = resultsTable.columnModel.columnCount - 1
            if (targetViewIdx < lastIdx) resultsTable.columnModel.moveColumn(lastIdx, targetViewIdx)
        }
    }

    // ── Filter bar ────────────────────────────────────────────────────────────

    private fun buildFilterBar(): JComponent = JPanel().apply {
        layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
        background    = DColors.bg1
        border        = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, DColors.line),
            BorderFactory.createEmptyBorder(0, 10, 0, 8))
        preferredSize = Dimension(Int.MAX_VALUE, 38)
        maximumSize   = Dimension(Int.MAX_VALUE, 38)
        minimumSize   = Dimension(0, 38)

        // Show/hide clear button as user types
        filterInput.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent)  { filterClearBtn.isVisible = filterInput.text.isNotEmpty() }
            override fun removeUpdate(e: DocumentEvent)  { filterClearBtn.isVisible = filterInput.text.isNotEmpty() }
            override fun changedUpdate(e: DocumentEvent) {}
        })

        filterInput.alignmentY     = Component.CENTER_ALIGNMENT
        filterClearBtn.alignmentY  = Component.CENTER_ALIGNMENT

        add(JLabel(AllIcons.Actions.Search).apply {
            foreground = DColors.accent; alignmentY = Component.CENTER_ALIGNMENT })
        add(Box.createHorizontalStrut(6))
        add(JLabel(tableName).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11); foreground = DColors.fg2
            alignmentY = Component.CENTER_ALIGNMENT })
        add(JLabel("  ·  Filter").apply {
            font = font.deriveFont(Font.PLAIN, 11.5f); foreground = DColors.fg3
            alignmentY = Component.CENTER_ALIGNMENT })
        add(Box.createHorizontalStrut(8))
        add(filterInput)
        add(Box.createHorizontalStrut(4))
        add(filterClearBtn)
        add(Box.createHorizontalStrut(6))
        add(buildApplyBadge())
        add(Box.createHorizontalStrut(4))
    }

    /** "⌘↵ apply" keyboard-badge hint shown at the right of the filter bar. */
    private fun buildApplyBadge(): JComponent = JPanel().apply {
        layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
        isOpaque = false
        alignmentY = Component.CENTER_ALIGNMENT

        add(object : JLabel("⌘↵") {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DColors.bg3; g2.fillRoundRect(0, 0, width, height, 4, 4)
                super.paintComponent(g)
            }
        }.apply {
            font = font.deriveFont(Font.PLAIN, 10f); foreground = DColors.fg2
            border = BorderFactory.createEmptyBorder(1, 5, 1, 5); isOpaque = false
            alignmentY = Component.CENTER_ALIGNMENT
        })
        add(JLabel(" apply").apply {
            font = font.deriveFont(Font.PLAIN, 11f); foreground = DColors.fg3
            alignmentY = Component.CENTER_ALIGNMENT
        })
    }

    // ── Results toolbar ───────────────────────────────────────────────────────

    private fun buildResultsToolbar(): JComponent = JPanel().apply {
        layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
        background    = DColors.bg1
        border        = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, DColors.line),
            BorderFactory.createEmptyBorder(0, 10, 0, 8))
        preferredSize = Dimension(Int.MAX_VALUE, 38)
        maximumSize   = Dimension(Int.MAX_VALUE, 38)
        minimumSize   = Dimension(0, 38)

        fun cv(c: JComponent) = c.apply { alignmentY = Component.CENTER_ALIGNMENT }

        add(cv(JLabel(AllIcons.Nodes.DataTables).apply { foreground = DColors.accent }))
        add(Box.createHorizontalStrut(8))
        add(cv(JLabel(tableName).apply { foreground = DColors.fg0; font = font.deriveFont(Font.BOLD, 13f) }))
        add(Box.createHorizontalStrut(8))
        add(cv(rowCountPill))
        add(Box.createHorizontalStrut(6))
        add(cv(timingPill))
        add(Box.createHorizontalStrut(6))
        add(cv(rcuPill))
        add(Box.createHorizontalGlue())
        add(cv(btnGroup(columnToggleBtn, inspectorToggleBtn)))
        add(Box.createHorizontalStrut(4))
    }

    // ── Results area (table + inspector) ─────────────────────────────────────

    private fun buildResultsArea(): JComponent {
        val tableScroll = JBScrollPane(resultsTable).apply {
            border = null; background = DColors.bg1; viewport.background = DColors.bg1
        }
        resultsSplit = JBSplitter(false, inspectorProportion).apply {
            firstComponent  = tableScroll
            secondComponent = null          // hidden by default; dividerWidth stays at default
        }
        return resultsSplit
    }

    private fun showInspector() {
        inspectorVisible             = true
        resultsSplit.secondComponent = inspector
        resultsSplit.proportion      = inspectorProportion
        resultsSplit.revalidate()
        inspectorToggleBtn.repaint()
    }

    private fun hideInspector() {
        if (::resultsSplit.isInitialized) {
            inspectorProportion = resultsSplit.proportion
        }
        inspectorVisible             = false
        if (::resultsSplit.isInitialized) {
            resultsSplit.secondComponent = null
            resultsSplit.proportion      = 1.0f
            resultsSplit.revalidate()
        }
        inspectorToggleBtn.repaint()
    }

    private fun toggleInspector() = if (inspectorVisible) hideInspector() else showInspector()

    // ── Execution ─────────────────────────────────────────────────────────────

    fun execute() {
        pageKeys.clear(); pageKeys.add(null)
        currentPage = 1
        loadCurrentPage()
    }

    private fun goToPage(page: Int) {
        if (page < 1) return
        currentPage = page
        loadCurrentPage()
    }

    private fun goToFirstPage() = goToPage(1)

    /** Jump to the furthest page whose start-key we already have cached. */
    private fun goToLastKnownPage() {
        val last = pageKeys.size   // pageKeys[0..n] covers pages 1..n+1; last known page = pageKeys.size
        if (last > currentPage) goToPage(last)
    }

    private fun loadCurrentPage() {
        val startKey = pageKeys.getOrNull(currentPage - 1) ?: if (currentPage != 1) return else null
        isRunning = true
        updateNavButtons(hasNext = false)

        scope.launch {
            val t0 = System.currentTimeMillis()
            val result = runCatching {
                val client = registry.clientFor(connectionName)
                val resp = client.scan(
                    ScanRequest.builder()
                        .tableName(tableName)
                        .limit(currentPageSize)
                        .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                        .apply { startKey?.let { exclusiveStartKey(it) } }
                        .build()
                ).await()
                ExecutionResult.QueryResult(
                    resp.items(),
                    resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                    resp.consumedCapacity()?.capacityUnits()
                )
            }.getOrElse { ex -> ExecutionResult.Error(ex.message ?: "Unknown error") }
            val ms = System.currentTimeMillis() - t0

            withContext(Dispatchers.Swing) {
                isRunning = false
                when (result) {
                    is ExecutionResult.QueryResult -> {
                        rawItems.clear(); tableModel.rowCount = 0; tableModel.columnCount = 0
                        rawItems += result.items
                        renderTableResults(result.items)

                        val hasNext = result.nextKey != null && result.items.isNotEmpty()
                        if (hasNext && pageKeys.size == currentPage) pageKeys.add(result.nextKey)
                        updateNavButtons(hasNext)

                        rowCountPill.text = " ${result.items.size} rows "
                        timingPill.text   = " ● ${ms}ms "
                        rcuPill.text      = result.consumedRcu?.let { " ${"%.1f".format(it)} RCU " } ?: ""

                        onHistoryEntry?.invoke(SidebarPanel.HistoryEntry("Scan", tableName, ms))
                    }
                    is ExecutionResult.Error -> showError(result.message)
                }
            }
        }
    }

    private fun updateNavButtons(hasNext: Boolean) {
        firstBtn.isEnabled     = currentPage > 1
        prevBtn.isEnabled      = currentPage > 1
        nextBtn.isEnabled      = hasNext
        // enabled when we've cached start-keys for pages beyond the current one
        lastBtn.isEnabled      = pageKeys.size > currentPage
        deleteRowBtn.isEnabled = rawItems.isNotEmpty()
    }

    // ── Cell rendering ────────────────────────────────────────────────────────

    private inner class SemanticCellRenderer : TableCellRenderer {
        private val defaultRenderer = DefaultTableCellRenderer()

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
        ): Component {
            val raw     = rawItems.getOrNull(table.convertRowIndexToModel(row))
            val colName = if (column < table.columnCount) table.getColumnName(column) else null
            val av      = if (raw != null && colName != null) raw[colName] else null

            val bg = when {
                isSelected   -> DColors.accentSoft
                row % 2 == 1 -> DColors.bg2
                else         -> DColors.bg1
            }

            // Row-number column — centered, dim, smaller monospaced font
            if (column == 0) {
                return (defaultRenderer.getTableCellRendererComponent(
                    table, value, false, false, row, column) as JLabel).apply {
                    background = bg; foreground = DColors.fg3; isOpaque = true
                    font = Font(Font.MONOSPACED, Font.PLAIN, 10)
                    horizontalAlignment = SwingConstants.CENTER
                    border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
                }
            }

            return when {
                av == null -> defaultCell(value?.toString() ?: "", bg, DColors.fg3)

                av.bool() != null -> boolPillCell(av.bool()!!, bg, isSelected)

                colName != null && colName == cachedSchema?.partitionKey?.name ->
                    defaultCell(av.displayValue(), bg, DColors.accent).also {
                        (it as? JLabel)?.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    }

                av.n() != null -> defaultCell(av.n()!!, bg, DColors.synNum).also {
                    (it as? JLabel)?.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                }

                av.s() != null && av.s()!!.matches(Regex("""\d{4}-\d{2}-\d{2}T.*""")) -> {
                    val rel = relativeTime(av.s()!!)
                    defaultCell("$rel  ${av.s()!!.take(10)}", bg, DColors.fg2).also {
                        (it as? JLabel)?.font = it.font.deriveFont(11.5f)
                    }
                }

                av.m().isNotEmpty() -> defaultCell("{Map: ${av.m().size}}", bg, DColors.fg3).also {
                    (it as? JLabel)?.font = it.font.deriveFont(Font.ITALIC, 11.5f)
                }
                av.l().isNotEmpty()  -> defaultCell("[List: ${av.l().size}]", bg, DColors.fg3).also {
                    (it as? JLabel)?.font = it.font.deriveFont(Font.ITALIC, 11.5f)
                }

                else -> defaultCell(av.displayValue(), bg, DColors.fg0)
            }
        }

        private fun defaultCell(text: String, bg: Color, fg: Color): Component {
            val lbl = defaultRenderer.getTableCellRendererComponent(
                resultsTable, text, false, false, 0, 0) as JLabel
            lbl.background          = bg
            lbl.foreground          = fg
            lbl.isOpaque            = true
            lbl.font                = lbl.font.deriveFont(Font.PLAIN, 12f)
            lbl.horizontalAlignment = SwingConstants.LEFT
            lbl.border              = BorderFactory.createEmptyBorder(0, 8, 0, 8)
            return lbl
        }

        private fun boolPillCell(v: Boolean, bg: Color, selected: Boolean): Component {
            val cellBg = if (selected) DColors.accentSoft else bg
            val pillBg = if (v) DColors.goodBg else DColors.badBg
            val pillFg = if (v) DColors.good   else DColors.bad
            val dotClr = if (v) DColors.good   else DColors.bad
            val label  = if (v) "true" else "false"

            return object : JPanel() {
                private val pillFont = font.deriveFont(Font.PLAIN, 11f).deriveFont(
                    mapOf(java.awt.font.TextAttribute.TRACKING to 0.08f)
                )

                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

                    // Use component.background — JBTable sets this via prepareRenderer
                    // so hover / selection / alternating rows all match other columns exactly
                    g2.color = background
                    g2.fillRect(0, 0, width, height)

                    val f    = pillFont
                    val fm   = g2.getFontMetrics(f)
                    val dotD = 7; val padL = 8; val dotGap = 5; val padR = 10
                    val pillH = 20; val pillX = 6
                    val pillW = padL + dotD + dotGap + fm.stringWidth("false") + padR
                    val pillY = (height - pillH) / 2

                    g2.color = pillBg
                    g2.fillRoundRect(pillX, pillY, pillW, pillH, 16, 16)

                    g2.color = dotClr
                    g2.fillOval(pillX + padL, pillY + (pillH - dotD) / 2, dotD, dotD)

                    g2.font  = f
                    g2.color = pillFg
                    val textX = pillX + padL + dotD + dotGap
                    val textY = (height + fm.ascent - fm.descent) / 2
                    g2.drawString(label, textX, textY)
                }
            }.apply {
                isOpaque = true
                background = cellBg   // initial value; JBTable overrides this on each paint
            }
        }
    }

    private fun relativeTime(iso: String): String = runCatching {
        val d = (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 86_400_000
        when { d < 1 -> "today"; d < 7 -> "${d}d ago"; d < 30 -> "${d / 7}w ago"; else -> "${d / 30}mo ago" }
    }.getOrElse { iso.take(10) }

    // ── Table rendering ───────────────────────────────────────────────────────

    private fun renderTableResults(items: List<Map<String, AttributeValue>>) {
        if (items.isEmpty()) { rowCountPill.text = " 0 rows "; return }

        // Restore any hidden columns before rebuilding so the model stays consistent
        hiddenColumns.values.forEach { resultsTable.columnModel.addColumn(it) }
        hiddenColumns.clear()

        val existingCols = (0 until tableModel.columnCount)
            .map { tableModel.getColumnName(it) }.filter { it != "#" }.toSet()
        val allCols = if (existingCols.isEmpty()) {
            items.flatMap { it.keys }.distinct().sortedWith(Comparator { a, b ->
                val pk = cachedSchema?.partitionKey?.name
                val sk = cachedSchema?.sortKey?.name
                when { a == pk -> -2; b == pk -> 2; a == sk -> -1; b == sk -> 1; else -> a.compareTo(b) }
            })
        } else (existingCols + items.flatMap { it.keys }).distinct().toList()

        tableModel.setColumnIdentifiers((listOf("#") + allCols).toTypedArray())
        val cols = allCols
        val rowOffset = (currentPage - 1) * currentPageSize
        items.forEachIndexed { idx, item ->
            tableModel.addRow(
                (listOf("${rowOffset + idx + 1}") + cols.map { col -> item[col]?.displayValue() ?: "" }).toTypedArray()
            )
        }

        // Column header decoration — row number + PK badge
        val pkName = cachedSchema?.partitionKey?.name
        resultsTable.tableHeader.defaultRenderer = object : TableCellRenderer {
            private val def = resultsTable.tableHeader.defaultRenderer
            override fun getTableCellRendererComponent(tbl: JTable, v: Any?, sel: Boolean, foc: Boolean, r: Int, c: Int): Component {
                val base = def.getTableCellRendererComponent(tbl, v, sel, foc, r, c) as? JLabel
                    ?: return def.getTableCellRendererComponent(tbl, v, sel, foc, r, c)
                base.background = DColors.bg2; base.foreground = DColors.fg2; base.isOpaque = true
                base.font = base.font.deriveFont(Font.PLAIN, 11.5f)
                base.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
                val colName = tbl.getColumnName(c)

                // Row-number column header
                if (colName == "#") {
                    base.text = "#"; base.foreground = DColors.fg3
                    base.horizontalAlignment = SwingConstants.CENTER
                    return base
                }

                // PK column — render as panel with a badge, properly centred
                if (colName == pkName) {
                    return JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.LINE_AXIS)
                        background = DColors.bg2; isOpaque = true
                        border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
                        add(object : JLabel("PK") {
                            override fun paintComponent(g: Graphics) {
                                val g2 = g as Graphics2D
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                                g2.color = DColors.bg3; g2.fillRoundRect(0, 0, width, height, 4, 4)
                                super.paintComponent(g)
                            }
                        }.apply {
                            font = base.font.deriveFont(Font.BOLD, 9f); foreground = DColors.accent
                            border = BorderFactory.createEmptyBorder(1, 3, 1, 3); isOpaque = false
                            alignmentY = Component.CENTER_ALIGNMENT
                        })
                        add(Box.createHorizontalStrut(4))
                        add(JLabel("$colName ↕").apply {
                            font = base.font; foreground = DColors.accent
                            alignmentY = Component.CENTER_ALIGNMENT
                        })
                    }
                }

                base.text = "$colName ↕"
                return base
            }
        }

        rowSorter = TableRowSorter(tableModel)
        resultsTable.rowSorter = rowSorter
        applyFilter()
        fitColumns()
    }

    private fun fitColumns() {
        if (resultsTable.columnCount == 0) return
        // Pin the row-number column (#)
        resultsTable.columnModel.getColumn(0).let {
            it.preferredWidth = 44; it.minWidth = 44; it.maxWidth = 60
        }
        for (col in 1 until resultsTable.columnCount) {
            var max = 80
            val hc = resultsTable.tableHeader.defaultRenderer
                .getTableCellRendererComponent(resultsTable, resultsTable.getColumnName(col), false, false, -1, col)
            max = maxOf(max, hc.preferredSize.width + 20)
            for (row in 0 until minOf(resultsTable.rowCount, 80)) {
                val c = resultsTable.prepareRenderer(resultsTable.getCellRenderer(row, col), row, col)
                max = maxOf(max, c.preferredSize.width + 16)
            }
            resultsTable.columnModel.getColumn(col).preferredWidth = minOf(max, 300)
        }
    }

    private fun applyFilter() {
        val text = filterInput.text.trim()
        rowSorter?.rowFilter = if (text.isEmpty()) null else
            javax.swing.RowFilter.regexFilter("(?i)${Regex.escape(text)}")
    }

    // ── Error display ─────────────────────────────────────────────────────────

    private fun showError(msg: String) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE)
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private fun showContextMenu(e: MouseEvent) {
        val row = resultsTable.rowAtPoint(e.point)
        if (row >= 0 && !resultsTable.isRowSelected(row)) resultsTable.setRowSelectionInterval(row, row)
        JPopupMenu().apply {
            add(JMenuItem("Edit item…").also { mi ->
                mi.addActionListener {
                    val r = resultsTable.selectedRow.takeIf { it >= 0 } ?: return@addActionListener
                    openItemEditor(rawItems.getOrNull(resultsTable.convertRowIndexToModel(r)) ?: return@addActionListener)
                }
            })
            add(JMenuItem("Delete selected").also { mi -> mi.addActionListener { batchDelete() } })
            addSeparator()
            add(JMenuItem("Copy cell").also { mi ->
                mi.addActionListener {
                    val r = resultsTable.selectedRow; val c = resultsTable.selectedColumn
                    if (r >= 0 && c >= 0) Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(resultsTable.getValueAt(r, c)?.toString() ?: ""), null)
                }
            })
        }.show(resultsTable, e.x, e.y)
    }

    // ── Item CRUD ─────────────────────────────────────────────────────────────

    private fun addRow() {
        val schema = cachedSchema
        val pkKeys = buildSet {
            schema?.partitionKey?.name?.let { add(it) }
            schema?.sortKey?.name?.let     { add(it) }
        }
        ItemEditorDialog(project, emptyMap(), pkKeys,
            onSave   = { updated -> saveNewItem(updated) },
            onDelete = {},
        ).show()
    }

    private fun openItemEditor(item: Map<String, AttributeValue>) {
        val schema = cachedSchema
        val pkKeys = buildSet {
            schema?.partitionKey?.name?.let { add(it) }
            schema?.sortKey?.name?.let     { add(it) }
        }
        ItemEditorDialog(project, item, pkKeys,
            onSave   = { updated -> saveItem(item, updated) },
            onDelete = { deleteItem(item) },
        ).show()
    }

    private fun saveNewItem(item: Map<String, AttributeValue>) {
        scope.launch {
            runCatching {
                registry.clientFor(connectionName)
                    .putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
                    .await()
            }.onSuccess  {
                withContext(Dispatchers.Swing) {
                    recordHistory(item, "Created")
                    execute()
                }
            }.onFailure  { ex -> withContext(Dispatchers.Swing) { Messages.showErrorDialog(project, ex.message, "Save Failed") } }
        }
    }

    private fun saveItem(original: Map<String, AttributeValue>, updated: Map<String, AttributeValue>) {
        val schema = cachedSchema ?: return
        scope.launch {
            runCatching {
                val client = registry.clientFor(connectionName)
                val key = buildMap {
                    put(schema.partitionKey.name, updated[schema.partitionKey.name]!!)
                    schema.sortKey?.name?.let { put(it, updated[it]!!) }
                }

                // Attributes to set/update (non-key fields present in updated)
                val toSet    = updated.filterKeys { it !in key }
                // Attributes to remove (present in original, absent in updated, not a key)
                val toRemove = original.keys.filter { it !in key && it !in updated }

                if (toSet.isEmpty() && toRemove.isEmpty()) return@runCatching

                val exprNames  = mutableMapOf<String, String>()
                val exprValues = mutableMapOf<String, AttributeValue>()

                val setParts = toSet.entries.mapIndexed { i, (k, v) ->
                    exprNames["#a$i"] = k
                    exprValues[":v$i"] = v
                    "#a$i = :v$i"
                }
                val removeParts = toRemove.mapIndexed { i, k ->
                    exprNames["#r$i"] = k
                    "#r$i"
                }

                val expr = buildString {
                    if (setParts.isNotEmpty())    append("SET ${setParts.joinToString(", ")}")
                    if (removeParts.isNotEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append("REMOVE ${removeParts.joinToString(", ")}")
                    }
                }

                client.updateItem(
                    UpdateItemRequest.builder()
                        .tableName(tableName).key(key)
                        .updateExpression(expr)
                        .expressionAttributeNames(exprNames)
                        .apply { if (exprValues.isNotEmpty()) expressionAttributeValues(exprValues) }
                        .build()
                ).await()
            }.onSuccess {
                withContext(Dispatchers.Swing) {
                    val changes = buildList {
                        updated.forEach { (k, v) ->
                            val old = original[k]?.displayValue()
                            val new = v.displayValue()
                            if (old != new) add("$k: $old → $new")
                        }
                        original.keys.filter { it !in updated }.forEach { add("$it: removed") }
                    }
                    recordHistory(original, "Edited", changes)
                    execute()
                    inspector.showItem(updated, cachedSchema)
                }
            }.onFailure { ex ->
                withContext(Dispatchers.Swing) {
                    Messages.showErrorDialog(project, ex.message ?: "Unknown error", "Save Failed")
                }
            }
        }
    }

    private fun deleteItem(item: Map<String, AttributeValue>) {
        val schema = cachedSchema ?: return
        scope.launch {
            runCatching {
                val client = registry.clientFor(connectionName)
                val key    = buildMap {
                    put(schema.partitionKey.name, item[schema.partitionKey.name]!!)
                    schema.sortKey?.name?.let { sk -> item[sk]?.let { put(sk, it) } }
                }
                client.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build()).await()
            }.onSuccess  {
                withContext(Dispatchers.Swing) {
                    recordHistory(item, "Deleted")
                    execute()
                }
            }.onFailure  { ex -> withContext(Dispatchers.Swing) { Messages.showErrorDialog(project, ex.message, "Delete Failed") } }
        }
    }

    fun batchDelete() {
        val selected = resultsTable.selectedRows
        if (selected.isEmpty()) return
        val schema = cachedSchema ?: return
        val confirm = Messages.showYesNoDialog(project, "Delete ${selected.size} item(s)?", "Batch Delete", Messages.getWarningIcon())
        if (confirm != Messages.YES) return
        val items = selected.map { rawItems[resultsTable.convertRowIndexToModel(it)] }
        scope.launch {
            val client = registry.clientFor(connectionName)
            items.forEach { item ->
                runCatching {
                    val key = buildMap {
                        put(schema.partitionKey.name, item[schema.partitionKey.name]!!)
                        schema.sortKey?.name?.let { sk -> item[sk]?.let { put(sk, it) } }
                    }
                    client.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build()).await()
                }
            }
            withContext(Dispatchers.Swing) { execute() }
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** Down-arrow icon used for the JSON download button. */
    private object DownArrowIcon : Icon {
        override fun getIconWidth()  = 16
        override fun getIconHeight() = 16
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = (g as Graphics2D).create() as Graphics2D
            g2.translate(x, y)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color  = DColors.fg1
            g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            // shaft
            g2.draw(java.awt.geom.Line2D.Double(8.0, 2.5, 8.0, 10.5))
            // arrowhead
            val head = Path2D.Double().apply {
                moveTo(4.5, 7.5); lineTo(8.0, 11.5); lineTo(11.5, 7.5)
            }
            g2.draw(head)
            // tray line
            g2.draw(java.awt.geom.Line2D.Double(3.5, 13.5, 12.5, 13.5))
            g2.dispose()
        }
    }

    /**
     * Saves selected rows (or all rows if nothing is selected) filtered to the
     * currently visible columns as a JSON array into ~/Downloads/.
     */
    fun exportSelectedJson() {
        if (rawItems.isEmpty()) return

        // Rows: selected or all
        val selectedViewRows = resultsTable.selectedRows
        val modelRows = if (selectedViewRows.isEmpty())
            (0 until resultsTable.rowCount).map { resultsTable.convertRowIndexToModel(it) }
        else
            selectedViewRows.map { resultsTable.convertRowIndexToModel(it) }
        val items = modelRows.mapNotNull { rawItems.getOrNull(it) }

        // Columns: visible columns only, excluding the row-number "#" column
        val visibleCols = (0 until resultsTable.columnCount)
            .map { resultsTable.getColumnName(it) }
            .filter { it != "#" }

        // Build JSON
        val json = buildString {
            append("[\n")
            items.forEachIndexed { i, item ->
                append("  {\n")
                val entries = visibleCols.mapNotNull { col -> item[col]?.let { col to it } }
                entries.forEachIndexed { j, (k, v) ->
                    append("    \"$k\": ${v.toJsonValue()}")
                    if (j < entries.size - 1) append(",")
                    append("\n")
                }
                append("  }")
                if (i < items.size - 1) append(",")
                append("\n")
            }
            append("]")
        }

        // Save to ~/Downloads/
        runCatching {
            val ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val dir  = java.nio.file.Paths.get(System.getProperty("user.home"), "Downloads").toFile()
                .also { it.mkdirs() }
            val file = File(dir, "$tableName-$ts.json")
            file.writeText(json)
            Messages.showInfoMessage(
                project,
                "Saved ${items.size} row(s) → ${file.absolutePath}",
                "Export Complete"
            )
        }.onFailure { ex ->
            Messages.showErrorDialog(project, ex.message ?: "Unknown error", "Export Failed")
        }
    }

    /** Proper JSON serialisation of an AttributeValue. */
    private fun AttributeValue.toJsonValue(): String = when {
        s()    != null -> "\"${s()!!.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        nul()  == true -> "null"
        ss().isNotEmpty() -> ss().joinToString(", ", "[", "]") { "\"$it\"" }
        ns().isNotEmpty() -> ns().joinToString(", ", "[", "]")
        l().isNotEmpty()  -> l().joinToString(", ", "[", "]") { it.toJsonValue() }
        m().isNotEmpty()  -> m().entries.joinToString(", ", "{", "}") {
            "\"${it.key}\": ${it.value.toJsonValue()}"
        }
        b() != null -> "\"<Binary>\""
        else        -> "null"
    }

    fun showExportDialog() = exportSelectedJson()   // kept for "more" menu compatibility

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearResults() {
        tableModel.rowCount = 0; tableModel.columnCount = 0
        rawItems.clear()
        pageKeys.clear(); pageKeys.add(null)
        currentPage = 1
        updateNavButtons(hasNext = false)
        rowCountPill.text = " — "; timingPill.text = " — "; rcuPill.text = ""
        rowSorter = null; filterInput.text = ""
        hiddenColumns.clear()
        inspector.clear()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun showTableData() { if (rawItems.isEmpty() && tableModel.rowCount == 0) execute() }
    fun getSchema()     = cachedSchema
    fun hasResults()    = rawItems.isNotEmpty()

    // ── Widget helpers ────────────────────────────────────────────────────────

    /** IntelliJ-style bordered button (used in row-toolbar). */
    private fun ijBtn(icon: Icon, tooltip: String, action: () -> Unit) =
        JButton(icon).apply {
            toolTipText         = tooltip
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            border              = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DColors.line, 1, true),
                BorderFactory.createEmptyBorder(2, 4, 2, 4))
            preferredSize       = Dimension(26, 26)
            maximumSize         = Dimension(26, 26)
            minimumSize         = Dimension(26, 26)
            addActionListener   { action() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { if (isEnabled) background = DColors.bg3 }
                override fun mouseExited(e: MouseEvent)  { background = DColors.bg2 }
            })
        }

    /** Icon-only mini button (used in results-toolbar). */
    private fun miniBtn(icon: Icon, tip: String, action: () -> Unit) =
        JButton(icon).apply {
            toolTipText         = tip
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            preferredSize       = Dimension(22, 22)
            addActionListener   { action() }
        }

    /** Rounded pill label for stats. */
    private fun statPill(text: String, fg: Color = DColors.fg2) =
        object : JLabel(" $text ") {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // fill
                g2.color = DColors.bg2
                g2.fillRoundRect(0, 0, width, height, 16, 16)
                // border stroke drawn manually so corners stay rounded
                g2.color = DColors.line
                g2.drawRoundRect(0, 0, width - 1, height - 1, 16, 16)
                super.paintComponent(g)
            }
        }.apply {
            foreground = fg; font = font.deriveFont(Font.BOLD, 11f); isOpaque = false
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }

    // ── AttributeValue helpers ────────────────────────────────────────────────

    private fun AttributeValue.displayValue(): String = when {
        s()    != null            -> s()!!
        n()    != null            -> n()!!
        bool() != null            -> bool().toString()
        ss().isNotEmpty()         -> ss().joinToString(", ", "[", "]")
        ns().isNotEmpty()         -> ns().joinToString(", ", "[", "]")
        l().isNotEmpty()          -> "[List:${l().size}]"
        m().isNotEmpty()          -> "{Map:${m().size}}"
        nul() == true             -> "null"
        b()   != null             -> "<Binary>"
        else                      -> "?"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun dispose() = scope.cancel()

    // ── Result types ─────────────────────────────────────────────────────────

    private sealed class ExecutionResult {
        data class QueryResult(
            val items: List<Map<String, AttributeValue>>,
            val nextKey: Map<String, AttributeValue>?,
            val consumedRcu: Double?,
        ) : ExecutionResult()
        data class Error(val message: String) : ExecutionResult()
    }
}
