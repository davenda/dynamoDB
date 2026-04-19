package com.yourplugin.dynamodb.language

import com.intellij.lang.Language

object DynamoQueryLanguage : Language("DynamoQuery") {
    private fun readResolve(): Any = DynamoQueryLanguage
}
