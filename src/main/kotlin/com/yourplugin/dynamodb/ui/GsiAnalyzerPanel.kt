package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.yourplugin.dynamodb.model.GsiInfo
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import software.amazon.awssdk.services.dynamodb.model.IndexStatus
import software.amazon.awssdk.services.dynamodb.model.ProjectionType
import java.awt.BorderLayout
import java.awt.Color
import java.text.NumberFormat
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Milestone 1 deliverable: GSI Analyzer Panel.
 *
 * Shows for a selected table:
 *  - Table summary (item count, size, billing mode)
 *  - Each GSI as a row: name, PK/SK, projection type, item count, size, throughput, status
 *
 * Provides immediate value that Anton's plugin buries in nested menus.
 */
class GsiAnalyzerPanel(
    private val project: Project,
    private val connectionName: String,
    private val tableName: String,
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val numFmt = NumberFormat.getNumberInstance()

    private val headerLabel = JLabel("Loading $tableName…")
    private val tableModel = buildTableModel()
    private val gsiTable = JBTable(tableModel).apply {
        setDefaultRenderer(Any::class.java, StatusAwareRenderer())
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        rowHeight = 24
    }

    init {
        add(headerLabel, BorderLayout.NORTH)
        add(JBScrollPane(gsiTable), BorderLayout.CENTER)
        loadAsync()
    }

    private fun buildTableModel() = object : DefaultTableModel(
        arrayOf("Index Name", "PK", "SK", "Projection", "Non-Key Attrs",
                "Item Count", "Size (MB)", "RCU", "WCU", "Status"),
        0
    ) {
        override fun isCellEditable(row: Int, col: Int) = false
    }

    private fun loadAsync() {
        scope.launch {
            val registry = com.intellij.openapi.application.ApplicationManager
                .getApplication().getService(DynamoConnectionRegistry::class.java)
            val client = registry.clientFor(connectionName)

            val schema = runCatching { TableSchemaService.describe(client, tableName) }
                .getOrElse { ex ->
                    withContext(Dispatchers.Swing) {
                        headerLabel.text = "Error: ${ex.message}"
                    }
                    return@launch
                }

            withContext(Dispatchers.Swing) {
                populateHeader(schema)
                populateGsiRows(schema)
            }
        }
    }

    private fun populateHeader(schema: TableSchema) {
        val billing = if (schema.billingMode.name == "PAY_PER_REQUEST") "On-Demand" else
            "Provisioned (${schema.readCapacityUnits}R / ${schema.writeCapacityUnits}W)"
        headerLabel.text = buildString {
            append("<html><b>${schema.tableName}</b>  ·  ")
            append("${numFmt.format(schema.itemCount)} items  ·  ")
            append("${schema.tableSizeBytes.toMb()} MB  ·  $billing")
            if (schema.gsis.isEmpty()) append("  ·  <i>No GSIs</i>")
            append("</html>")
        }
    }

    private fun populateGsiRows(schema: TableSchema) {
        tableModel.rowCount = 0

        // Always add the base table as the first row for comparison
        tableModel.addRow(arrayOf(
            "[Base Table]",
            "${schema.partitionKey.name} (${schema.partitionKey.type})",
            schema.sortKey?.let { "${it.name} (${it.type})" } ?: "—",
            "ALL",
            "—",
            numFmt.format(schema.itemCount),
            schema.tableSizeBytes.toMb(),
            schema.readCapacityUnits?.toString() ?: "On-Demand",
            schema.writeCapacityUnits?.toString() ?: "On-Demand",
            schema.status.name,
        ))

        schema.gsis.forEach { gsi -> tableModel.addRow(gsi.toRow()) }
        schema.lsis.forEach { lsi ->
            tableModel.addRow(arrayOf(
                lsi.indexName + " (LSI)",
                "${schema.partitionKey.name} (${schema.partitionKey.type})",
                "${lsi.sortKey.name} (${lsi.sortKey.type})",
                lsi.projection.name,
                lsi.nonKeyAttributes.joinToString(", ").ifEmpty { "—" },
                numFmt.format(lsi.itemCount),
                lsi.sizeBytes.toMb(),
                "Shared", "Shared",
                "ACTIVE",
            ))
        }
    }

    private fun GsiInfo.toRow(): Array<Any> = arrayOf(
        indexName,
        "${partitionKey.name} (${partitionKey.type})",
        sortKey?.let { "${it.name} (${it.type})" } ?: "—",
        projection.name,
        if (projection == ProjectionType.INCLUDE)
            nonKeyAttributes.joinToString(", ")
        else "—",
        numFmt.format(itemCount),
        sizeBytes.toMb(),
        readCapacityUnits?.toString() ?: "On-Demand",
        writeCapacityUnits?.toString() ?: "On-Demand",
        status.name,
    )

    private fun Long.toMb(): String = "%.2f".format(this / 1_048_576.0)

    /** Color-codes rows by index status */
    private inner class StatusAwareRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, selected: Boolean,
            focused: Boolean, row: Int, col: Int
        ): java.awt.Component {
            super.getTableCellRendererComponent(table, value, selected, focused, row, col)
            val statusCol = table.columnCount - 1
            val status = table.getValueAt(row, statusCol)?.toString() ?: ""
            if (!selected) {
                background = when {
                    status == "CREATING" || status == "UPDATING" -> Color(255, 250, 220)
                    status.contains("DELETING") -> Color(255, 235, 235)
                    row == 0 -> Color(235, 245, 255) // base table highlight
                    else -> table.background
                }
            }
            return this
        }
    }

    fun dispose() = scope.cancel()
}
