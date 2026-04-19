package com.yourplugin.dynamodb.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Placeholder for AI Access Pattern generator.
 * Phase 2: wire to IntelliJ AI Assistant SDK (com.intellij.ml.llm).
 */
class AiQueryAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "AI Access Pattern generator coming in Phase 2.\n" +
                "Will use IntelliJ AI Assistant SDK to translate natural language → QueryRequest.",
            "DynamoDB Pro — AI Query"
        )
    }

    override fun update(e: AnActionEvent) {
        // Only enable when AI Assistant plugin is present
        e.presentation.isEnabled = try {
            Class.forName("com.intellij.ml.llm.core.LlmClientManager")
            true
        } catch (_: ClassNotFoundException) { false }
    }
}
