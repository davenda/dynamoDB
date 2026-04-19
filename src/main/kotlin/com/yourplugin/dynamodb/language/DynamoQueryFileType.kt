package com.yourplugin.dynamodb.language

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object DynamoQueryFileType : LanguageFileType(DynamoQueryLanguage) {
    override fun getName()        = "DynamoQuery"
    override fun getDescription() = "DynamoDB Query Language"
    override fun getDefaultExtension() = "dql"
    override fun getIcon(): Icon = IconLoader.getIcon("icons/dynamodb.svg", DynamoQueryFileType::class.java)
}
