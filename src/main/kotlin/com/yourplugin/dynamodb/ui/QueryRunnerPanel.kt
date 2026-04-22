package com.yourplugin.dynamodb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
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
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

/**
 * Query Runner panel — DQL execution UI.
 */
class QueryRunnerPanel(
    private val project: Project,
    private val connectionName: String,
    private val tableName: String,
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val registry get() = ApplicationManager.getApplication()
        .getService(DynamoConnectionRegistry::class.java)

    // ── Editor ────────────────────────────────────────────────────────────────
    private val editor: EditorEx = run {
        val dqlType = FileTypeManager.getInstance().getFileTypeByExtension("dql")
        val doc = EditorFactory.getInstance().createDocument(defaultQuery())
        (EditorFactory.getInstance().createEditor(doc, project, dqlType, false) as EditorEx).also { ed ->
            // Strip away all overlay/gutter UI so the editor is a clean query input
            ed.settings.apply {
                isLineNumbersShown = false
                isLineMarkerAreaShown = false
                isFoldingOutlineShown = false
                isRightMarginShown = false
                additionalLinesCount = 0
                additionalColumnsCount = 0
                isCaretRowShown = false
                isShowIntentionBulb = false
            }
            // Remove the header panel (breadcrumbs / notification bar) that floats over sibling components
            ed.headerComponent = null
            ed.permanentHeaderComponent = null
        }
    }

    // ── Query mode toggle ─────────────────────────────────────────────────────
    private enum class QueryMode { SQL, NATIVE }
    private var queryMode = QueryMode.SQL

    private val sqlRadio    = JRadioButton("SQL", true)
    private val nativeRadio = JRadioButton("DynamoDB Native", false)

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private val runBtn      = JButton("▶ Execute").apply { addActionListener { execute() } }
    private val clearBtn    = JButton("✕ Clear").apply { addActionListener { clearResults() } }
    private val rawToggle   = JCheckBox("Raw JSON").apply { addActionListener { toggleRaw() } }
    private val statusLabel = JLabel("Ready")
    private val contextLabel = JLabel("$connectionName · $tableName")

    // ── Filter bar ────────────────────────────────────────────────────────────
    private val filterField = JTextField(20).apply {
        toolTipText = "Filter results…"
        putClientProperty("JTextField.placeholderText", "Filter results…")
    }
    private var rowSorter: TableRowSorter<DefaultTableModel>? = null

    // ── Native DynamoDB query inputs ──────────────────────────────────────────
    private val nativeOperationCombo = JComboBox(arrayOf("Scan", "Query"))
    private val nativeKeyCondField = JTextField().apply {
        toolTipText = "Key Condition Expression (required for Query). e.g. cafe_id = :pk"
        putClientProperty("JTextField.placeholderText", "cafe_id = :pk AND begins_with(sort_key, :prefix)")
    }
    private val nativeFilterField = JTextField().apply {
        toolTipText = "Filter Expression (optional). e.g. city = :city"
        putClientProperty("JTextField.placeholderText", "city = :city AND age > :minAge")
    }
    private val nativeAttrNamesArea = JTextArea(3, 1).apply {
        toolTipText = "Expression Attribute Names (JSON). e.g. {\"#n\": \"name\"}"
        lineWrap = true; wrapStyleWord = true; font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        putClientProperty("JTextField.placeholderText", "{\"#n\": \"name\"}")
    }
    private val nativeAttrValuesArea = JTextArea(4, 1).apply {
        toolTipText = "Expression Attribute Values (JSON). e.g. {\":city\": \"Tampa\", \":minAge\": 25}"
        lineWrap = true; wrapStyleWord = true; font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        putClientProperty("JTextField.placeholderText", "{\":city\": \"Tampa\", \":minAge\": 25}")
    }
    // Cards to switch between SQL editor and native form
    private val inputCards = CardLayout()
    private val inputPanel = JPanel(inputCards)

    // ── Results ───────────────────────────────────────────────────────────────
    private val tableModel = object : DefaultTableModel(0, 0) {
        override fun isCellEditable(r: Int, c: Int) = false
    }
    private val resultsTable = JBTable(tableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        rowHeight = 22
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val col = columnAtPoint(e.point).takeIf { it >= 0 } ?: return
                    autoFitColumn(col)
                }
            }
        })
    }

    private fun fitColumnsToContent() {
        val colCount = resultsTable.columnCount
        if (colCount == 0) return
        val minColWidth = 80
        for (col in 0 until colCount) {
            var maxWidth = minColWidth
            val headerRenderer = resultsTable.tableHeader.defaultRenderer
            val headerComp = headerRenderer.getTableCellRendererComponent(
                resultsTable, resultsTable.columnModel.getColumn(col).headerValue, false, false, -1, col)
            maxWidth = maxOf(maxWidth, headerComp.preferredSize.width + 16)
            for (row in 0 until minOf(resultsTable.rowCount, 100)) {
                val renderer = resultsTable.getCellRenderer(row, col)
                val comp = resultsTable.prepareRenderer(renderer, row, col)
                maxWidth = maxOf(maxWidth, comp.preferredSize.width + 8)
            }
            resultsTable.columnModel.getColumn(col).preferredWidth = maxWidth
        }
        resultsTable.revalidate()
    }

    private fun autoFitColumn(col: Int) {
        var maxWidth = resultsTable.tableHeader.getHeaderRect(col).width
        val headerRenderer = resultsTable.tableHeader.defaultRenderer
        val headerComp = headerRenderer.getTableCellRendererComponent(
            resultsTable, resultsTable.columnModel.getColumn(col).headerValue, false, false, -1, col)
        maxWidth = maxOf(maxWidth, headerComp.preferredSize.width + 16)
        for (row in 0 until minOf(resultsTable.rowCount, 200)) {
            val renderer = resultsTable.getCellRenderer(row, col)
            val comp = resultsTable.prepareRenderer(renderer, row, col)
            maxWidth = maxOf(maxWidth, comp.preferredSize.width + 8)
        }
        resultsTable.columnModel.getColumn(col).preferredWidth = maxWidth
    }

    private val rawArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val resultCards = CardLayout()
    private val resultPanel = JPanel(resultCards).apply {
        add(JBScrollPane(resultsTable), "table")
        add(JBScrollPane(rawArea), "raw")
    }

    // ── Item detail panel ─────────────────────────────────────────────────────
    private val detailPanel = ItemDetailPanel()

    // ── Pagination state ──────────────────────────────────────────────────────
    private var cachedSchema: TableSchema? = null
    private var rawItems = mutableListOf<Map<String, AttributeValue>>()
    // pageKeys[i] holds the exclusiveStartKey for page (i+1); pageKeys[0] = null means page 1
    private val pageKeys = mutableListOf<Map<String, AttributeValue>?>(null)
    // Cumulative item count BEFORE each page: pageStartOffset[0]=0, pageStartOffset[1]=items on page1, etc.
    private val pageStartOffset = mutableListOf(0)
    private var currentPage = 1
    // The LIMIT extracted from the current query (null = no limit)
    private var queryTotalLimit: Int? = null

    // ── Pagination controls (wired up in buildPaginationBar) ──────────────────
    private val prevPageBtn = JButton("◀ Prev").apply {
        isEnabled = false
        addActionListener { goToPage(currentPage - 1) }
    }
    private val nextPageBtn = JButton("Next ▶").apply {
        isEnabled = false
        addActionListener { goToPage(currentPage + 1) }
    }
    private val pageLabel = JLabel("Page 1")

    // ── Per-page size selector ─────────────────────────────────────────────────
    private val pageSizeOptions = arrayOf(10, 20, 50, 100, 500)
    private val pageSizeCombo = JComboBox(pageSizeOptions).apply {
        selectedItem = 20          // default: 20 rows per page
        maximumSize = preferredSize
        toolTipText = "Rows per page (applies when no LIMIT is written in the query)"
        addActionListener {
            // Re-run from page 1 whenever the page size changes
            execute()
        }
    }

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(buildMainArea(), BorderLayout.CENTER)

        // Row click → item detail; double-click → editor
        resultsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = resultsTable.rowAtPoint(e.point)
                if (row < 0) return
                val modelRow = resultsTable.convertRowIndexToModel(row)
                val item = rawItems.getOrNull(modelRow) ?: return
                if (e.clickCount == 1) detailPanel.showItem(item)
                if (e.clickCount == 2) openItemEditor(item)
            }
        })

        // Right-click context menu
        resultsTable.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent)  { if (e.isPopupTrigger) showContextMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showContextMenu(e) }
        })

        // Filter bar live filtering
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent)  { applyFilter() }
            override fun removeUpdate(e: DocumentEvent)  { applyFilter() }
            override fun changedUpdate(e: DocumentEvent) { applyFilter() }
        })


        // Pre-load schema
        scope.launch {
            cachedSchema = runCatching {
                TableSchemaService.describe(registry.clientFor(connectionName), tableName)
            }.getOrNull()
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun toolbarSolidDivider(): JComponent {
        val sep = JSeparator(SwingConstants.VERTICAL)
        sep.preferredSize = Dimension(9, 24)
        sep.minimumSize   = Dimension(9, 24)
        sep.maximumSize   = Dimension(9, 24)
        return sep
    }

    private fun buildToolbar(): JComponent {
        ButtonGroup().also { it.add(sqlRadio); it.add(nativeRadio) }
        sqlRadio.addActionListener    { switchMode(QueryMode.SQL) }
        nativeRadio.addActionListener { switchMode(QueryMode.NATIVE) }

        return JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            add(runBtn)
            add(toolbarSolidDivider())
            add(clearBtn)
            add(rawToggle)
            add(toolbarSolidDivider())
            add(JLabel("Mode:"))
            add(Box.createHorizontalStrut(4))
            add(sqlRadio)
            add(nativeRadio)
            add(toolbarSolidDivider())
            add(JLabel("Filter:"))
            add(Box.createHorizontalStrut(4))
            add(filterField)
            add(toolbarSolidDivider())
            add(JButton("Export…").apply { addActionListener { showExportDialog() } })
            add(JButton("Delete Selected").apply {
                toolTipText = "Delete selected rows from DynamoDB"
                addActionListener { batchDelete() }
            })
            add(Box.createHorizontalGlue())
            add(contextLabel.apply {
                border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
                foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
            })
            add(statusLabel.apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") ?: Color(130, 130, 130), 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
                )
            })
        }
    }

    private fun buildNativeForm(): JComponent {
        // Operation selector row
        val opRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JLabel("Operation:"))
            add(nativeOperationCombo)
            nativeOperationCombo.addActionListener {
                val isQuery = nativeOperationCombo.selectedItem == "Query"
                nativeKeyCondField.isEnabled = isQuery
            }
        }

        val form = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(3, 4, 3, 4)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
        }

        fun addRow(label: String, comp: JComponent, multiLine: Boolean = false, row: Int) {
            gbc.gridy = row
            gbc.gridx = 0; gbc.weightx = 0.0; gbc.weighty = 0.0
            form.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            if (multiLine) { gbc.weighty = 0.5; gbc.fill = GridBagConstraints.BOTH }
            form.add(if (multiLine) JBScrollPane(comp) else comp, gbc)
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weighty = 0.0
        }

        addRow("Operation:", opRow, row = 0)
        addRow("Key Condition:", nativeKeyCondField, row = 1)
        addRow("Filter:", nativeFilterField, row = 2)
        addRow("Attr Names:", nativeAttrNamesArea, multiLine = true, row = 3)
        addRow("Attr Values:", nativeAttrValuesArea, multiLine = true, row = 4)

        // hint label
        gbc.gridy = 5; gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 0.0
        val hint = JLabel("<html><small>Attr Names: {\"#n\": \"name\"} &nbsp;|&nbsp; " +
                "Attr Values: {\":city\": \"Tampa\", \":age\": 25, \":flag\": true}</small></html>").apply {
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
        }
        form.add(hint, gbc)

        return JBScrollPane(form)
    }

    private fun buildMainArea(): JComponent {
        // Build inputPanel cards
        inputPanel.add(editor.component, "sql")
        inputPanel.add(buildNativeForm(), "native")
        inputCards.show(inputPanel, "sql")

        val resultsPanelWithFooter = JPanel(BorderLayout()).apply {
            add(resultPanel, BorderLayout.CENTER)
            add(buildPaginationBar(), BorderLayout.SOUTH)
        }
        val rightSplit = JBSplitter(true, 0.65f).apply {
            firstComponent  = resultsPanelWithFooter
            secondComponent = detailPanel
        }
        return JBSplitter(true, 0.35f).apply {
            firstComponent  = inputPanel
            secondComponent = rightSplit
        }
    }

    private fun switchMode(mode: QueryMode) {
        queryMode = mode
        inputCards.show(inputPanel, if (mode == QueryMode.SQL) "sql" else "native")
        // Update key condition field state
        if (mode == QueryMode.NATIVE) {
            val isQuery = nativeOperationCombo.selectedItem == "Query"
            nativeKeyCondField.isEnabled = isQuery
        }
    }

    private fun buildPaginationBar() = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
        add(prevPageBtn)
        add(pageLabel)
        add(nextPageBtn)
        add(Box.createHorizontalStrut(16))
        add(JLabel("Rows per page:"))
        add(pageSizeCombo)
    }


    // ── Execution ─────────────────────────────────────────────────────────────

    /** Called by the Execute button — always resets pagination back to page 1. */
    private fun execute() {
        pageKeys.clear()
        pageKeys.add(null)   // page 1 has no start key
        pageStartOffset.clear()
        pageStartOffset.add(0)
        currentPage = 1
        if (queryMode == QueryMode.SQL) {
            val dql = ApplicationManager.getApplication().runReadAction<String> { editor.document.text }.trim()
            queryTotalLimit = extractTotalLimit(dql)
        } else {
            queryTotalLimit = null
        }
        loadCurrentPage()
    }

    /** Navigate to a specific page (1-based). */
    private fun goToPage(page: Int) {
        if (page < 1) return
        currentPage = page
        loadCurrentPage()
    }

    private fun loadCurrentPage() {
        val startKey = pageKeys.getOrNull(currentPage - 1) ?: run {
            // Requested page key not cached yet — this shouldn't happen in normal flow
            if (currentPage != 1) return
            null
        }

        val dql = ApplicationManager.getApplication().runReadAction<String> { editor.document.text }.trim()
        if (queryMode == QueryMode.SQL && dql.isBlank()) { statusLabel.text = "Query is empty"; return }

        runBtn.isEnabled = false
        prevPageBtn.isEnabled = false
        nextPageBtn.isEnabled = false
        statusLabel.text = "Executing…"

        scope.launch {
            try {
                val result: ExecutionResult = runCatching {
                    val client = registry.clientFor(connectionName)
                    if (queryMode == QueryMode.NATIVE) {
                        executeNativeQuery(client, startKey)
                    } else if (dql.trimStart().uppercase().startsWith("UPDATE")) {
                        executeUpdate(dql)
                    } else {
                        executeQuery(client, dql, cachedSchema, startKey)
                    }
                }.getOrElse { ex -> ExecutionResult.Error(ex.message ?: "Unknown error") }

                withContext(Dispatchers.Swing) {
                    when (result) {
                        is ExecutionResult.QueryResult -> {
                            val limit = queryTotalLimit
                            val served = pageStartOffset.getOrElse(currentPage - 1) { pageStartOffset.last() }
                            val remaining = if (limit != null) limit - served else Int.MAX_VALUE

                            // Cap items to whatever is remaining of the total limit
                            val items = if (remaining <= 0) emptyList()
                                        else if (result.items.size > remaining) result.items.take(remaining)
                                        else result.items

                            // Record cumulative offset for the NEXT page
                            val nextOffset = served + items.size
                            if (pageStartOffset.size == currentPage) {
                                pageStartOffset.add(nextOffset)
                            }

                            // Replace table contents with this page's items
                            rawItems.clear()
                            tableModel.rowCount = 0
                            tableModel.columnCount = 0
                            detailPanel.clear()
                            rawItems += items
                            renderTableResults(items)

                            // Next page is available only if there's more data AND we haven't hit the total limit
                            val limitExhausted = limit != null && nextOffset >= limit
                            val hasNextKey = result.nextKey != null && items.isNotEmpty()

                            // Cache next page key
                            if (!limitExhausted && hasNextKey && pageKeys.size == currentPage) {
                                pageKeys.add(result.nextKey)
                            }

                            prevPageBtn.isEnabled = currentPage > 1
                            nextPageBtn.isEnabled = !limitExhausted && hasNextKey
                            pageLabel.text = "Page $currentPage"
                            statusLabel.text = "✓ ${items.size} items  ·  page $currentPage" +
                                if (result.consumedRcu != null) "  ·  ${result.consumedRcu} RCU" else ""
                        }
                        is ExecutionResult.UpdateResult -> {
                            statusLabel.text = "✓ Update applied  ·  ${result.consumedWcu} WCU"
                            rawArea.text = "Update successful.\nConsumed WCU: ${result.consumedWcu}"
                            resultCards.show(resultPanel, "raw")
                        }
                        is ExecutionResult.Error -> {
                            statusLabel.text = "✗ Error"
                            showError(result.message)
                        }
                    }
                    if (rawToggle.isSelected) renderRaw()
                }
            } finally {
                // Always re-enable the Execute button — even on cancellation or exception.
                withContext(NonCancellable + Dispatchers.Swing) {
                    runBtn.isEnabled = true
                }
            }
        }
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private fun showContextMenu(e: MouseEvent) {
        val row = resultsTable.rowAtPoint(e.point)
        if (row >= 0 && !resultsTable.isRowSelected(row)) resultsTable.setRowSelectionInterval(row, row)

        val menu = JPopupMenu()
        menu.add(JMenuItem("Edit item…").apply {
            addActionListener {
                val r = resultsTable.selectedRow.takeIf { it >= 0 } ?: return@addActionListener
                openItemEditor(rawItems.getOrNull(resultsTable.convertRowIndexToModel(r)) ?: return@addActionListener)
            }
        })
        menu.add(JMenuItem("Delete selected").apply { addActionListener { batchDelete() } })
        menu.addSeparator()
        menu.add(JMenuItem("Copy cell").apply {
            addActionListener {
                val r = resultsTable.selectedRow; val c = resultsTable.selectedColumn
                if (r >= 0 && c >= 0) {
                    val text = resultsTable.getValueAt(r, c)?.toString() ?: ""
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(text), null)
                }
            }
        })
        menu.show(resultsTable, e.x, e.y)
    }

    // ── Item editor ───────────────────────────────────────────────────────────

    private fun openItemEditor(item: Map<String, AttributeValue>) {
        val schema = cachedSchema
        val pkKeys = buildSet {
            schema?.partitionKey?.name?.let { add(it) }
            schema?.sortKey?.name?.let { add(it) }
        }

        ItemEditorDialog(
            project = project,
            item = item,
            pkKeys = pkKeys,
            onSave = { updated -> saveItem(item, updated) },
            onDelete = { deleteItem(item) },
        ).show()
    }

    private fun saveItem(original: Map<String, AttributeValue>, updated: Map<String, AttributeValue>) {
        val schema = cachedSchema ?: return
        scope.launch {
            val client = registry.clientFor(connectionName)
            runCatching {
                val key = buildMap {
                    put(schema.partitionKey.name, updated[schema.partitionKey.name]!!)
                    schema.sortKey?.name?.let { put(it, updated[it]!!) }
                }
                // Build UpdateItem expression from changed attributes
                val toUpdate = updated.filterKeys { it !in key }
                if (toUpdate.isEmpty()) return@launch

                val names  = toUpdate.keys.mapIndexed { i, k -> "#a$i" to k }.toMap()
                val values = toUpdate.entries.mapIndexed { i, (_, v) -> ":v$i" to v }.toMap()
                val expr   = names.keys.zip(values.keys).joinToString(", ") { (n, v) -> "$n = $v" }

                client.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET $expr")
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build()).await()
            }.onSuccess {
                withContext(Dispatchers.Swing) {
                    statusLabel.text = "✓ Item saved"
                    // Refresh results
                    execute()
                }
            }.onFailure { ex ->
                withContext(Dispatchers.Swing) {
                    Messages.showErrorDialog(project, "Save failed: ${ex.message}", "Error")
                }
            }
        }
    }

    private fun deleteItem(item: Map<String, AttributeValue>) {
        val schema = cachedSchema ?: return
        scope.launch {
            val client = registry.clientFor(connectionName)
            runCatching {
                val key = buildMap {
                    put(schema.partitionKey.name, item[schema.partitionKey.name]!!)
                    schema.sortKey?.name?.let { sk -> item[sk]?.let { put(sk, it) } }
                }
                client.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build()).await()
            }.onSuccess {
                withContext(Dispatchers.Swing) { statusLabel.text = "✓ Item deleted"; execute() }
            }.onFailure { ex ->
                withContext(Dispatchers.Swing) {
                    Messages.showErrorDialog(project, "Delete failed: ${ex.message}", "Error")
                }
            }
        }
    }

    // ── Batch delete ──────────────────────────────────────────────────────────

    private fun batchDelete() {
        val selectedRows = resultsTable.selectedRows
        if (selectedRows.isEmpty()) { statusLabel.text = "No rows selected"; return }
        val schema = cachedSchema ?: return

        val confirm = Messages.showYesNoDialog(
            project,
            "Delete ${selectedRows.size} selected item(s) from DynamoDB?",
            "Batch Delete",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return

        val itemsToDelete = selectedRows.map { row ->
            rawItems[resultsTable.convertRowIndexToModel(row)]
        }

        scope.launch {
            val client = registry.clientFor(connectionName)
            var deleted = 0
            itemsToDelete.forEach { item ->
                runCatching {
                    val key = buildMap {
                        put(schema.partitionKey.name, item[schema.partitionKey.name]!!)
                        schema.sortKey?.name?.let { sk -> item[sk]?.let { put(sk, it) } }
                    }
                    client.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build()).await()
                    deleted++
                }
            }
            withContext(Dispatchers.Swing) {
                statusLabel.text = "✓ Deleted $deleted item(s)"
                execute()
            }
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private fun showExportDialog() {
        if (rawItems.isEmpty()) { statusLabel.text = "No results to export"; return }
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Results"
            addChoosableFileFilter(FileNameExtensionFilter("JSON files", "json"))
            addChoosableFileFilter(FileNameExtensionFilter("CSV files", "csv"))
            fileFilter = choosableFileFilters[1]
            selectedFile = File("${tableName}-results.json")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        runCatching {
            if (file.name.endsWith(".csv")) ResultExporter.exportCsv(rawItems, file)
            else ResultExporter.exportJson(rawItems, file)
            statusLabel.text = "✓ Exported ${rawItems.size} items to ${file.name}"
        }.onFailure { ex ->
            Messages.showErrorDialog(project, "Export failed: ${ex.message}", "Error")
        }
    }

    // ── Filter bar ────────────────────────────────────────────────────────────

    private fun applyFilter() {
        val text = filterField.text.trim()
        rowSorter?.rowFilter = if (text.isEmpty()) null else
            javax.swing.RowFilter.regexFilter("(?i)${Regex.escape(text)}")
    }

    // ── AWS calls ─────────────────────────────────────────────────────────────

    /**
     * Returns the effective page size for the current DynamoDB request.
     * When a LIMIT is set, cap to the remaining items not yet served.
     */
    private fun extractPageSize(): Int {
        val base = pageSizeCombo.selectedItem as? Int ?: 20
        val limit = queryTotalLimit ?: return base
        val served = pageStartOffset.getOrElse(currentPage - 1) { pageStartOffset.last() }
        val remaining = limit - served
        return if (remaining > 0) minOf(base, remaining) else base
    }

    /** Returns the explicit LIMIT from the DQL, or null if none. */
    private fun extractTotalLimit(dql: String): Int? =
        Regex("""LIMIT\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(dql)?.groupValues?.get(1)?.toIntOrNull()

    private suspend fun executeQuery(
        client: software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient,
        dql: String,
        schema: TableSchema?,
        startKey: Map<String, AttributeValue>?,
    ): ExecutionResult {
        val pageSize = extractPageSize()
        val hasWhere = dql.contains("WHERE", ignoreCase = true)

        if (!hasWhere) {
            // Plain scan — no WHERE
            val req = ScanRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .apply { startKey?.let { exclusiveStartKey(it) } }
                .build()
            val resp = client.scan(req).await()
            return ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        }

        val parsed = parseDqlWhereClause(dql)
        val indexName = Regex("""USE\s+INDEX\s*\(\s*(\S+?)\s*\)""", RegexOption.IGNORE_CASE)
            .find(dql)?.groupValues?.get(1)

        // Determine key attribute names for the table (or the chosen GSI/LSI)
        val keyAttrNames: Set<String> = if (schema != null) {
            val keys = mutableSetOf(schema.partitionKey.name)
            schema.sortKey?.let { keys.add(it.name) }
            if (indexName != null) {
                // Also include GSI/LSI key names so they route to keyCondition
                schema.gsis.firstOrNull { it.indexName == indexName }?.let {
                    keys.add(it.partitionKey.name); it.sortKey?.let { sk -> keys.add(sk.name) }
                }
                schema.lsis.firstOrNull { it.indexName == indexName }?.let {
                    keys.add(it.sortKey.name)
                }
            }
            keys
        } else emptySet()

        // Split the parsed expression into individual AND-connected conditions
        // then route each to keyCondition or filterExpression based on the attribute
        val splitConditions = splitAndConditions(parsed.expression)
        val keyCondParts = mutableListOf<String>()
        val filterParts = mutableListOf<String>()

        for (cond in splitConditions) {
            // Find which #alias attributes appear in this condition
            val aliasesInCond = Regex("""#(\w+)""").findAll(cond).map { it.groupValues[1] }.toSet()
            val isKeyCondition = aliasesInCond.isNotEmpty() && aliasesInCond.all { it in keyAttrNames }
            if (isKeyCondition && keyAttrNames.isNotEmpty()) {
                keyCondParts.add(cond)
            } else {
                filterParts.add(cond)
            }
        }

        val keyCondExpr = keyCondParts.joinToString(" AND ")
        val filterExpr  = filterParts.joinToString(" AND ")

        return if (keyCondExpr.isNotBlank()) {
            // Use Query with key conditions; non-key conditions go to filterExpression
            val req = QueryRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .keyConditionExpression(keyCondExpr)
                .apply {
                    if (filterExpr.isNotBlank()) filterExpression(filterExpr)
                    if (parsed.attrNames.isNotEmpty()) expressionAttributeNames(parsed.attrNames)
                    if (parsed.attrValues.isNotEmpty()) expressionAttributeValues(parsed.attrValues)
                    indexName?.let { indexName(it) }
                    startKey?.let { exclusiveStartKey(it) }
                }
                .build()
            val resp = client.query(req).await()
            ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        } else {
            // No key conditions — fall back to Scan with filterExpression
            val effectiveFilter = parsed.expression
            val req = ScanRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .apply {
                    if (effectiveFilter.isNotBlank()) filterExpression(effectiveFilter)
                    if (parsed.attrNames.isNotEmpty()) expressionAttributeNames(parsed.attrNames)
                    if (parsed.attrValues.isNotEmpty()) expressionAttributeValues(parsed.attrValues)
                    startKey?.let { exclusiveStartKey(it) }
                }
                .build()
            val resp = client.scan(req).await()
            ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        }
    }

    /**
     * Splits a DynamoDB condition expression on top-level AND keywords,
     * respecting parentheses so nested expressions are not broken.
     */
    private fun splitAndConditions(expr: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < expr.length) {
            when {
                expr[i] == '(' -> { depth++; i++ }
                expr[i] == ')' -> { depth--; i++ }
                depth == 0 && expr.substring(i).matches(Regex("""(?i)AND\b.*""")) -> {
                    parts.add(expr.substring(start, i).trim())
                    i += 3 // skip "AND"
                    while (i < expr.length && expr[i].isWhitespace()) i++
                    start = i
                }
                else -> i++
            }
        }
        if (start < expr.length) parts.add(expr.substring(start).trim())
        return parts.filter { it.isNotBlank() }
    }

    /**
     * Parses the DQL WHERE clause and returns a Triple of:
     *  1. The key-condition expression (with placeholders for all literals)
     *  2. Expression attribute names map (#alias -> realName)
     *  3. Expression attribute values map (:placeholder -> AttributeValue)
     *
     * Supports all of:
     *  - Plain SQL style:  WHERE city = "Tampa" AND age > 25
     *  - Hash-prefixed:    WHERE #pk = 'abc' AND #sk > 100
     *  - Explicit params:  WHERE #pk = :pkVal  (where :pkVal resolved via `:pkVal = 'abc'` elsewhere in the DQL)
     */
    private data class ParsedWhere(
        val expression: String,
        val attrNames: Map<String, String>,
        val attrValues: Map<String, AttributeValue>,
    )

    /** DynamoDB expression keywords / functions that must NOT be treated as attribute names */
    private val DQL_KEYWORDS = setOf(
        "AND", "OR", "NOT", "BETWEEN", "IN", "NULL", "TRUE", "FALSE",
        "begins_with", "contains", "attribute_exists", "attribute_not_exists",
        "attribute_type", "size", "if_not_exists", "list_append"
    )

    private fun parseDqlWhereClause(dql: String): ParsedWhere {
        // Extract raw WHERE clause (stop before known trailing keywords)
        val rawWhere = Regex(
            """WHERE\s+(.+?)(?=\s+(?:LIMIT\b|USE\s+INDEX|ORDER\s+BY|ASC\b|DESC\b)|$|;)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(dql)?.groups?.get(1)?.value?.trim() ?: return ParsedWhere("", emptyMap(), emptyMap())

        val attrNames  = mutableMapOf<String, String>()
        val attrValues = mutableMapOf<String, AttributeValue>()
        var paramCounter = 0

        // ── Step 1: Replace string literals ('value' or "value") first so their
        //            content is never misinterpreted as attribute names or numbers.
        var expr = Regex("""(['"])((?:(?!\1).)*?)\1""").replace(rawWhere) { match ->
            val paramKey = ":_lit${paramCounter++}"
            attrValues[paramKey] = AttributeValue.builder().s(match.groupValues[2]).build()
            paramKey
        }

        // ── Step 2: Replace bare numeric literals with :_litN placeholders
        expr = Regex("""(?<![:\w])(\d+(?:\.\d+)?)(?!\w)""").replace(expr) { match ->
            val paramKey = ":_lit${paramCounter++}"
            attrValues[paramKey] = AttributeValue.builder().n(match.groupValues[1]).build()
            paramKey
        }

        // ── Step 3: Collect already-hash-prefixed attribute names (#attr)
        Regex("""#(\w+)""").findAll(expr).forEach {
            attrNames["#${it.groups[1]!!.value}"] = it.groups[1]!!.value
        }

        // ── Step 4: Auto-prefix bare attribute names (no # prefix) that are not
        //            DQL keywords, :params, or function names followed by '('
        //   Matches a word that:
        //    - is not preceded by # or :
        //    - is not followed by (   (i.e. not a function call)
        //    - is not a known keyword
        expr = Regex("""(?<![#:])(?<!\w)([A-Za-z_][A-Za-z0-9_]*)(?!\s*\()""").replace(expr) { match ->
            val word = match.value
            if (word.uppercase() in DQL_KEYWORDS.map { it.uppercase() }) {
                word  // leave keywords alone
            } else {
                val alias = "#$word"
                attrNames[alias] = word
                alias
            }
        }

        // ── Step 5: Resolve any explicit :param placeholders (e.g. WHERE #pk = :pkVal)
        Regex(""":((?!_lit)\w+)""").findAll(expr).forEach { match ->
            val paramKey = match.value
            if (!attrValues.containsKey(paramKey)) {
                val valueStr = Regex(
                    """${Regex.escape(paramKey)}\s*=\s*['"]?([^'",\s\)]+)['"]?""",
                    RegexOption.IGNORE_CASE
                ).find(dql)?.groups?.get(1)?.value?.trim()
                attrValues[paramKey] = when {
                    valueStr == null -> AttributeValue.builder().s("<missing: $paramKey>").build()
                    valueStr.toDoubleOrNull() != null -> AttributeValue.builder().n(valueStr).build()
                    else -> AttributeValue.builder().s(valueStr).build()
                }
            }
        }

        return ParsedWhere(expr, attrNames, attrValues)
    }

    private fun executeUpdate(_dql: String): ExecutionResult =
        ExecutionResult.Error("UPDATE translation not yet implemented.")

    // ── Native DynamoDB query execution ───────────────────────────────────────

    private suspend fun executeNativeQuery(
        client: software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient,
        startKey: Map<String, AttributeValue>?,
    ): ExecutionResult {
        val pageSize = extractPageSize()
        val isQuery  = nativeOperationCombo.selectedItem == "Query"
        val keyCondExpr  = nativeKeyCondField.text.trim().takeIf { it.isNotBlank() }
        val filterExpr   = nativeFilterField.text.trim().takeIf { it.isNotBlank() }
        val attrNamesRaw = nativeAttrNamesArea.text.trim()
        val attrValsRaw  = nativeAttrValuesArea.text.trim()

        val exprAttrNames: Map<String, String> = if (attrNamesRaw.isNotBlank())
            parseAttrNamesJson(attrNamesRaw) else emptyMap()
        val exprAttrValues: Map<String, AttributeValue> = if (attrValsRaw.isNotBlank())
            parseAttrValuesJson(attrValsRaw) else emptyMap()

        // Validate that all :placeholders in expressions have corresponding values
        val allExprs = listOfNotNull(keyCondExpr, filterExpr).joinToString(" ")
        val usedPlaceholders = Regex(""":\w+""").findAll(allExprs).map { it.value }.toSet()
        val missingPlaceholders = usedPlaceholders - exprAttrValues.keys
        if (missingPlaceholders.isNotEmpty()) {
            return ExecutionResult.Error(
                "Missing Attribute Values for placeholder(s): ${missingPlaceholders.joinToString(", ")}\n\n" +
                "Add them in the 'Attr Values' field, e.g.:\n" +
                missingPlaceholders.joinToString("\n") { "  \"$it\": \"your_value\"" } +
                "\n\nWrap all entries in { } like: {${missingPlaceholders.first()}: \"value\"}"
            )
        }

        return if (isQuery) {
            if (keyCondExpr == null)
                return ExecutionResult.Error("Key Condition Expression is required for Query operation.")
            val req = QueryRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .keyConditionExpression(keyCondExpr)
                .apply {
                    filterExpr?.let { filterExpression(it) }
                    if (exprAttrNames.isNotEmpty()) expressionAttributeNames(exprAttrNames)
                    if (exprAttrValues.isNotEmpty()) expressionAttributeValues(exprAttrValues)
                    startKey?.let { exclusiveStartKey(it) }
                }.build()
            val resp = client.query(req).await()
            ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        } else {
            val req = ScanRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .apply {
                    filterExpr?.let { filterExpression(it) }
                    if (exprAttrNames.isNotEmpty()) expressionAttributeNames(exprAttrNames)
                    if (exprAttrValues.isNotEmpty()) expressionAttributeValues(exprAttrValues)
                    startKey?.let { exclusiveStartKey(it) }
                }.build()
            val resp = client.scan(req).await()
            ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        }
    }

    /**
     * Parses a simplified JSON object like {"#name": "name", "#city": "city"}
     * into a Map<String, String> for Expression Attribute Names.
     */
    private fun parseAttrNamesJson(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cleaned = json.trim().removePrefix("{").removeSuffix("}")
        // Match "key": "value" pairs
        val pattern = Regex(""""(#?\w+)"\s*:\s*"([^"]+)"""")
        pattern.findAll(cleaned).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    /**
     * Parses a simplified JSON object like {":city": "Tampa", ":age": 25, ":flag": true}
     * into a Map<String, AttributeValue>. Supports strings, numbers, and booleans.
     * Also supports explicit DynamoDB JSON: {":v": {"S": "Tampa"}} or {":v": {"N": "25"}}.
     */
    private fun parseAttrValuesJson(json: String): Map<String, AttributeValue> {
        val result = mutableMapOf<String, AttributeValue>()
        val cleaned = json.trim().removePrefix("{").removeSuffix("}")
        // Try to match each "key": value pair where value can be string, number, boolean,
        // or nested object {"S": ...} / {"N": ...} / {"BOOL": ...}
        val stringPat  = Regex(""""(:\w+)"\s*:\s*"([^"]*)"""".trim())
        val numberPat  = Regex(""""(:\w+)"\s*:\s*(-?\d+(?:\.\d+)?)(?=[,\s}])""")
        val boolPat    = Regex(""""(:\w+)"\s*:\s*(true|false)(?=[,\s}])""", RegexOption.IGNORE_CASE)
        val dynStrPat  = Regex(""""(:\w+)"\s*:\s*\{\s*"S"\s*:\s*"([^"]*)"\s*}""")
        val dynNumPat  = Regex(""""(:\w+)"\s*:\s*\{\s*"N"\s*:\s*"([^"]*)"\s*}""")
        val dynBoolPat = Regex(""""(:\w+)"\s*:\s*\{\s*"BOOL"\s*:\s*(true|false)\s*}""", RegexOption.IGNORE_CASE)

        fun applyAll(pat: Regex, block: (MatchResult) -> AttributeValue) {
            pat.findAll(cleaned).forEach { result[it.groupValues[1]] = block(it) }
        }

        applyAll(dynStrPat)  { AttributeValue.builder().s(it.groupValues[2]).build() }
        applyAll(dynNumPat)  { AttributeValue.builder().n(it.groupValues[2]).build() }
        applyAll(dynBoolPat) { AttributeValue.builder().bool(it.groupValues[2].lowercase() == "true").build() }
        // Simple values — only add if not already resolved as DynamoDB JSON
        applyAll(stringPat)  { m -> result.getOrPut(m.groupValues[1]) { AttributeValue.builder().s(m.groupValues[2]).build() } ; AttributeValue.builder().s(m.groupValues[2]).build() }
        applyAll(numberPat)  { m -> result.getOrPut(m.groupValues[1]) { AttributeValue.builder().n(m.groupValues[2]).build() } ; AttributeValue.builder().n(m.groupValues[2]).build() }
        applyAll(boolPat)    { m -> result.getOrPut(m.groupValues[1]) { AttributeValue.builder().bool(m.groupValues[2].lowercase() == "true").build() } ; AttributeValue.builder().bool(m.groupValues[2].lowercase() == "true").build() }

        return result
    }


    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderTableResults(items: List<Map<String, AttributeValue>>) {
        if (items.isEmpty()) return
        val existingCols = (0 until tableModel.columnCount).map { tableModel.getColumnName(it) }.toSet()
        val allCols = if (existingCols.isEmpty()) {
            items.flatMap { it.keys }.distinct().sorted()
        } else {
            (existingCols + items.flatMap { it.keys }).distinct().sorted()
        }
        if (existingCols.isEmpty() || allCols.size > existingCols.size) {
            tableModel.setColumnIdentifiers(allCols.toTypedArray())
        }
        val cols = (0 until tableModel.columnCount).map { tableModel.getColumnName(it) }
        items.forEach { item ->
            tableModel.addRow(cols.map { col -> item[col]?.displayValue() ?: "" }.toTypedArray())
        }
        // Set up / refresh row sorter for filter
        rowSorter = TableRowSorter(tableModel)
        resultsTable.rowSorter = rowSorter
        applyFilter()
        resultCards.show(resultPanel, "table")
        fitColumnsToContent()
    }

    private fun renderRaw() {
        val sb = StringBuilder()
        rawItems.forEachIndexed { i, item ->
            sb.appendLine("// Item ${i + 1}")
            item.entries.sortedBy { it.key }.forEach { (k, v) -> sb.appendLine("  $k: ${v.displayValue()}") }
            sb.appendLine()
        }
        rawArea.text = sb.toString()
        rawArea.caretPosition = 0
        resultCards.show(resultPanel, "raw")
    }

    private fun clearResults() {
        tableModel.rowCount = 0; tableModel.columnCount = 0
        rawItems.clear(); rawArea.text = ""
        pageKeys.clear(); pageKeys.add(null)
        pageStartOffset.clear(); pageStartOffset.add(0)
        currentPage = 1; queryTotalLimit = null
        prevPageBtn.isEnabled = false; nextPageBtn.isEnabled = false
        pageLabel.text = "Page 1"
        statusLabel.text = "Ready"; detailPanel.clear()
        rowSorter = null; filterField.text = ""
    }

    private fun toggleRaw() {
        if (rawToggle.isSelected) renderRaw() else resultCards.show(resultPanel, "table")
    }

    private fun showError(msg: String) {
        rawArea.text = "ERROR:\n$msg"; resultCards.show(resultPanel, "raw")
    }


    private fun AttributeValue.displayValue(): String = when {
        s()    != null -> s()!!
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        ss().isNotEmpty() -> ss().joinToString(", ", "[", "]")
        ns().isNotEmpty() -> ns().joinToString(", ", "[", "]")
        l().isNotEmpty()  -> "[List:${l().size}]"
        m().isNotEmpty()  -> "{Map:${m().size}}"
        nul() == true     -> "null"
        b()   != null     -> "<Binary>"
        else -> "?"
    }

    private fun defaultQuery() = "SELECT *\nFROM $tableName\nLIMIT 50"

    fun showTableData() {
        rawToggle.isSelected = false
        resultCards.show(resultPanel, "table")
        if (rawItems.isEmpty() && tableModel.rowCount == 0 && runBtn.isEnabled) {
            execute()
        }
    }

    fun loadQuery(dql: String) {
        ApplicationManager.getApplication().runWriteAction { editor.document.setText(dql) }
    }

    fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
        scope.cancel()
    }

    private sealed class ExecutionResult {
        data class QueryResult(
            val items: List<Map<String, AttributeValue>>,
            val nextKey: Map<String, AttributeValue>?,
            val consumedRcu: Double?,
        ) : ExecutionResult()
        data class UpdateResult(val consumedWcu: Double?) : ExecutionResult()
        data class Error(val message: String) : ExecutionResult()
    }
}
