package com.yourplugin.dynamodb.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application-level settings for DynamoDB Pro.
 * Stored in dynamodb-pro-settings.xml in the IDE config dir.
 */
@State(
    name = "DynamoProSettings",
    storages = [Storage("dynamodb-pro-settings.xml")]
)
class DynamoProSettings : PersistentStateComponent<DynamoProSettings.State> {

    class State {
        var anthropicApiKey: String = ""
        var anthropicModel: String = "claude-opus-4-6"
        var aiEnabled: Boolean = true
    }

    private var _state = State()

    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    var anthropicApiKey: String
        get() = _state.anthropicApiKey
        set(v) { _state.anthropicApiKey = v }

    var anthropicModel: String
        get() = _state.anthropicModel
        set(v) { _state.anthropicModel = v }

    var aiEnabled: Boolean
        get() = _state.aiEnabled
        set(v) { _state.aiEnabled = v }

    companion object {
        fun getInstance(): DynamoProSettings = service()
    }
}
