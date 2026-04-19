package com.yourplugin.dynamodb.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Minimal Anthropic Messages API client.
 * Uses only Java 11+ built-in HttpClient — no extra dependency needed.
 */
object ClaudeClient {

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val mapper = jacksonObjectMapper()

    data class Message(val role: String, val content: String)

    /**
     * Sends a single-turn request and returns the text of the first content block.
     * Throws on HTTP error or malformed response.
     */
    fun complete(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 1024,
    ): String {
        val body = mapper.writeValueAsString(mapOf(
            "model" to model,
            "max_tokens" to maxTokens,
            "system" to systemPrompt,
            "messages" to listOf(mapOf("role" to "user", "content" to userMessage)),
        ))

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            val errBody = runCatching { mapper.readTree(response.body()) }.getOrNull()
            val errMsg = errBody?.path("error")?.path("message")?.asText()
                ?: response.body().take(200)
            throw RuntimeException("Anthropic API error ${response.statusCode()}: $errMsg")
        }

        val tree = mapper.readTree(response.body())
        return tree.path("content").firstOrNull()?.path("text")?.asText()
            ?: throw RuntimeException("Unexpected response shape: ${response.body().take(200)}")
    }
}
