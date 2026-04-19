package com.yourplugin.dynamodb.language

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class DynamoQueryColorSettingsPage : ColorSettingsPage {

    private val descriptors = arrayOf(
        AttributesDescriptor("Keyword",          DynamoQuerySyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Function",         DynamoQuerySyntaxHighlighter.FUNCTION),
        AttributesDescriptor("String",           DynamoQuerySyntaxHighlighter.STRING),
        AttributesDescriptor("Number",           DynamoQuerySyntaxHighlighter.NUMBER),
        AttributesDescriptor("Comment",          DynamoQuerySyntaxHighlighter.COMMENT),
        AttributesDescriptor("Parameter (:val)", DynamoQuerySyntaxHighlighter.PARAM_REF),
        AttributesDescriptor("Name ref (#attr)", DynamoQuerySyntaxHighlighter.NAME_REF),
        AttributesDescriptor("Operator",         DynamoQuerySyntaxHighlighter.OPERATOR),
        AttributesDescriptor("Dry-run directive",DynamoQuerySyntaxHighlighter.DIRECTIVE),
    )

    override fun getIcon(): Icon? = null
    override fun getHighlighter(): SyntaxHighlighter = DynamoQuerySyntaxHighlighter()
    override fun getDisplayName() = "DynamoDB Query (DQL)"
    override fun getAttributeDescriptors() = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap() = null

    override fun getDemoText() = """
        -- Find all orders for a customer placed this year
        --dry-run
        SELECT orderId, status, totalAmount, createdAt
        FROM OrdersTable
        USE INDEX (GSI_CustomerDate)
        WHERE #customerId = :custId
          AND begins_with(#sk, "ORDER#2026")
          AND #status <> "CANCELLED"
        LIMIT 50
        DESC

        -- Increment a view counter atomically
        UPDATE PageViewsTable
        WHERE #pk = :pageId AND #sk = :dateKey
        SET #views = if_not_exists(#views, :zero) + :one
    """.trimIndent()
}
