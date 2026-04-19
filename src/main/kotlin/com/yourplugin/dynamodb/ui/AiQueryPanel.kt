package com.yourplugin.dynamodb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.yourplugin.dynamodb.ai.AiQueryTranslator
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * AI Access Pattern Generator panel.
 *
 * The user types a natural-language request (e.g. "show all orders for customer X
 * placed in the last 30 days") and the panel generates a DQL query using:
 *  - Heuristic translator (always available)
 *  - IntelliJ AI Assistant SDK (when com.intellij.ml.llm is present — Phase 2)
 *
 * The generated DQL can be copied or sent directly to the Query Runner.
 */
class AiQueryPanel(
    private val project: Project,
    private val connectionName: String,
    private val tableName: String,
    private val onSendToRunner: (String) -> Unit,   // callback → QueryRunnerPanel editor
) : JPanel(BorderLayout(8, 8)) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val registry get() = ApplicationManager.getApplication()
        .getService(DynamoConnectionRegistry::class.java)

    private var schema: TableSchema? = null

    // ── Input area ────────────────────────────────────────────────────────────
    private val inputArea = JTextArea(3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }
    private val translateBtn = JButton("Generate DQL ✨").apply {
        font = font.deriveFont(Font.BOLD)
        addActionListener { translate() }
    }
    private val examplesBox = JComboBox(arrayOf(
        "Try an example…",
        "Show all orders for customer X placed in the last 30 days",
        "Get all users with email = user@example.com",
        "Find all products in category ELECTRONICS under $50",
        "List the 10 most recent sessions for device abc123",
        "Fetch all failed payments from last week",
    )).apply {
        addActionListener {
            val sel = selectedItem?.toString() ?: return@addActionListener
            if (sel != "Try an example…") inputArea.text = sel
        }
    }

    // ── Output area ───────────────────────────────────────────────────────────
    private val dqlOutput = JTextArea().apply {
        isEditable = true   // user can tweak before sending
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }
    private val explanationLabel = JLabel("<html><i>Enter a request above and click Generate.</i></html>").apply {
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    }
    private val confidenceBadge = JLabel("").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
    }
    private val sendBtn = JButton("Send to Query Runner →").apply {
        isEnabled = false
        addActionListener { onSendToRunner(dqlOutput.text) }
    }
    private val copyBtn = JButton("Copy DQL").apply {
        isEnabled = false
        addActionListener {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(dqlOutput.text), null)
        }
    }

    // ── Schema hint panel ─────────────────────────────────────────────────────
    private val schemaHint = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        background = UIManager.getColor("Panel.background")
    }

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        add(buildInputPanel(), BorderLayout.NORTH)
        add(buildOutputPanel(), BorderLayout.CENTER)
        add(buildSchemaPanel(), BorderLayout.EAST)
        loadSchema()
    }

    // ── Layout builders ───────────────────────────────────────────────────────

    private fun buildInputPanel(): JPanel {
        val panel = JPanel(BorderLayout(4, 4))
        panel.border = TitledBorder("Describe your access pattern in plain English")

        val topRow = JPanel(BorderLayout(4, 0))
        topRow.add(examplesBox, BorderLayout.CENTER)
        topRow.add(translateBtn, BorderLayout.EAST)

        panel.add(topRow, BorderLayout.NORTH)
        panel.add(JBScrollPane(inputArea), BorderLayout.CENTER)
        return panel
    }

    private fun buildOutputPanel(): JPanel {
        val panel = JPanel(BorderLayout(4, 4))
        panel.border = TitledBorder("Generated DQL")

        val metaRow = JPanel(BorderLayout())
        metaRow.add(explanationLabel, BorderLayout.CENTER)
        metaRow.add(confidenceBadge, BorderLayout.EAST)

        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT))
        btnRow.add(sendBtn)
        btnRow.add(copyBtn)

        panel.add(metaRow, BorderLayout.NORTH)
        panel.add(JBScrollPane(dqlOutput), BorderLayout.CENTER)
        panel.add(btnRow, BorderLayout.SOUTH)
        return panel
    }

    private fun buildSchemaPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Schema Reference")
        panel.preferredSize = Dimension(200, 0)
        panel.add(JBScrollPane(schemaHint), BorderLayout.CENTER)
        return panel
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private fun loadSchema() {
        scope.launch {
            val s = runCatching {
                TableSchemaService.describe(registry.clientFor(connectionName), tableName)
            }.getOrNull() ?: return@launch
            schema = s
            withContext(Dispatchers.Swing) { populateSchemaHint(s) }
        }
    }

    private fun translate() {
        val request = inputArea.text.trim()
        if (request.isBlank()) return
        val s = schema ?: run {
            explanationLabel.text = "<html><i>Schema still loading, please wait…</i></html>"
            return
        }

        translateBtn.isEnabled = false
        explanationLabel.text = "<html><i>Translating…</i></html>"

        scope.launch {
            val result = runCatching {
                AiQueryTranslator.translate(request, s)
            }.getOrElse { ex ->
                AiQueryTranslator.TranslationResult(
                    dql = "-- Error during translation: ${ex.message}",
                    explanation = ex.message ?: "Unknown error",
                    usedIndex = null,
                    confidence = AiQueryTranslator.Confidence.LOW,
                )
            }

            withContext(Dispatchers.Swing) {
                translateBtn.isEnabled = true
                dqlOutput.text = result.dql
                dqlOutput.caretPosition = 0

                explanationLabel.text = "<html>${result.explanation.replace("\n", "<br>")}</html>"

                val (badgeText, badgeColor) = when (result.confidence) {
                    AiQueryTranslator.Confidence.HIGH   -> "HIGH confidence"   to Color(40, 167, 69)
                    AiQueryTranslator.Confidence.MEDIUM -> "MEDIUM confidence" to Color(255, 153, 0)
                    AiQueryTranslator.Confidence.LOW    -> "LOW confidence"    to Color(220, 53, 69)
                }
                confidenceBadge.text = badgeText
                confidenceBadge.foreground = badgeColor

                sendBtn.isEnabled = true
                copyBtn.isEnabled = true
            }
        }
    }

    private fun populateSchemaHint(s: TableSchema) {
        schemaHint.text = buildString {
            appendLine("Table: ${s.tableName}")
            appendLine("PK: ${s.partitionKey.name}")
            s.sortKey?.let { appendLine("SK: ${it.name}") }
            appendLine()
            if (s.gsis.isNotEmpty()) {
                appendLine("GSIs:")
                s.gsis.forEach { gsi ->
                    appendLine("  ${gsi.indexName}")
                    appendLine("    PK: ${gsi.partitionKey.name}")
                    gsi.sortKey?.let { appendLine("    SK: ${it.name}") }
                }
            } else {
                appendLine("(no GSIs)")
            }
        }
        schemaHint.caretPosition = 0
    }

    fun dispose() = scope.cancel()
}
