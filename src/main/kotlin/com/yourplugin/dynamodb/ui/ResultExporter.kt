package com.yourplugin.dynamodb.ui

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.io.File

object ResultExporter {

    private val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

    fun exportJson(items: List<Map<String, AttributeValue>>, file: File) {
        val list = items.map { item -> item.mapValues { (_, v) -> v.toPlain() } }
        mapper.writeValue(file, list)
    }

    fun exportCsv(items: List<Map<String, AttributeValue>>, file: File) {
        if (items.isEmpty()) { file.writeText(""); return }
        val columns = items.flatMap { it.keys }.distinct().sorted()
        val sb = StringBuilder()
        sb.appendLine(columns.joinToString(",") { csvEscape(it) })
        items.forEach { item ->
            sb.appendLine(columns.joinToString(",") { col ->
                csvEscape(item[col]?.toPlainString() ?: "")
            })
        }
        file.writeText(sb.toString())
    }

    private fun AttributeValue.toPlain(): Any? = when {
        s()    != null -> s()
        n()    != null -> n()!!.toBigDecimalOrNull() ?: n()
        bool() != null -> bool()
        nul()  == true -> null
        ss().isNotEmpty() -> ss()
        ns().isNotEmpty() -> ns()
        l().isNotEmpty()  -> l().map { it.toPlain() }
        m().isNotEmpty()  -> m().mapValues { (_, v) -> v.toPlain() }
        b()    != null -> "<binary>"
        else -> null
    }

    private fun AttributeValue.toPlainString(): String = when {
        s()    != null -> s()!!
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        nul()  == true -> "null"
        ss().isNotEmpty() -> ss().joinToString(";")
        ns().isNotEmpty() -> ns().joinToString(";")
        l().isNotEmpty()  -> "[list:${l().size}]"
        m().isNotEmpty()  -> "{map:${m().size}}"
        else -> ""
    }

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            return "\"${s.replace("\"", "\"\"")}\""
        }
        return s
    }
}
