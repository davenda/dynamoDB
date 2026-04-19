package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class EditConnectionDialog(
    project: Project,
    private val registry: DynamoConnectionRegistry,
    private val existing: DynamoConnectionRegistry.ConnectionConfig,
) : DialogWrapper(project) {

    private val nameField     = JTextField(existing.name, 25)
    private val regionField   = JTextField(existing.region, 25)
    private val profileField  = JTextField(existing.profileName ?: "", 25)
    private val endpointField = JTextField(existing.endpointOverride ?: "", 25)
    private val credTypeBox   = JComboBox(DynamoConnectionRegistry.CredentialType.entries.toTypedArray()).apply {
        selectedItem = existing.credentialType
    }

    init {
        title = "Edit Connection"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4); fill = GridBagConstraints.HORIZONTAL
        }
        fun row(label: String, field: JComponent, row: Int) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(field, gbc)
        }
        row("Connection name:", nameField, 0)
        row("Region:", regionField, 1)
        row("Credential type:", credTypeBox, 2)
        row("AWS profile (optional):", profileField, 3)
        row("Endpoint override (optional):", endpointField, 4)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("Connection name is required", nameField)
        if (regionField.text.isBlank()) return ValidationInfo("Region is required", regionField)
        return null
    }

    override fun doOKAction() {
        // Remove old entry (handles rename)
        registry.removeConnection(existing.name)
        registry.addConnection(DynamoConnectionRegistry.ConnectionConfig(
            name             = nameField.text.trim(),
            region           = regionField.text.trim(),
            profileName      = profileField.text.trim().ifEmpty { null },
            endpointOverride = endpointField.text.trim().ifEmpty { null },
            credentialType   = credTypeBox.selectedItem as DynamoConnectionRegistry.CredentialType,
        ))
        super.doOKAction()
    }
}
