package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog for viewing and editing a single DynamoDB item.
 * Shows all attributes in a two-column table (name | value).
 * User can add, edit, or remove attributes before saving.
 */
class ItemEditorDialog(
    project: Project,
    private val item: Map<String, AttributeValue>,
    private val pkKeys: Set<String>,         // partition + sort key names — not editable
    private val onSave: (Map<String, AttributeValue>) -> Unit,
    private val onDelete: () -> Unit,
) : DialogWrapper(project) {

    private val tableModel = object : DefaultTableModel(arrayOf("Attribute", "Value"), 0) {
        override fun isCellEditable(row: Int, col: Int): Boolean {
            if (col == 0) return false          // attribute name column not editable
            val attr = getValueAt(row, 0) as String
            return attr !in pkKeys              // key attributes not editable
        }
    }
    private val table = JBTable(tableModel).apply {
        rowHeight = 24
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    }

    private val deleteBtn = JButton("Delete Item").apply {
        foreground = java.awt.Color(200, 50, 50)
        addActionListener { confirmDelete() }
    }

    init {
        title = "Edit Item"
        setSize(600, 450)
        init()
        populateTable()
    }

    private fun populateTable() {
        tableModel.rowCount = 0
        item.entries.sortedWith(compareBy({ it.key !in pkKeys }, { it.key }))
            .forEach { (k, v) -> tableModel.addRow(arrayOf(k, v.toEditString())) }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(4, 4))
        panel.preferredSize = Dimension(580, 400)

        val hint = JLabel("<html><small><i>Key attributes (${pkKeys.joinToString(", ")}) are read-only. Edit values in the Value column.</i></small></html>")
        hint.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val addRow = JPanel(FlowLayout(FlowLayout.LEFT))
        val newAttrField = JTextField(15).apply { toolTipText = "New attribute name" }
        val newValField  = JTextField(20).apply { toolTipText = "Value (string)" }
        val addBtn = JButton("+ Add").apply {
            addActionListener {
                val name = newAttrField.text.trim()
                val value = newValField.text.trim()
                if (name.isNotEmpty()) {
                    tableModel.addRow(arrayOf(name, value))
                    newAttrField.text = ""
                    newValField.text = ""
                }
            }
        }
        val removeBtn = JButton("− Remove").apply {
            addActionListener {
                val row = table.selectedRow
                if (row >= 0) {
                    val attr = tableModel.getValueAt(row, 0) as String
                    if (attr in pkKeys) {
                        Messages.showWarningDialog("Cannot remove key attribute '$attr'.", "Warning")
                        return@addActionListener
                    }
                    tableModel.removeRow(row)
                }
            }
        }
        addRow.add(JLabel("Attr:"))
        addRow.add(newAttrField)
        addRow.add(JLabel("Value:"))
        addRow.add(newValField)
        addRow.add(addBtn)
        addRow.add(removeBtn)

        panel.add(hint, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(addRow, BorderLayout.SOUTH)
        return panel
    }

    override fun createSouthPanel(): JComponent {
        val base = super.createSouthPanel()
        val wrapper = JPanel(BorderLayout())
        wrapper.add(deleteBtn, BorderLayout.WEST)
        wrapper.add(base, BorderLayout.EAST)
        return wrapper
    }

    override fun doOKAction() {
        val updated = mutableMapOf<String, AttributeValue>()
        for (row in 0 until tableModel.rowCount) {
            val name  = tableModel.getValueAt(row, 0) as String
            val value = tableModel.getValueAt(row, 1) as String
            updated[name] = parseValue(value, item[name])
        }
        onSave(updated)
        super.doOKAction()
    }

    private fun confirmDelete() {
        val result = Messages.showYesNoDialog(
            "Delete this item permanently from DynamoDB?",
            "Delete Item",
            Messages.getWarningIcon()
        )
        if (result == Messages.YES) {
            onDelete()
            close(CANCEL_EXIT_CODE)
        }
    }

    // ── Value helpers ─────────────────────────────────────────────────────────

    private fun AttributeValue.toEditString(): String = when {
        s()    != null -> s()!!
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        nul()  == true -> "null"
        ss().isNotEmpty() -> ss().joinToString(",")
        ns().isNotEmpty() -> ns().joinToString(",")
        l().isNotEmpty()  -> "[list:${l().size}]"
        m().isNotEmpty()  -> "{map:${m().size}}"
        b()    != null -> "<binary>"
        else -> ""
    }

    private fun parseValue(str: String, original: AttributeValue?): AttributeValue {
        // Preserve original type when possible
        return when {
            original?.n() != null   -> AttributeValue.builder().n(str).build()
            original?.bool() != null -> AttributeValue.builder().bool(str.toBooleanStrictOrNull() ?: false).build()
            str == "null"           -> AttributeValue.builder().nul(true).build()
            str.toDoubleOrNull() != null && original?.s() == null ->
                AttributeValue.builder().n(str).build()
            else                    -> AttributeValue.builder().s(str).build()
        }
    }
}
