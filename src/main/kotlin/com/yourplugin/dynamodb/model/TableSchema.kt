package com.yourplugin.dynamodb.model

import software.amazon.awssdk.services.dynamodb.model.*

/**
 * Enriched snapshot of a DynamoDB table — everything the plugin needs
 * to power the GSI Analyzer, Entity Facets view, and Cost Dry-Run.
 */
data class TableSchema(
    val tableName: String,
    val status: TableStatus,
    val billingMode: BillingMode,

    // Key schema
    val partitionKey: KeyDef,
    val sortKey: KeyDef?,

    // Throughput (null for on-demand)
    val readCapacityUnits: Long?,
    val writeCapacityUnits: Long?,

    // Size metrics
    val itemCount: Long,
    val tableSizeBytes: Long,

    // Indexes
    val gsis: List<GsiInfo>,
    val lsis: List<LsiInfo>,

    // Attribute definitions (all declared attributes)
    val attributeDefinitions: List<AttributeDef>,

    // Stream config
    val streamEnabled: Boolean,
    val streamViewType: StreamViewType?,
)

data class KeyDef(val name: String, val type: ScalarAttributeType)

data class AttributeDef(val name: String, val type: ScalarAttributeType)

data class GsiInfo(
    val indexName: String,
    val partitionKey: KeyDef,
    val sortKey: KeyDef?,
    val projection: ProjectionType,
    val nonKeyAttributes: List<String>,  // only for INCLUDE projection
    val itemCount: Long,
    val sizeBytes: Long,
    val readCapacityUnits: Long?,
    val writeCapacityUnits: Long?,
    val status: IndexStatus,
)

data class LsiInfo(
    val indexName: String,
    val sortKey: KeyDef,           // LSI always shares the table PK
    val projection: ProjectionType,
    val nonKeyAttributes: List<String>,
    val itemCount: Long,
    val sizeBytes: Long,
)

/** User-defined mapping: "if PK matches this prefix → treat as EntityType" */
data class EntityFacet(
    val entityName: String,
    val pkPrefix: String,          // e.g. "USER#"
    val skPrefix: String? = null,  // optional, e.g. "PROFILE#"
    val visibleAttributes: List<String> = emptyList(),  // empty = show all
    val color: String = "#4A90D9", // hex for UI badge
)

/** Schema mapping config persisted per-project per-table */
data class SchemaMapping(
    val tableName: String,
    val facets: List<EntityFacet> = emptyList(),
)
