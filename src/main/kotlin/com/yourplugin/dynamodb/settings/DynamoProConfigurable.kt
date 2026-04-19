package com.yourplugin.dynamodb.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Settings page shown under Settings → Tools → DynamoDB Pro.
 */
class DynamoProConfigurable : Configurable {

    private val settings = DynamoProSettings.getInstance()

    private val apiKeyField = JBPasswordField().apply { columns = 40 }
    private val modelField = JBTextField(40)
    private val aiEnabledBox = JCheckBox("Enable AI query generation")

    override fun getDisplayName() = "DynamoDB Pro"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        fun row(label: String, field: JComponent, row: Int, hint: String? = null) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0; gbc.gridwidth = 1
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(field, gbc)
            if (hint != null) {
                gbc.gridx = 0; gbc.gridy = row + 1; gbc.gridwidth = 2
                panel.add(
                    JLabel("<html><small><i>$hint</i></small></html>"),
                    gbc.apply { weightx = 0.0 }
                )
            }
        }

        row("Anthropic API key:", apiKeyField, 0,
            "Get your key at console.anthropic.com → API Keys")
        row("Claude model:", modelField, 2,
            "Default: claude-opus-4-6  (or claude-sonnet-4-6 for faster/cheaper)")

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2
        panel.add(aiEnabledBox, gbc)

        gbc.gridy = 5
        panel.add(
            JLabel("<html><small><i>When disabled, AI Query tab uses heuristic-only translation.</i></small></html>"),
            gbc
        )

        // Push everything to the top
        gbc.gridy = 6; gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc)

        reset()
        return panel
    }

    override fun isModified(): Boolean =
        String(apiKeyField.password) != settings.anthropicApiKey ||
            modelField.text != settings.anthropicModel ||
            aiEnabledBox.isSelected != settings.aiEnabled

    override fun apply() {
        settings.anthropicApiKey = String(apiKeyField.password)
        settings.anthropicModel = modelField.text.trim().ifEmpty { "claude-opus-4-6" }
        settings.aiEnabled = aiEnabledBox.isSelected
    }

    override fun reset() {
        apiKeyField.text = settings.anthropicApiKey
        modelField.text = settings.anthropicModel
        aiEnabledBox.isSelected = settings.aiEnabled
    }
}
