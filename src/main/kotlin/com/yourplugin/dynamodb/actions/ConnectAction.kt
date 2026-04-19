package com.yourplugin.dynamodb.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.ui.ConnectDialog

class ConnectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val registry = ApplicationManager.getApplication()
            .getService(DynamoConnectionRegistry::class.java)
        ConnectDialog(project, registry).show()
    }
}
