package com.yourplugin.dynamodb.ai

import com.yourplugin.dynamodb.model.GsiInfo
import com.yourplugin.dynamodb.model.TableSchema
import com.yourplugin.dynamodb.settings.DynamoProSettings

/**
 * Translates a natural-language access pattern request into a DQL query string.
 *
 * Two modes:
 *  1. CLAUDE — calls Anthropic API when an API key is configured (Phase 2)
 *  2. LOCAL  — rule-based heuristics when no API key is set; covers ~80% of common patterns
 *
 * The UI calls [translate] and always gets back a DQL string it can drop into the editor.
 */
object AiQueryTranslator {

    data class TranslationResult(
        val dql: String,
        val explanation: String,
        val usedIndex: String?,       // null = base table
        val confidence: Confidence,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    /**
     * Entry point. Tries Claude API first if a key is configured, falls back to heuristics.
     */
    fun translate(request: String, schema: TableSchema): TranslationResult {
        val settings = DynamoProSettings.getInstance()
        if (settings.aiEnabled && settings.anthropicApiKey.isNotBlank()) {
            return runCatching {
                claudeTranslate(request.trim(), schema, settings.anthropicApiKey, settings.anthropicModel)
            }.getOrElse { ex ->
                // Surface the error but still try the heuristic fallback
                val heuristic = heuristicTranslate(request.trim(), schema)
                heuristic.copy(
                    explanation = "⚠ Claude API error: ${ex.message}\n\nFallback result:\n${heuristic.explanation}",
                    confidence = Confidence.LOW,
                )
            }
        }
        return heuristicTranslate(request.trim(), schema)
    }

    // ── Claude API translator ─────────────────────────────────────────────────

    private fun claudeTranslate(
        request: String,
        schema: TableSchema,
        apiKey: String,
        model: String,
    ): TranslationResult {
        val systemPrompt = buildSystemPrompt(schema)
        val userMessage = buildUserMessage(request, schema)

        val raw = ClaudeClient.complete(
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            maxTokens = 1024,
        )

        return parseClaudeResponse(raw, schema)
    }

    private fun buildSystemPrompt(schema: TableSchema): String {
        val gsiList = if (schema.gsis.isEmpty()) "  (none)" else
            schema.gsis.joinToString("\n") { gsi ->
                "  - ${gsi.indexName}: PK=${gsi.partitionKey.name}" +
                    (gsi.sortKey?.let { " SK=${it.name}" } ?: "")
            }
        val lsiList = if (schema.lsis.isEmpty()) "" else
            "\nLSIs:\n" + schema.lsis.joinToString("\n") { "  - ${it.indexName}: SK=${it.sortKey.name}" }

        return """
            You are an expert DynamoDB query generator. Given a natural-language access pattern request,
            you output a valid DQL (DynamoDB Query Language) query plus a brief explanation.

            The DQL syntax is:
              SELECT * FROM <table> [USE INDEX (<indexName>)] [WHERE <keyCondition>] [LIMIT <n>] [ASC|DESC]

            Rules:
            - Always prefer a Query over a Scan (use a WHERE clause with the PK)
            - Use USE INDEX when a GSI/LSI is more appropriate than the base table
            - Use #attrName for attribute names (ExpressionAttributeNames)
            - Use :paramName for values (ExpressionAttributeValues)
            - Include begins_with(#sk, :prefix) for prefix-based SK conditions

            Current table schema:
              Table: ${schema.tableName}
              Base PK: ${schema.partitionKey.name} (${schema.partitionKey.type})
              Base SK: ${schema.sortKey?.let { "${it.name} (${it.type})" } ?: "none"}
            GSIs:
            $gsiList$lsiList

            Respond in this exact JSON format (no markdown fences, just raw JSON):
            {
              "dql": "<the complete DQL query>",
              "explanation": "<1-3 sentence explanation of which index was chosen and why>",
              "usedIndex": "<index name or null for base table>",
              "confidence": "HIGH|MEDIUM|LOW"
            }
        """.trimIndent()
    }

    private fun buildUserMessage(request: String, schema: TableSchema): String =
        "Generate a DQL query for: $request"

    private fun parseClaudeResponse(raw: String, schema: TableSchema): TranslationResult {
        // Strip markdown fences if Claude added them
        val json = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val node = mapper.readTree(json)

        val dql = node.path("dql").asText().trim()
        val explanation = node.path("explanation").asText().trim()
        val usedIndex = node.path("usedIndex").takeIf { !it.isNull && it.asText() != "null" }?.asText()
        val confidence = when (node.path("confidence").asText().uppercase()) {
            "HIGH"   -> Confidence.HIGH
            "MEDIUM" -> Confidence.MEDIUM
            else     -> Confidence.LOW
        }

        if (dql.isBlank()) throw RuntimeException("Claude returned empty DQL")

        return TranslationResult(
            dql = dql,
            explanation = "Claude: $explanation",
            usedIndex = usedIndex,
            confidence = confidence,
        )
    }

    // ── Heuristic translator ──────────────────────────────────────────────────

    private fun heuristicTranslate(req: String, schema: TableSchema): TranslationResult {
        val lower = req.lowercase()

        // ── Pattern: "all X for Y" / "X by Y"  ───────────────────────────────
        val entityMatch = Regex("""(?:all\s+)?(\w+)s?\s+(?:for|by|of)\s+(\w+)\s+(\w+)""")
            .find(lower)

        if (entityMatch != null) {
            val (_, entity, _, identifierValue) = entityMatch.groupValues
            // Try to find a GSI whose PK looks like it indexes the entity
            val gsi = schema.gsis.firstOrNull { gsi ->
                gsi.partitionKey.name.contains(entity, ignoreCase = true) ||
                    gsi.partitionKey.name.contains("id", ignoreCase = true)
            }
            if (gsi != null) {
                return buildGsiQuery(schema, gsi, entity, identifierValue, req)
            }
        }

        // ── Pattern: "last N days / hours" ────────────────────────────────────
        val timeMatch = Regex("""last\s+(\d+)\s+(day|hour|week|month)s?""").find(lower)
        if (timeMatch != null) {
            val (_, amount, unit) = timeMatch.groupValues
            val gsi = schema.gsis.firstOrNull { gsi ->
                val sk = gsi.sortKey?.name?.lowercase() ?: ""
                sk.contains("date") || sk.contains("time") || sk.contains("created") || sk.contains("at")
            }
            if (gsi != null) {
                val (from, to) = timeRangeValues(amount.toInt(), unit)
                val dql = """
                    -- Auto-generated: $req
                    SELECT *
                    FROM ${schema.tableName}
                    USE INDEX (${gsi.indexName})
                    WHERE #${gsi.partitionKey.name} = :pkVal
                      AND #${gsi.sortKey!!.name} BETWEEN :from AND :to
                    LIMIT 100
                    DESC
                """.trimIndent()
                return TranslationResult(
                    dql = dql,
                    explanation = "Using ${gsi.indexName} to filter by ${gsi.sortKey.name} in the last $amount ${unit}s.\n" +
                        "Replace :pkVal with your partition key value.\n" +
                        ":from = $from  |  :to = $to",
                    usedIndex = gsi.indexName,
                    confidence = Confidence.MEDIUM,
                )
            }
        }

        // ── Pattern: "find/get/fetch X where attr = value" ────────────────────
        val whereMatch = Regex("""(?:find|get|fetch|show|list)\s+.*?where\s+(\w+)\s*=\s*[\"']?(\w+)[\"']?""")
            .find(lower)
        if (whereMatch != null) {
            val (_, attr, value) = whereMatch.groupValues
            // Does a GSI index this attribute?
            val gsi = schema.gsis.firstOrNull { it.partitionKey.name.equals(attr, ignoreCase = true) }
            return if (gsi != null) {
                TranslationResult(
                    dql = """
                        -- Auto-generated: $req
                        SELECT *
                        FROM ${schema.tableName}
                        USE INDEX (${gsi.indexName})
                        WHERE #${gsi.partitionKey.name} = :${attr}Val
                        LIMIT 50
                    """.trimIndent(),
                    explanation = "Found GSI '${gsi.indexName}' that indexes '$attr'.\nSet :${attr}Val = \"$value\".",
                    usedIndex = gsi.indexName,
                    confidence = Confidence.HIGH,
                )
            } else {
                // Fall back to base table scan with filter
                TranslationResult(
                    dql = """
                        -- Auto-generated: $req
                        -- ⚠ No GSI found for '$attr' — this will Scan the full table
                        SELECT *
                        FROM ${schema.tableName}
                        WHERE #$attr = :${attr}Val
                        LIMIT 50
                    """.trimIndent(),
                    explanation = "No GSI found for '$attr'. Consider adding one to avoid a full Scan.",
                    usedIndex = null,
                    confidence = Confidence.LOW,
                )
            }
        }

        // ── Fallback: generic template ────────────────────────────────────────
        val pkName = schema.partitionKey.name
        val skName = schema.sortKey?.name
        val bestGsi = schema.gsis.firstOrNull()

        val dql = buildString {
            appendLine("-- Auto-generated from: $req")
            appendLine("-- TODO: fill in the correct WHERE conditions")
            append("SELECT *\nFROM ${schema.tableName}")
            if (bestGsi != null) append("\nUSE INDEX (${bestGsi.indexName})")
            append("\nWHERE #$pkName = :pkVal")
            if (skName != null) append("\n  AND #$skName = :skVal")
            append("\nLIMIT 50")
        }

        return TranslationResult(
            dql = dql,
            explanation = "Could not parse the request precisely. Generated a template using " +
                (bestGsi?.let { "GSI '${it.indexName}'" } ?: "the base table") + ".\n" +
                "Available GSIs: ${schema.gsis.joinToString { it.indexName }.ifEmpty { "none" }}",
            usedIndex = bestGsi?.indexName,
            confidence = Confidence.LOW,
        )
    }

    private fun buildGsiQuery(
        schema: TableSchema,
        gsi: GsiInfo,
        entity: String,
        identifier: String,
        original: String,
    ): TranslationResult {
        val dql = """
            -- Auto-generated: $original
            SELECT *
            FROM ${schema.tableName}
            USE INDEX (${gsi.indexName})
            WHERE #${gsi.partitionKey.name} = :${entity}Id
            LIMIT 100
        """.trimIndent()
        return TranslationResult(
            dql = dql,
            explanation = "Using GSI '${gsi.indexName}' on '${gsi.partitionKey.name}'.\n" +
                "Set :${entity}Id to the $entity identifier (e.g. \"$identifier\").",
            usedIndex = gsi.indexName,
            confidence = Confidence.MEDIUM,
        )
    }

    /** Returns ISO-8601 range strings for the last N units */
    private fun timeRangeValues(amount: Int, unit: String): Pair<String, String> {
        val now = java.time.Instant.now()
        val from = when (unit) {
            "hour"  -> now.minus(amount.toLong(), java.time.temporal.ChronoUnit.HOURS)
            "day"   -> now.minus(amount.toLong(), java.time.temporal.ChronoUnit.DAYS)
            "week"  -> now.minus((amount * 7).toLong(), java.time.temporal.ChronoUnit.DAYS)
            "month" -> now.minus((amount * 30).toLong(), java.time.temporal.ChronoUnit.DAYS)
            else    -> now.minus(amount.toLong(), java.time.temporal.ChronoUnit.DAYS)
        }
        return from.toString() to now.toString()
    }
}
