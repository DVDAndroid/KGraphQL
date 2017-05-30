package com.github.pgutkowski.kgraphql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.pgutkowski.kgraphql.schema.Schema
import com.github.pgutkowski.kgraphql.schema.dsl.SchemaBuilder
import com.github.pgutkowski.kgraphql.schema.DefaultSchema
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*

val objectMapper = jacksonObjectMapper()

fun deserialize(json: String) : Map<*,*> {
    return objectMapper.readValue(json, HashMap::class.java)
}

fun getMap(map : Map<*,*>, key : String) : Map<*,*>{
    return map[key] as Map<*,*>
}

@Suppress("UNCHECKED_CAST")
fun <T>extract(map: Map<*,*>, path : String) : T {
    val tokens = path.trim().split('/').filter(String::isNotBlank)
    try {
        return tokens.fold(map as Any?, { workingMap, token ->
            if(token.contains('[')){
                val list = (workingMap as Map<*,*>)[token.substringBefore('[')]
                val index = token[token.indexOf('[')+1].toString().toInt()
                (list as List<*>)[index]
            } else {
                (workingMap as Map<*,*>)[token]
            }
        }) as T
    } catch (e : Exception){
        throw IllegalArgumentException("Path: $path does not exist in map: $map", e)
    }
}

fun defaultSchema(block: SchemaBuilder.() -> Unit): DefaultSchema {
    return SchemaBuilder(block).build() as DefaultSchema
}

fun assertNoErrors(map : Map<*,*>) {
    if(map["errors"] != null) throw AssertionError("Errors encountered: ${map["errors"]}")
    if(map["data"] == null) throw AssertionError("Data is null")
}

fun assertError(map : Map<*,*>, vararg messageElements : String) {
    val errorMessage = extract<String>(map, "errors/message")
    MatcherAssert.assertThat(errorMessage, CoreMatchers.notNullValue())

    messageElements
            .filterNot { errorMessage.contains(it) }
            .forEach { throw AssertionError("Expected error message to contain $it, but was: $errorMessage") }
}

inline fun <reified T: Exception> expect(message: String, block: () -> Unit){
    try {
        block()
    } catch (e : Exception){
        assertThat(e, instanceOf(T::class.java))
        assertThat(e.message, containsString(message))
    }
}

fun executeEqualQueries(schema: Schema, expected: Map<*,*>, vararg queries : String){
    queries.map { request ->
        deserialize(schema.execute(request))
    }.forEach { map ->
        MatcherAssert.assertThat(map, CoreMatchers.equalTo(expected))
    }
}


