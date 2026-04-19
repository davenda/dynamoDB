package com.yourplugin.dynamodb.services

import com.yourplugin.dynamodb.model.*
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

/**
 * Fetches and maps the full TableSchema (including per-GSI item counts/sizes)
 * from DescribeTable. All methods are suspend functions — call from a coroutine scope.
 */
object TableSchemaService {

    suspend fun describe(client: DynamoDbAsyncClient, tableName: String): TableSchema {
        val resp = client.describeTable { it.tableName(tableName) }.await()
        val td = resp.table()
        return td.toTableSchema()
    }

    suspend fun listTables(client: DynamoDbAsyncClient): List<String> {
        val tables = mutableListOf<String>()
        var lastKey: String? = null
        do {
            val resp = client.listTables { req ->
                req.limit(100)
                lastKey?.let { req.exclusiveStartTableName(it) }
            }.await()
            tables += resp.tableNames()
            lastKey = resp.lastEvaluatedTableName()
        } while (lastKey != null)
        return tables
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun TableDescription.toTableSchema(): TableSchema {
        val attrMap = attributeDefinitions().associate {
            it.attributeName() to it.attributeType()
        }

        fun KeySchemaElement.toDef() = KeyDef(attributeName(), attrMap[attributeName()]!!)

        val pkDef = keySchema().first { it.keyType() == KeyType.HASH }.toDef()
        val skDef = keySchema().firstOrNull { it.keyType() == KeyType.RANGE }?.toDef()

        val gsis = (globalSecondaryIndexes() ?: emptyList()).map { gsi ->
            val gsiPk = gsi.keySchema().first { it.keyType() == KeyType.HASH }.toDef()
            val gsiSk = gsi.keySchema().firstOrNull { it.keyType() == KeyType.RANGE }?.toDef()
            GsiInfo(
                indexName = gsi.indexName(),
                partitionKey = gsiPk,
                sortKey = gsiSk,
                projection = gsi.projection().projectionType(),
                nonKeyAttributes = gsi.projection().nonKeyAttributes() ?: emptyList(),
                itemCount = gsi.itemCount() ?: 0L,
                sizeBytes = gsi.indexSizeBytes() ?: 0L,
                readCapacityUnits = gsi.provisionedThroughput()?.readCapacityUnits(),
                writeCapacityUnits = gsi.provisionedThroughput()?.writeCapacityUnits(),
                status = gsi.indexStatus(),
            )
        }

        val lsis = (localSecondaryIndexes() ?: emptyList()).map { lsi ->
            val lsiSk = lsi.keySchema().first { it.keyType() == KeyType.RANGE }.toDef()
            LsiInfo(
                indexName = lsi.indexName(),
                sortKey = lsiSk,
                projection = lsi.projection().projectionType(),
                nonKeyAttributes = lsi.projection().nonKeyAttributes() ?: emptyList(),
                itemCount = lsi.itemCount() ?: 0L,
                sizeBytes = lsi.indexSizeBytes() ?: 0L,
            )
        }

        return TableSchema(
            tableName = tableName(),
            status = tableStatus(),
            billingMode = billingModeSummary()?.billingMode() ?: BillingMode.PROVISIONED,
            partitionKey = pkDef,
            sortKey = skDef,
            readCapacityUnits = provisionedThroughput()?.readCapacityUnits(),
            writeCapacityUnits = provisionedThroughput()?.writeCapacityUnits(),
            itemCount = itemCount() ?: 0L,
            tableSizeBytes = tableSizeBytes() ?: 0L,
            gsis = gsis,
            lsis = lsis,
            attributeDefinitions = attributeDefinitions().map {
                AttributeDef(it.attributeName(), it.attributeType())
            },
            streamEnabled = streamSpecification()?.streamEnabled() ?: false,
            streamViewType = streamSpecification()?.streamViewType(),
        )
    }
}
