package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JPanel

class DynamoTableViewPanel(
    project: Project,
    connectionName: String,
    tableName: String,
) : JPanel(BorderLayout()) {

    private val queryRunnerPanel = QueryRunnerPanel(project, connectionName, tableName)

    init {
        add(queryRunnerPanel, BorderLayout.CENTER)
    }

    fun showQueryRunnerData() {
        queryRunnerPanel.showTableData()
    }

    fun dispose() {
        queryRunnerPanel.dispose()
    }
}
