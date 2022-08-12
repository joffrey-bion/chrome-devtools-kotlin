package org.hildan.chrome.devtools.domains.runtime

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private val json = Json { ignoreUnknownKeys = true }

/**
 * Evaluates the given [js] expression, and returns the result as a value of type [T].
 *
 * The value is converted from JSON using Kotlinx Serialization, so the type [T] must be
 * [@Serializable][kotlinx.serialization.Serializable].
 */
suspend inline fun <reified T> RuntimeDomain.evaluateJs(js: String): T? = evaluateJs(js, serializer())

/**
 * Evaluates the given [js] expression, and returns the result as a value of type [T] using the provided [deserializer].
 *
 * The value is converted from JSON using Kotlinx Serialization, so the type [T] must be
 * [@Serializable][kotlinx.serialization.Serializable].
 */
suspend fun <T> RuntimeDomain.evaluateJs(js: String, deserializer: DeserializationStrategy<T>): T? {
    val response = evaluate(js) { returnByValue = true }
    if (response.exceptionDetails != null) {
        throw RuntimeJSEvaluationException(js, response.exceptionDetails)
    }
    if (response.result.value == null) {
        return null
    }
    return json.decodeFromJsonElement(deserializer, response.result.value)
}

/**
 * Thrown when the evaluation of some JS expression went wrong.
 */
class RuntimeJSEvaluationException(
    /** The expression that failed to evaluate. */
    val jsExpression: String,
    /** The details of the evaluation error. */
    val details: ExceptionDetails,
): Exception(details.exception?.description ?: details.stackTrace?.description ?: details.text)
