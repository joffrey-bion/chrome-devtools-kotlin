package org.hildan.chrome.devtools.domains.runtime

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private val json = Json { ignoreUnknownKeys = true }

suspend inline fun <reified T> RuntimeDomain.evaluateJs(js: String): T? = evaluateJs(js, serializer())

suspend fun <T> RuntimeDomain.evaluateJs(js: String, deserializer: DeserializationStrategy<T>): T? {
    val response = evaluate(EvaluateRequest(js, returnByValue = true))
    if (response.exceptionDetails != null) {
        throw RuntimeJSEvaluationException(js, response.exceptionDetails)
    }
    if (response.result.value == null) {
        return null
    }
    return json.decodeFromJsonElement(deserializer, response.result.value)
}

class RuntimeJSEvaluationException(
    val jsExpression: String,
    val details: ExceptionDetails,
): Exception(details.exception?.description ?: details.stackTrace?.description ?: details.text)