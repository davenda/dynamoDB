package com.yourplugin.dynamodb.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.yourplugin.dynamodb.model.EntityFacet
import com.yourplugin.dynamodb.model.SchemaMapping

/**
 * Project-level service: persists per-table Entity Facet mappings.
 * These power the Single-Table Design pivot view.
 *
 * Stored as JSON in .idea/dynamodb-schema-map.xml
 */
@State(
    name = "DynamoSchemaMapService",
    storages = [Storage("dynamodb-schema-map.xml")]
)
class SchemaMapService : PersistentStateComponent<SchemaMapService.State> {

    class State {
        // table name → JSON-serialized SchemaMapping
        var mappings: MutableMap<String, String> = mutableMapOf()
    }

    private val mapper = jacksonObjectMapper()
    private var _state = State()

    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    fun getMappingFor(tableName: String): SchemaMapping {
        val json = _state.mappings[tableName] ?: return SchemaMapping(tableName)
        return mapper.readValue(json)
    }

    fun saveMappingFor(mapping: SchemaMapping) {
        _state.mappings[mapping.tableName] = mapper.writeValueAsString(mapping)
    }

    fun addFacet(tableName: String, facet: EntityFacet) {
        val existing = getMappingFor(tableName)
        val updated = existing.copy(
            facets = existing.facets.filterNot { it.entityName == facet.entityName } + facet
        )
        saveMappingFor(updated)
    }

    fun removeFacet(tableName: String, entityName: String) {
        val existing = getMappingFor(tableName)
        saveMappingFor(existing.copy(facets = existing.facets.filterNot { it.entityName == entityName }))
    }

    /**
     * Given an item's PK value, return the matching EntityFacet (if any).
     * Used by the pivot table renderer to decide which columns to show.
     */
    fun resolveEntity(tableName: String, pkValue: String, skValue: String?): EntityFacet? {
        return getMappingFor(tableName).facets.firstOrNull { facet ->
            pkValue.startsWith(facet.pkPrefix) &&
                (facet.skPrefix == null || skValue?.startsWith(facet.skPrefix) == true)
        }
    }
}
