package com.yourplugin.dynamodb.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.yourplugin.dynamodb.ui.DynamoTableViewPanel
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class DynamoTableFileEditor(
    project: Project,
    private val file: DynamoTableVirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val tableViewPanel = DynamoTableViewPanel(project, file.connectionName, file.tableName).apply {
        showQueryRunnerData()
    }

    override fun getComponent(): JComponent = tableViewPanel

    override fun getPreferredFocusedComponent(): JComponent = tableViewPanel

    override fun getName(): String = "DynamoDB Table"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun selectNotify() {
        tableViewPanel.showQueryRunnerData()
    }

    override fun deselectNotify() = Unit

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        tableViewPanel.dispose()
    }
}

