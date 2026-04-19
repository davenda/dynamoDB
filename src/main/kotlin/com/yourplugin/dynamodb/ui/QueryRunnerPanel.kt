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

    // ── Results ───────────────────────────────────────────────────────────────
    private val tableModel = object : DefaultTableModel(0, 0) {
        override fun isCellEditable(r: Int, c: Int) = false
    }
    private val resultsTable = JBTable(tableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_OFF
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
        return JToolBar().apply {
            isFloatable = false
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            add(runBtn)
            add(toolbarSolidDivider())
            add(clearBtn)
            add(rawToggle)
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

    private fun buildMainArea(): JComponent {
        val resultsPanelWithFooter = JPanel(BorderLayout()).apply {
            add(resultPanel, BorderLayout.CENTER)
            add(buildPaginationBar(), BorderLayout.SOUTH)
        }
        val rightSplit = JBSplitter(true, 0.65f).apply {
            firstComponent  = resultsPanelWithFooter
            secondComponent = detailPanel
        }
        return JBSplitter(true, 0.3f).apply {
            firstComponent  = editor.component
            secondComponent = rightSplit
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
        val dql = ApplicationManager.getApplication().runReadAction<String> { editor.document.text }.trim()
        queryTotalLimit = extractTotalLimit(dql)
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
        if (dql.isBlank()) { statusLabel.text = "Query is empty"; return }

        runBtn.isEnabled = false
        prevPageBtn.isEnabled = false
        nextPageBtn.isEnabled = false
        statusLabel.text = "Executing…"

        scope.launch {
            try {
                val result: ExecutionResult = runCatching {
                    val client = registry.clientFor(connectionName)
                    if (dql.trimStart().uppercase().startsWith("UPDATE")) {
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
        // Always use the combo-box page size for the DynamoDB request.
        // The LIMIT in the DQL is applied as a post-fetch total cap (see loadCurrentPage).
        val pageSize = extractPageSize()
        val isScan = !dql.contains("WHERE", ignoreCase = true)
        return if (isScan) {
            val req = ScanRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .apply { startKey?.let { exclusiveStartKey(it) } }
                .build()
            val resp = client.scan(req).await()
            ExecutionResult.QueryResult(
                items = resp.items(),
                nextKey = resp.lastEvaluatedKey().takeIf { it.isNotEmpty() },
                consumedRcu = resp.consumedCapacity()?.capacityUnits(),
            )
        } else {
            val keyConditionExpr = extractKeyConditionExpression(dql)
            val (exprAttrNames, exprAttrValues) = extractAttributeReferencesAndValues(dql)
            if (keyConditionExpr.isNullOrBlank())
                return ExecutionResult.Error("WHERE clause must specify a key condition")
            val indexName = Regex("""USE\s+INDEX\s*\((\w+)\)""", RegexOption.IGNORE_CASE)
                .find(dql)?.groupValues?.get(1)
            val req = QueryRequest.builder()
                .tableName(tableName).limit(pageSize)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .keyConditionExpression(keyConditionExpr)
                .apply {
                    if (exprAttrNames.isNotEmpty()) expressionAttributeNames(exprAttrNames)
                    if (exprAttrValues.isNotEmpty()) expressionAttributeValues(exprAttrValues)
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
        }
    }

    private fun extractKeyConditionExpression(dql: String): String? =
        Regex("""WHERE\s+(.+?)(?:LIMIT|USE|$|;)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(dql)?.groups?.get(1)?.value?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractAttributeReferencesAndValues(dql: String): Pair<Map<String, String>, Map<String, AttributeValue>> {
        val attrNames  = mutableMapOf<String, String>()
        val attrValues = mutableMapOf<String, AttributeValue>()
        Regex("""#(\w+)""").findAll(dql).forEach { attrNames["#${it.groups[1]!!.value}"] = it.groups[1]!!.value }
        val whereClause = extractKeyConditionExpression(dql) ?: ""
        Regex(""":(\w+)""").findAll(whereClause).forEach { match ->
            val paramName = match.groups[1]!!.value
            val valueKey  = ":$paramName"
            val valueStr  = Regex("""$valueKey\s*=\s*['"]?([^'",\s]+)['"]?""", RegexOption.IGNORE_CASE)
                .find(dql)?.groups?.get(1)?.value?.trim()
            attrValues[valueKey] = if (valueStr != null) {
                if (valueStr.toDoubleOrNull() != null) AttributeValue.builder().n(valueStr).build()
                else AttributeValue.builder().s(valueStr).build()
            } else AttributeValue.builder().s("<provide value>").build()
        }
        return Pair(attrNames, attrValues)
    }

    private fun executeUpdate(_dql: String): ExecutionResult =
        ExecutionResult.Error("UPDATE translation not yet implemented.")


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
