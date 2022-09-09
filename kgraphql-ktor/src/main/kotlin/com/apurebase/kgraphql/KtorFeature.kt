package com.apurebase.kgraphql

import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.apurebase.kgraphql.schema.execution.ExecutionPlan
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.Charset

class GraphQL(val schema: Schema) {

    class Configuration : SchemaConfigurationDSL() {
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

        fun metrics(block: (plan: ExecutionPlan, time: Long, result: String?) -> Unit) {
            metricsBlock = block
        }

        internal var contextSetup: (ContextBuilder.(ApplicationCall) -> Unit)? = null
        internal var wrapWith: (Route.(next: Route.() -> Unit) -> Unit)? = null
        internal var schemaBlock: (SchemaBuilder.() -> Unit)? = null
        internal var metricsBlock: ((plan: ExecutionPlan, time: Long, result: String?) -> Unit)? = null

    }


    companion object Feature : BaseApplicationPlugin<Application, Configuration, GraphQL> {
        override val key = AttributeKey<GraphQL>("KGraphQL")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): GraphQL {
            val config = Configuration().apply(configure)
            val schema = KGraphQL.schema {
                configuration = config
                config.schemaBlock?.invoke(this)
            }

            val routing: Routing.() -> Unit = {
                val routing: Route.() -> Unit = {
                    route(config.endpoint) {
                        post {
                            val bodyAsText = call.receiveTextWithCorrectEncoding()
                            val request = decodeFromString(GraphqlRequest.serializer(), bodyAsText)
                            val ctx = context {
                                config.contextSetup?.invoke(this, call)
                            }
                            try {
                                val (plan, time, result) = schema.execute(request.query, request.variables.toString(), ctx)

                                config.metricsBlock?.invoke(plan, time, result)
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
                        if (config.playground) get {
                            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            val playgroundHtml = KtorGraphQLConfiguration::class.java.classLoader.getResource("playground.html").readBytes()
                            call.respondBytes(playgroundHtml, contentType = ContentType.Text.Html)
                        }
                    }
                }

                config.wrapWith?.invoke(this, routing) ?: routing(this)
            }

            pipeline.pluginOrNull(Routing)?.apply(routing) ?: pipeline.install(Routing, routing)
            return GraphQL(schema)
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
            put("error", buildJsonArray {
                addJsonObject {
                    put("message", message)
                    put("locations", buildJsonArray {
                        locations?.forEach {
                            addJsonObject {
                                put("line", it.line)
                                put("column", it.column)
                            }
                        }
                    })
                }
            })
        }.toString()
    }

}
