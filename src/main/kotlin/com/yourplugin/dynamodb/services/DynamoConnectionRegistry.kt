package com.yourplugin.dynamodb.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.ProxyConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level service: manages named DynamoDB connections.
 * State is persisted to dynamodb-connections.xml in IDE config dir.
 */
@State(
    name = "DynamoConnectionRegistry",
    storages = [Storage("dynamodb-connections.xml")]
)
class DynamoConnectionRegistry : PersistentStateComponent<DynamoConnectionRegistry.State> {

    data class ConnectionConfig(
        var name: String = "",
        var region: String = "us-east-1",
        var profileName: String? = null,         // AWS CLI named profile
        var endpointOverride: String? = null,    // for DynamoDB Local
        var roleArn: String? = null,             // for cross-account access
        var credentialType: CredentialType = CredentialType.DEFAULT_CHAIN,
        var proxyHost: String? = null,           // HTTP/HTTPS proxy host
        var proxyPort: Int? = null,              // proxy port (defaults to 8080 if host is set)
    )

    enum class CredentialType { DEFAULT_CHAIN, NAMED_PROFILE, STATIC_KEYS, ROLE_ASSUMPTION }

    class State {
        var connections: MutableList<ConnectionConfig> = mutableListOf()
    }

    private var _state = State()
    private val clients = ConcurrentHashMap<String, DynamoDbAsyncClient>()

    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    fun allConnections(): List<ConnectionConfig> = _state.connections.toList()

    fun addConnection(config: ConnectionConfig) {
        _state.connections.removeIf { it.name == config.name }
        _state.connections.add(config)
    }

    fun removeConnection(name: String) {
        clients.remove(name)?.close()
        _state.connections.removeIf { it.name == name }
    }

    /**
     * Returns (or lazily creates) an async DynamoDB client for the named connection.
     * All AWS calls in the plugin go through here so we get connection reuse.
     */
    fun clientFor(name: String): DynamoDbAsyncClient {
        return clients.getOrPut(name) {
            val config = _state.connections.first { it.name == name }
            buildClient(config)
        }
    }

    private fun buildClient(config: ConnectionConfig): DynamoDbAsyncClient {
        val credentials: AwsCredentialsProvider = when (config.credentialType) {
            CredentialType.DEFAULT_CHAIN -> DefaultCredentialsProvider.create()
            CredentialType.NAMED_PROFILE -> ProfileCredentialsProvider.create(config.profileName)
            CredentialType.STATIC_KEYS   -> DefaultCredentialsProvider.create() // placeholder
            CredentialType.ROLE_ASSUMPTION -> DefaultCredentialsProvider.create() // add STS later
        }

        val httpClientBuilder = NettyNioAsyncHttpClient.builder()
        if (config.proxyHost != null) {
            httpClientBuilder.proxyConfiguration(
                ProxyConfiguration.builder()
                    .host(config.proxyHost!!)
                    .port(config.proxyPort ?: 8080)
                    .scheme("https")
                    .build()
            )
        }

        return DynamoDbAsyncClient.builder()
            .region(Region.of(config.region))
            .credentialsProvider(credentials)
            .httpClientBuilder(httpClientBuilder)
            .apply {
                config.endpointOverride?.let {
                    endpointOverride(URI.create(it))
                }
            }
            .build()
    }

    fun dispose() = clients.values.forEach { it.close() }
}
