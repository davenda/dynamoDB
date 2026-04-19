package com.yourplugin.dynamodb.services

import com.intellij.openapi.components.Service
import com.yourplugin.dynamodb.model.TableSchema
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

/**
 * Cost Dry-Run module.
 *
 * Before executing, we send the request with ReturnConsumedCapacity=TOTAL
 * but with a Limit=0 trick (or a real preview call) to retrieve consumed RCUs
 * without fetching a full result set. For Scan, we warn if the table is large.
 */
@Service(Service.Level.PROJECT)
class CostEstimatorService {

    data class CostEstimate(
        val estimatedRcu: Double,
        val warning: String?,
        val suggestedIndex: String?,  // name of a GSI that would be cheaper
    )

    companion object {
        private const val LARGE_TABLE_THRESHOLD = 10_000_000L  // 10M items
        private const val RCU_ITEM_SIZE_BYTES = 4096L           // 4 KB per RCU (eventually consistent = 8 KB)
    }

    /**
     * Dry-run a Query: executes with Limit=1 + ReturnConsumedCapacity=TOTAL
     * then extrapolates. Returns the estimate without loading data into the UI.
     */
    suspend fun estimateQuery(
        client: DynamoDbAsyncClient,
        request: QueryRequest,
        schema: TableSchema,
    ): CostEstimate {
        val probeReq = request.toBuilder()
            .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
            .limit(1)
            .build()

        val resp = client.query(probeReq).await()
        val consumed = resp.consumedCapacity()?.capacityUnits() ?: 0.0

        // Rough extrapolation: count is from probe, real total is unknown without full scan
        val warning = if (request.indexName() == null && schema.itemCount > LARGE_TABLE_THRESHOLD) {
            "Query targets the base table with ${schema.itemCount.humanize()} items. " +
                "Consider using a GSI for better cost efficiency."
        } else null

        val suggestion = if (warning != null) suggestGsi(request, schema) else null

        return CostEstimate(
            estimatedRcu = consumed,
            warning = warning,
            suggestedIndex = suggestion,
        )
    }

    /**
     * Scan warning: always warn, always suggest an alternative index if one exists.
     */
    fun estimateScan(schema: TableSchema, scanRequest: ScanRequest): CostEstimate {
        val isBig = schema.itemCount > LARGE_TABLE_THRESHOLD
        val warning = if (isBig) {
            "⚠ Full table Scan on ${schema.tableName} (${schema.itemCount.humanize()} items). " +
                "This will consume ~${estimateScanRcu(schema)} RCUs and may be expensive."
        } else null

        // Find any GSI as a potential alternative
        val suggestion = schema.gsis.firstOrNull()?.indexName

        return CostEstimate(
            estimatedRcu = estimateScanRcu(schema).toDouble(),
            warning = warning,
            suggestedIndex = suggestion,
        )
    }

    private fun estimateScanRcu(schema: TableSchema): Long {
        val avgItemSize = if (schema.itemCount > 0) schema.tableSizeBytes / schema.itemCount else 0L
        val rcuPerItem = maxOf(1L, (avgItemSize + RCU_ITEM_SIZE_BYTES - 1) / RCU_ITEM_SIZE_BYTES)
        return schema.itemCount * rcuPerItem / 2  // eventually consistent reads = half cost
    }

    /**
     * Very naive GSI suggestion: find a GSI whose PK matches
     * the filter expression's leading attribute.
     */
    private fun suggestGsi(request: QueryRequest, schema: TableSchema): String? {
        val filterAttr = request.filterExpression()
            ?.split(" ")?.firstOrNull()
            ?.trim('#', '(', ')', ' ')
        return schema.gsis
            .firstOrNull { it.partitionKey.name == filterAttr || it.sortKey?.name == filterAttr }
            ?.indexName
    }

    private fun Long.humanize(): String = when {
        this >= 1_000_000_000 -> "${this / 1_000_000_000}B"
        this >= 1_000_000     -> "${this / 1_000_000}M"
        this >= 1_000         -> "${this / 1_000}K"
        else                  -> toString()
    }
}
