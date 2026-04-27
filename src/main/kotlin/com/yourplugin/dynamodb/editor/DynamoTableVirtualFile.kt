package com.yourplugin.dynamodb.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

class DynamoTableVirtualFile(
    val connectionName: String,
    val tableName: String,
) : LightVirtualFile("$tableName [$connectionName]") {

    companion object {
        fun findOrCreate(project: Project, connectionName: String, tableName: String): DynamoTableVirtualFile {
            val editorManager = FileEditorManager.getInstance(project)
            return editorManager.openFiles
                .filterIsInstance<DynamoTableVirtualFile>()
                .firstOrNull { it.connectionName == connectionName && it.tableName == tableName }
                ?: DynamoTableVirtualFile(connectionName, tableName)
        }
    }

    override fun getPath(): String = "dynamodb://$connectionName/$tableName"

    override fun isWritable(): Boolean = true
}

