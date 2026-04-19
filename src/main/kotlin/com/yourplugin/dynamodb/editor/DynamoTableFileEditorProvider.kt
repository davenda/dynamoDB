package com.yourplugin.dynamodb.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DynamoTableFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is DynamoTableVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        DynamoTableFileEditor(project, file as DynamoTableVirtualFile)

    override fun getEditorTypeId(): String = "dynamodb-table-file-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

