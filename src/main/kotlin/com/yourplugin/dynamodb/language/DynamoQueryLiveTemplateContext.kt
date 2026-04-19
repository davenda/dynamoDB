package com.yourplugin.dynamodb.language

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

class DynamoQueryLiveTemplateContext :
    TemplateContextType("DynamoQuery", "DynamoDB (DQL)") {

    override fun isInContext(context: TemplateActionContext): Boolean =
        context.file.language == DynamoQueryLanguage
}
