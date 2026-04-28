package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class ConnectDialog(
    project: Project,
    private val registry: DynamoConnectionRegistry,
) : DialogWrapper(project) {

    private val nameField      = JTextField("My DynamoDB", 25)
    private val regionField    = JTextField("us-east-1", 25)
    private val profileField   = JTextField("", 25)
    private val endpointField  = JTextField("", 25)  // blank = AWS, or http://localhost:8000 for local
    private val credTypeBox    = JComboBox(DynamoConnectionRegistry.CredentialType.entries.toTypedArray())
    private val proxyHostField = JTextField("", 25)
    private val proxyPortField = JTextField("", 6)

    init {
        title = "Connect to DynamoDB"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
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
        row("Proxy host (optional):", proxyHostField, 5)
        row("Proxy port (optional):", proxyPortField, 6)

        val hint = JLabel("<html><i>Leave endpoint blank for AWS. Use http://localhost:8000 for DynamoDB Local.</i></html>")
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2
        panel.add(hint, gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("Connection name is required", nameField)
        if (regionField.text.isBlank()) return ValidationInfo("Region is required", regionField)
        return null
    }

    override fun doOKAction() {
        registry.addConnection(
            DynamoConnectionRegistry.ConnectionConfig(
                name             = nameField.text.trim(),
                region           = regionField.text.trim(),
                profileName      = profileField.text.trim().ifEmpty { null },
                endpointOverride = endpointField.text.trim().ifEmpty { null },
                credentialType   = credTypeBox.selectedItem as DynamoConnectionRegistry.CredentialType,
                proxyHost        = proxyHostField.text.trim().ifEmpty { null },
                proxyPort        = proxyPortField.text.trim().toIntOrNull(),
            )
        )
        super.doOKAction()
    }
}
