package com.yourplugin.dynamodb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import java.awt.*
import javax.swing.*
import javax.swing.border.MatteBorder

/**
 * Editor-tab wrapper: titleBar + QueryRunnerPanel (with its own row-toolbar) + statusBar.
 */
class DynamoTableViewPanel(
    private val project: Project,
    private val connectionName: String,
    private val tableName: String,
) : JPanel(BorderLayout()) {

    private val registry
        get() = ApplicationManager.getApplication().getService(DynamoConnectionRegistry::class.java)

    // ── Status bar ────────────────────────────────────────────────────────────
    private val statusLabel = JLabel().apply {
        font       = font.deriveFont(Font.PLAIN, 10f)
        foreground = DColors.fg3
        border     = BorderFactory.createEmptyBorder(0, 10, 0, 0)
    }
    private val versionLabel = JLabel("v${com.yourplugin.dynamodb.BuildConfig.VERSION}  ").apply {
        font       = font.deriveFont(Font.PLAIN, 10f)
        foreground = DColors.fg3
    }
    private val statusBar = JPanel(BorderLayout()).apply {
        background    = DColors.bg1
        border        = MatteBorder(1, 0, 0, 0, DColors.line)
        preferredSize = Dimension(0, 24)
        maximumSize   = Dimension(Int.MAX_VALUE, 24)
        minimumSize   = Dimension(0, 24)
        add(statusLabel,  BorderLayout.WEST)
        add(versionLabel, BorderLayout.EAST)
    }

    // ── Query runner ──────────────────────────────────────────────────────────
    private val queryRunner = QueryRunnerPanel(
        project        = project,
        connectionName = connectionName,
        tableName      = tableName,
        onSchemaLoaded = { schema ->
            SwingUtilities.invokeLater {
                versionLabel.text = "PK: ${schema.partitionKey.name}  ·  v${com.yourplugin.dynamodb.BuildConfig.VERSION}  "
            }
        },
    )

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        background = DColors.bg0

        statusLabel.text = "  $connectionName  ·  $tableName"

        add(queryRunner, BorderLayout.CENTER)
        add(statusBar,   BorderLayout.SOUTH)
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun showQueryRunnerData() = queryRunner.showTableData()
    fun dispose()             = queryRunner.dispose()
}
