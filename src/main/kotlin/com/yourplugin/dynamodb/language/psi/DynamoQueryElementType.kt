package com.yourplugin.dynamodb.language.psi

import com.intellij.psi.tree.IElementType
import com.yourplugin.dynamodb.language.DynamoQueryLanguage

class DynamoQueryElementType(debugName: String) :
    IElementType(debugName, DynamoQueryLanguage)
