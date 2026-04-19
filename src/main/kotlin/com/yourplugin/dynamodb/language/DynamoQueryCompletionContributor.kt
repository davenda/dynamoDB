package com.yourplugin.dynamodb.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.yourplugin.dynamodb.language.psi.DynamoQueryTypes
import com.yourplugin.dynamodb.services.DynamoConnectionRegistry
import com.yourplugin.dynamodb.services.TableSchemaService
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.runBlocking

/**
 * Provides completions for .dql files:
 *
 * 1. Keywords (SELECT, FROM, WHERE, …)
 * 2. Table names (after FROM)
 * 3. Index names (after USE INDEX)
 * 4. Attribute names — sourced live from the table currently in scope
 * 5. Common Live Templates (SET … if_not_exists, increment, etc.)
 */
class DynamoQueryCompletionContributor : CompletionContributor() {

    init {
        // All completions handled in fillCompletionVariants for context sensitivity
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            DynamoQueryCompletionProvider(),
        )
    }
}

private class DynamoQueryCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val keywords = listOf(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT",
        "BETWEEN", "IN", "BEGINS_WITH", "CONTAINS", "EXISTS",
        "INDEX", "LIMIT", "ASC", "DESC",
        "UPDATE", "SET", "REMOVE", "ADD", "DELETE",
        "if_not_exists", "list_append", "size", "attribute_type",
        "--dry-run",
    )

    private val liveTemplates = mapOf(
        "SET … if_not_exists" to "SET #attr = if_not_exists(#attr, :default)",
        "Increment counter"   to "SET #counter = #counter + :one",
        "Append to list"      to "SET #list = list_append(#list, :newItems)",
        "Query by PK"         to "SELECT * FROM \$TABLE\$ WHERE #pk = :pkVal",
        "Query by PK+SK begins_with" to
            "SELECT * FROM \$TABLE\$ WHERE #pk = :pkVal AND begins_with(#sk, :skPrefix)",
        "Query via GSI"       to
            "SELECT * FROM \$TABLE\$ USE INDEX (\$GSI\$) WHERE #gsiPk = :gsiPkVal LIMIT 50",
    )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)
        val prevText = prevLeaf?.text?.uppercase()

        // ── 1. Keywords ──────────────────────────────────────────────────────
        keywords.forEach { kw ->
            result.addElement(
                LookupElementBuilder.create(kw)
                    .bold()
                    .withTypeText("keyword", true)
            )
        }

        // ── 2. Live templates ─────────────────────────────────────────────────
        liveTemplates.forEach { (label, template) ->
            result.addElement(
                LookupElementBuilder.create(template)
                    .withPresentableText(label)
                    .withTypeText("template", true)
                    .withBoldness(false)
            )
        }

        // ── 3. Table names (after FROM) ───────────────────────────────────────
        if (prevText == "FROM") {
            tableNames().forEach { name ->
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText("table", true)
                        .withIcon(null)
                )
            }
            return
        }

        // ── 4. Index names (after INDEX (...)) ────────────────────────────────
        val tableName = extractTableNameFromFile(parameters) ?: return
        if (prevText == "INDEX" || prevLeaf?.node?.elementType == DynamoQueryTypes.LPAREN) {
            indexNames(tableName).forEach { idx ->
                result.addElement(
                    LookupElementBuilder.create(idx)
                        .withTypeText("GSI/LSI", true)
                )
            }
        }

        // ── 5. Attribute names (WHERE / SET / REMOVE context) ─────────────────
        if (prevText in setOf("WHERE", "AND", "OR", "NOT", "SET", "REMOVE", "ADD")) {
            attributeNames(tableName).forEach { attr ->
                result.addElement(
                    LookupElementBuilder.create("#$attr")  // prefixed for ExpressionAttributeNames safety
                        .withPresentableText(attr)
                        .withTypeText("attribute", true)
                )
                result.addElement(
                    LookupElementBuilder.create(":$attr")  // corresponding param ref
                        .withPresentableText(":$attr")
                        .withTypeText("param", true)
                )
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun registry() = ApplicationManager.getApplication()
        .getService(DynamoConnectionRegistry::class.java)

    private fun tableNames(): List<String> = runCatching {
        val reg = registry()
        val conn = reg.allConnections().firstOrNull() ?: return emptyList()
        runBlocking { TableSchemaService.listTables(reg.clientFor(conn.name)) }
    }.getOrElse { emptyList() }

    private fun indexNames(tableName: String): List<String> = runCatching {
        val reg = registry()
        val conn = reg.allConnections().firstOrNull() ?: return emptyList()
        val schema = runBlocking { TableSchemaService.describe(reg.clientFor(conn.name), tableName) }
        (schema.gsis.map { it.indexName } + schema.lsis.map { it.indexName })
    }.getOrElse { emptyList() }

    private fun attributeNames(tableName: String): List<String> = runCatching {
        val reg = registry()
        val conn = reg.allConnections().firstOrNull() ?: return emptyList()
        val schema = runBlocking { TableSchemaService.describe(reg.clientFor(conn.name), tableName) }
        // Return declared attribute definitions + well-known key names
        schema.attributeDefinitions.map { it.name }
    }.getOrElse { emptyList() }

    /**
     * Walk backwards through the PSI file to find the table name in the FROM clause.
     * Simple heuristic: find the token after FROM in the same statement.
     */
    private fun extractTableNameFromFile(params: CompletionParameters): String? {
        val text = params.originalFile.text
        val fromIdx = text.lastIndexOf("FROM ", params.offset).takeIf { it >= 0 } ?: return null
        val afterFrom = text.substring(fromIdx + 5).trimStart()
        return afterFrom.split(Regex("\\s+|\\(")).firstOrNull()?.trim()
    }
}
