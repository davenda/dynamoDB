package com.yourplugin.dynamodb.language

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class DynamoQueryFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, DynamoQueryLanguage) {

    override fun getFileType() = DynamoQueryFileType
    override fun toString() = "DynamoQuery File"
}
