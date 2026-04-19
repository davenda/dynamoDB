package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.yourplugin.dynamodb.model.EntityFacet
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.SchemaMapService
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Single-Table Design Entity Facets view.
 *
 * Instead of a flat dump of all items, this panel:
 *  1. Shows one tab per EntityFacet defined in SchemaMapService
 *  2. Each tab renders ONLY the attributes relevant to that entity
 *  3. Fetches items filtered by the facet's PK/SK prefix conditions
 *
 * Also provides a "Manage Facets" button to add/edit/remove entity mappings.
 */
class EntityFacetsPanel(
    private val project: Project,
    private val connectionName: String,
    private val tableName: String,
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val schemaMapService = project.getService(SchemaMapService::class.java)
    private val registry get() = com.intellij.openapi.application.ApplicationManager
        .getApplication().getService(DynamoConnectionRegistry::class.java)

    private val facetTabs = JTabbedPane()
    private var schema: TableSchema? = null

    init {
        add(buildToolbar(), BorderLayout.NORTH)
        add(facetTabs, BorderLayout.CENTER)
        loadAndRender()
    }

    private fun buildToolbar(): JToolBar {
        return JToolBar().apply {
            isFloatable = false
            add(JButton("Manage Facets…").apply {
                addActionListener { showManageFacetsDialog() }
            })
            add(JButton("↺ Refresh").apply {
                addActionListener { loadAndRender() }
            })
            add(JLabel("  Single-Table Design — Entity Pivot View").apply {
                foreground = Color(100, 100, 100)
                font = font.deriveFont(Font.ITALIC)
            })
        }
    }

    private fun loadAndRender() {
        scope.launch {
            val client = registry.clientFor(connectionName)
            val s = runCatching { TableSchemaService.describe(client, tableName) }.getOrElse {
                withContext(Dispatchers.Swing) {
                    facetTabs.removeAll()
                    facetTabs.addTab("Error", JLabel("Failed to load schema: ${it.message}"))
                }
                return@launch
            }
            schema = s

            val mapping = schemaMapService.getMappingFor(tableName)

            withContext(Dispatchers.Swing) {
                facetTabs.removeAll()

                if (mapping.facets.isEmpty()) {
                    facetTabs.addTab("No Facets Defined", buildNoFacetsHint(s))
                    return@withContext
                }

                // Always add a raw "All Items" tab
                facetTabs.addTab("All Items", buildRawTab(client, s))

                // One tab per entity facet
                mapping.facets.forEach { facet ->
                    val tab = buildFacetTab(client, s, facet)
                    facetTabs.addTab(facet.entityName, tab)

                    // Color the tab label to match the facet badge color
                    val idx = facetTabs.tabCount - 1
                    facetTabs.setBackgroundAt(idx, Color.decode(facet.color).let {
                        Color(it.red, it.green, it.blue, 40)
                    })
                }
            }
        }
    }

    // ── Per-facet tab ─────────────────────────────────────────────────────────

    private fun buildFacetTab(
        client: DynamoDbAsyncClient,
        schema: TableSchema,
        facet: EntityFacet,
    ): JComponent {
        val model = DefaultTableModel(0, 0)
        val table = JBTable(model).apply { autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS }
        val panel = JPanel(BorderLayout())
        val statusLabel = JLabel("Loading ${facet.entityName} items…")

        panel.add(statusLabel, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        scope.launch {
            val pkAttr = schema.partitionKey.name
            val skAttr = schema.sortKey?.name

            // Build a Query using begins_with on the PK
            val req = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(
                    buildKeyCondition(pkAttr, skAttr, facet)
                )
                .expressionAttributeNames(buildNameMap(pkAttr, skAttr))
                .expressionAttributeValues(buildValueMap(facet))
                .limit(200)
                .build()

            val items = try {
                client.query(req).await().items()
            } catch (e: Exception) {
                withContext(Dispatchers.Swing) {
                    statusLabel.text = "Error: ${e.message}"
                }
                return@launch
            }

            // Determine columns: facet.visibleAttributes if set, else union of all attr keys
            val columns = if (facet.visibleAttributes.isNotEmpty()) {
                facet.visibleAttributes
            } else {
                items.flatMap { it.keys }.distinct().sorted()
            }

            withContext(Dispatchers.Swing) {
                model.setColumnIdentifiers(columns.toTypedArray())
                items.forEach { item ->
                    model.addRow(columns.map { col ->
                        item[col]?.displayValue() ?: ""
                    }.toTypedArray())
                }
                statusLabel.text = "${facet.entityName} — ${items.size} items" +
                    if (items.size == 200) " (limit 200, scroll for more)" else ""

                // Badge the PK/SK columns
                table.columnModel.getColumn(0).cellRenderer = BadgeRenderer(facet.color)
            }
        }

        return panel
    }

    private fun buildRawTab(client: DynamoDbAsyncClient, schema: TableSchema): JComponent {
        val model = DefaultTableModel(0, 0)
        val table = JBTable(model).apply { autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS }
        val panel = JPanel(BorderLayout())
        val statusLabel = JLabel("Scanning first 100 items…")
        panel.add(statusLabel, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        scope.launch {
            val req = ScanRequest.builder().tableName(tableName).limit(100).build()
            val items = try {
                client.scan(req).await().items()
            } catch (e: Exception) {
                withContext(Dispatchers.Swing) { statusLabel.text = "Error: ${e.message}" }
                return@launch
            }
            val columns = items.flatMap { it.keys }.distinct().sorted()
            withContext(Dispatchers.Swing) {
                model.setColumnIdentifiers(columns.toTypedArray())
                items.forEach { item ->
                    model.addRow(columns.map { col -> item[col]?.displayValue() ?: "" }.toTypedArray())
                }
                statusLabel.text = "All Items — ${items.size} sampled (Scan, unfiltered)"
            }
        }

        return panel
    }

    private fun buildNoFacetsHint(schema: TableSchema): JComponent {
        val msg = JTextArea("""
            No Entity Facets defined for '${schema.tableName}'.

            Click "Manage Facets…" to define entities.

            Example:
              Entity: User
              PK prefix: USER#
              SK prefix: PROFILE#
              Visible attrs: userId, email, createdAt

            Each entity gets its own tab with only its relevant columns.
            This is the Single-Table Design pivot view.
        """.trimIndent()).apply {
            isEditable = false
            background = null
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }
        return JBScrollPane(msg)
    }

    // ── Facet management dialog ───────────────────────────────────────────────

    private fun showManageFacetsDialog() {
        val s = schema ?: return
        val dialog = ManageFacetsDialog(project, tableName, schemaMapService, s)
        if (dialog.showAndGet()) loadAndRender()
    }

    // ── Key condition builders ────────────────────────────────────────────────

    private fun buildKeyCondition(pkAttr: String, skAttr: String?, facet: EntityFacet): String {
        val pkCond = "#pk = :pkPrefix"
        val skCond = if (skAttr != null && facet.skPrefix != null)
            " AND begins_with(#sk, :skPrefix)"
        else ""
        return pkCond + skCond
    }

    private fun buildNameMap(pkAttr: String, skAttr: String?): Map<String, String> =
        buildMap {
            put("#pk", pkAttr)
            skAttr?.let { put("#sk", it) }
        }

    private fun buildValueMap(facet: EntityFacet): Map<String, AttributeValue> =
        buildMap {
            put(":pkPrefix", AttributeValue.builder().s(facet.pkPrefix).build())
            facet.skPrefix?.let {
                put(":skPrefix", AttributeValue.builder().s(it).build())
            }
        }

    private fun AttributeValue.displayValue(): String = when {
        s()  != null -> s()
        n()  != null -> n()
        bool() != null -> bool().toString()
        ss().isNotEmpty() -> ss().joinToString(", ", "[", "]")
        ns().isNotEmpty() -> ns().joinToString(", ", "[", "]")
        l().isNotEmpty()  -> "[List: ${l().size} items]"
        m().isNotEmpty()  -> "{Map: ${m().size} keys}"
        nul() == true     -> "null"
        b()  != null -> "<Binary>"
        else -> "?"
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private inner class BadgeRenderer(private val hexColor: String) : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, selected: Boolean,
            focused: Boolean, row: Int, col: Int,
        ): Component {
            super.getTableCellRendererComponent(table, value, selected, focused, row, col)
            if (!selected) background = Color.decode(hexColor).let {
                Color(it.red, it.green, it.blue, 30)
            }
            return this
        }
    }

    fun dispose() = scope.cancel()
}
