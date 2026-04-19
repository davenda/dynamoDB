package com.yourplugin.dynamodb.language

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.yourplugin.dynamodb.language.psi.DynamoQueryTypes

class DynamoQuerySyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val KEYWORD   = createTextAttributesKey("DQL_KEYWORD",   DefaultLanguageHighlighterColors.KEYWORD)
        val STRING    = createTextAttributesKey("DQL_STRING",    DefaultLanguageHighlighterColors.STRING)
        val NUMBER    = createTextAttributesKey("DQL_NUMBER",    DefaultLanguageHighlighterColors.NUMBER)
        val COMMENT   = createTextAttributesKey("DQL_COMMENT",   DefaultLanguageHighlighterColors.LINE_COMMENT)
        val PARAM_REF = createTextAttributesKey("DQL_PARAM",     DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val NAME_REF  = createTextAttributesKey("DQL_NAME_REF",  DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val OPERATOR  = createTextAttributesKey("DQL_OPERATOR",  DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val FUNCTION  = createTextAttributesKey("DQL_FUNCTION",  DefaultLanguageHighlighterColors.STATIC_METHOD)
        val DIRECTIVE = createTextAttributesKey("DQL_DIRECTIVE",  DefaultLanguageHighlighterColors.METADATA)
        val BAD_CHAR  = createTextAttributesKey("DQL_BAD_CHAR",  HighlighterColors.BAD_CHARACTER)

        private val KEYWORDS = setOf(
            DynamoQueryTypes.SELECT, DynamoQueryTypes.FROM, DynamoQueryTypes.WHERE,
            DynamoQueryTypes.AND, DynamoQueryTypes.OR, DynamoQueryTypes.NOT,
            DynamoQueryTypes.BETWEEN, DynamoQueryTypes.IN,
            DynamoQueryTypes.INDEX, DynamoQueryTypes.USE,
            DynamoQueryTypes.LIMIT, DynamoQueryTypes.ASC, DynamoQueryTypes.DESC,
            DynamoQueryTypes.UPDATE, DynamoQueryTypes.SET,
            DynamoQueryTypes.REMOVE, DynamoQueryTypes.ADD, DynamoQueryTypes.DELETE,
        )

        private val FUNCTIONS = setOf(
            DynamoQueryTypes.BEGINS_WITH, DynamoQueryTypes.CONTAINS, DynamoQueryTypes.EXISTS,
            DynamoQueryTypes.SIZE, DynamoQueryTypes.TYPE, DynamoQueryTypes.ATTRIBUTE_TYPE,
            DynamoQueryTypes.IF_NOT_EXISTS, DynamoQueryTypes.LIST_APPEND,
        )

        private val OPERATORS = setOf(
            DynamoQueryTypes.EQ, DynamoQueryTypes.NEQ,
            DynamoQueryTypes.LT, DynamoQueryTypes.LTE,
            DynamoQueryTypes.GT, DynamoQueryTypes.GTE,
            DynamoQueryTypes.PLUS, DynamoQueryTypes.MINUS,
        )

        private val EMPTY = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer =
        com.intellij.lexer.FlexAdapter(
            com.yourplugin.dynamodb.language.lexer.DynamoQueryLexer(null)
        )

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when {
        tokenType in KEYWORDS             -> arrayOf(KEYWORD)
        tokenType in FUNCTIONS            -> arrayOf(FUNCTION)
        tokenType in OPERATORS            -> arrayOf(OPERATOR)
        tokenType == DynamoQueryTypes.STRING  -> arrayOf(STRING)
        tokenType == DynamoQueryTypes.NUMBER  -> arrayOf(NUMBER)
        tokenType == DynamoQueryTypes.BOOLEAN -> arrayOf(KEYWORD)
        tokenType == DynamoQueryTypes.NULL    -> arrayOf(KEYWORD)
        tokenType == DynamoQueryTypes.LINE_COMMENT ||
        tokenType == DynamoQueryTypes.BLOCK_COMMENT -> arrayOf(COMMENT)
        tokenType == DynamoQueryTypes.COLON   -> arrayOf(PARAM_REF)   // :val prefix
        tokenType == DynamoQueryTypes.HASH    -> arrayOf(NAME_REF)    // #attr prefix
        tokenType == DynamoQueryTypes.DRY_RUN -> arrayOf(DIRECTIVE)
        tokenType == TokenType.BAD_CHARACTER  -> arrayOf(BAD_CHAR)
        else -> EMPTY
    }
}
