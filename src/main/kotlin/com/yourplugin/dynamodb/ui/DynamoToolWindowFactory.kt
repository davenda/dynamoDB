package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DynamoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = DynamoMainPanel(project)

        val expandAction = object : AnAction("Expand All", "Expand all nodes", AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) = mainPanel.expandAll()
        }
        val collapseAction = object : AnAction("Collapse All", "Collapse all nodes", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) = mainPanel.collapseAll()
        }
        toolWindow.setTitleActions(listOf(expandAction, collapseAction))

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
