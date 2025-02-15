package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.Charset
import kotlin.time.Duration

val KGraphQLPlugin = createApplicationPlugin("KGraphQL", ::KtorKGraphQLConfiguration) {
    val schema = KGraphQL.schema {
        configuration = pluginConfig
        pluginConfig.schemaBlock?.invoke(this)
    }

    val routing: Routing.() -> Unit = {
        val routing: Route.() -> Unit = {
            route(this@createApplicationPlugin.pluginConfig.endpoint) {
                post {
                    val bodyAsText = call.receiveTextWithCorrectEncoding()
                    val request = Json.decodeFromString(GraphqlRequest.serializer(), bodyAsText)
                    val ctx = context {
                        this@createApplicationPlugin.pluginConfig.contextSetup?.invoke(this, call)
                    }
                    try {
                        val (operationInfo, duration, result) = schema.execute(request.query, request.variables.toString(), ctx)

                        this@createApplicationPlugin.pluginConfig.metricsBlock?.onResult(operationInfo, duration, result)
                        call.respondText(result, contentType = ContentType.Application.Json)
                    } catch (e: Exception) {
                        if (e is GraphQLError) {
                            context.respondText(
                                    contentType = ContentType.Application.Json,
                                    status = HttpStatusCode.OK,
                                    text = e.serialize(),
                            )
                        } else throw e
                    }
                }
                if (this@createApplicationPlugin.pluginConfig.playground) get {
                    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    val playgroundHtml = KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html").readBytes()
                    call.respondBytes(playgroundHtml, contentType = ContentType.Text.Html)
                }
            }
        }

        this@createApplicationPlugin.pluginConfig.wrapWith?.invoke(this, routing) ?: routing(this)
    }

    application.routing(routing)
}

fun interface GraphQLMetrics {
    fun onResult(operationInfo: OperationDefinitionNode, duration: Duration, result: String)
}

class KtorKGraphQLConfiguration : SchemaConfigurationDSL() {
    fun schema(block: SchemaBuilder.() -> Unit) {
        schemaBlock = block
    }

    /**
     * This adds support for opening the graphql route within the browser
     */
    var playground: Boolean = false

    var endpoint: String = "/graphql"

    fun context(block: ContextBuilder.(ApplicationCall) -> Unit) {
        contextSetup = block
    }

    fun wrap(block: Route.(next: Route.() -> Unit) -> Unit) {
        wrapWith = block
    }

    fun metrics(block: GraphQLMetrics) {
        metricsBlock = block
    }

    internal var contextSetup: (ContextBuilder.(ApplicationCall) -> Unit)? = null
    internal var wrapWith: (Route.(next: Route.() -> Unit) -> Unit)? = null
    internal var schemaBlock: (SchemaBuilder.() -> Unit)? = null
    internal var metricsBlock: GraphQLMetrics? = null

}

private suspend fun ApplicationCall.receiveTextWithCorrectEncoding(): String {
    fun ContentType.defaultCharset(): Charset = when (this) {
        ContentType.Application.Json -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    val contentType = request.contentType()
    val suitableCharset = contentType.charset() ?: contentType.defaultCharset()
    return withContext(Dispatchers.IO) {
        receiveStream().bufferedReader(charset = suitableCharset).readText()
    }
}

private fun GraphQLError.serialize(): String = buildJsonObject {
    put("error", buildJsonObject {
        put("message", message)
        locations?.map {
            buildJsonObject {
                put("line", it.line)
                put("column", it.column)
            }
        }?.let { put("locations", JsonArray(it)) }
    })
}.toString()