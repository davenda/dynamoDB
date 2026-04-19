package com.yourplugin.dynamodb.language.psi

import com.intellij.psi.tree.IElementType
import com.yourplugin.dynamodb.language.DynamoQueryLanguage

class DynamoQueryTokenType(debugName: String) :
    IElementType(debugName, DynamoQueryLanguage) {
    override fun toString() = "DynamoQueryTokenType.${super.toString()}"
}
