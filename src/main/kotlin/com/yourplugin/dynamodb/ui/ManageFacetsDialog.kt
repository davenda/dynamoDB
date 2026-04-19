package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.yourplugin.dynamodb.model.EntityFacet
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.services.SchemaMapService
import java.awt.*
import javax.swing.*

/**
 * Dialog to add/edit/remove Entity Facet mappings for a table.
 */
class ManageFacetsDialog(
    project: Project,
    private val tableName: String,
    private val service: SchemaMapService,
    private val schema: TableSchema,
) : DialogWrapper(project) {

    private val mapping = service.getMappingFor(tableName)
    private val listModel = DefaultListModel<EntityFacet>().also { m ->
        mapping.facets.forEach { m.addElement(it) }
    }
    private val facetList = JBList(listModel)

    init {
        title = "Manage Entity Facets — $tableName"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.preferredSize = Dimension(640, 400)

        // Left: list of existing facets
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(JLabel("Defined Entities:"), BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(facetList), BorderLayout.CENTER)

        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        btnPanel.add(JButton("Add…").apply { addActionListener { addFacet() } })
        btnPanel.add(JButton("Edit…").apply { addActionListener { editFacet() } })
        btnPanel.add(JButton("Remove").apply { addActionListener { removeFacet() } })
        leftPanel.add(btnPanel, BorderLayout.SOUTH)

        // Right: hint showing available attributes
        val attrText = buildString {
            appendLine("Table: $tableName")
            appendLine("PK: ${schema.partitionKey.name} (${schema.partitionKey.type})")
            schema.sortKey?.let { appendLine("SK: ${it.name} (${it.type})") }
            appendLine()
            appendLine("Declared attributes:")
            schema.attributeDefinitions.forEach { appendLine("  • ${it.name} (${it.type})") }
            appendLine()
            appendLine("GSIs:")
            schema.gsis.forEach { gsi ->
                appendLine("  • ${gsi.indexName}")
                appendLine("    PK: ${gsi.partitionKey.name}")
                gsi.sortKey?.let { appendLine("    SK: ${it.name}") }
            }
        }
        val rightPanel = JPanel(BorderLayout())
        rightPanel.add(JLabel("Schema Reference:"), BorderLayout.NORTH)
        rightPanel.add(JBScrollPane(JTextArea(attrText).apply {
            isEditable = false; font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }), BorderLayout.CENTER)

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        split.dividerLocation = 300
        panel.add(split, BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        // Persist all current facets
        val updated = mapping.copy(facets = (0 until listModel.size).map { listModel[it] })
        service.saveMappingFor(updated)
        super.doOKAction()
    }

    private fun addFacet() {
        val form = FacetFormDialog(null)
        if (form.showAndGet()) form.result()?.let { listModel.addElement(it) }
    }

    private fun editFacet() {
        val idx = facetList.selectedIndex.takeIf { it >= 0 } ?: return
        val existing = listModel[idx]
        val form = FacetFormDialog(existing)
        if (form.showAndGet()) form.result()?.let { listModel.set(idx, it) }
    }

    private fun removeFacet() {
        val idx = facetList.selectedIndex.takeIf { it >= 0 } ?: return
        listModel.remove(idx)
    }
}

/** Small inline form for a single EntityFacet */
private class FacetFormDialog(existing: EntityFacet?) : DialogWrapper(true) {

    private val nameField   = JTextField(existing?.entityName ?: "", 20)
    private val pkField     = JTextField(existing?.pkPrefix ?: "ENTITY#", 20)
    private val skField     = JTextField(existing?.skPrefix ?: "", 20)
    private val attrsField  = JTextField(existing?.visibleAttributes?.joinToString(", ") ?: "", 30)
    private val colorField  = JTextField(existing?.color ?: "#4A90D9", 10)

    init {
        title = if (existing == null) "Add Entity Facet" else "Edit Entity Facet"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); fill = GridBagConstraints.HORIZONTAL }

        fun row(label: String, field: JComponent, row: Int, hint: String? = null) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(field, gbc)
            if (hint != null) {
                gbc.gridx = 0; gbc.gridy = row + 1; gbc.gridwidth = 2
                panel.add(JLabel("<html><i>$hint</i></html>").apply {
                    foreground = Color.GRAY; font = font.deriveFont(11f)
                }, gbc)
                gbc.gridwidth = 1
            }
        }

        row("Entity name:", nameField, 0)
        row("PK prefix:", pkField, 1, "e.g. USER# — items whose PK starts with this belong to this entity")
        row("SK prefix (optional):", skField, 3, "e.g. PROFILE# — further narrows by sort key")
        row("Visible attributes:", attrsField, 5, "comma-separated; leave blank to show all")
        row("Badge color (hex):", colorField, 7)

        return panel
    }

    fun result(): EntityFacet? {
        val name = nameField.text.trim().ifEmpty { return null }
        return EntityFacet(
            entityName = name,
            pkPrefix = pkField.text.trim(),
            skPrefix = skField.text.trim().ifEmpty { null },
            visibleAttributes = attrsField.text.split(",")
                .map { it.trim() }.filter { it.isNotEmpty() },
            color = colorField.text.trim().ifEmpty { "#4A90D9" },
        )
    }
}
