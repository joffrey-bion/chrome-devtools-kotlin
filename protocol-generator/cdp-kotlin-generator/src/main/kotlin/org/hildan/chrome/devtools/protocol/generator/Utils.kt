package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.protocol.model.*
import org.hildan.chrome.devtools.protocol.names.*

fun <T> T.addKDocAndStabilityAnnotations(element: ChromeDPElement) where T : Annotatable.Builder<*>, T : Documentable.Builder<*> {
    addKdoc(element.description?.escapeForKotlinPoet() ?: "*(undocumented in the protocol definition)*")
    element.docUrl?.let {
        addKdoc("\n\n[Official\u00A0doc]($it)".escapeForKotlinPoet())
    }
    if (element.deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (element.experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
}

// KotlinPoet interprets % signs as format elements, and we don't use this for docs generated from descriptions
private fun String.escapeForKotlinPoet(): String = replace("%", "%%")

internal data class ConstructorCallTemplate(
    val template: String,
    val namedArgsMapping: Map<String, *>,
)

internal fun constructorCallTemplate(constructedTypeName: TypeName, paramNames: List<String>): ConstructorCallTemplate {
    val constructedTypeArgName = "cdk_constructedTypeName"
    val commandArgsTemplate = paramNames.joinToString(", ") { "%$it:L" }
    val commandArgsMapping = paramNames.associateWith { it }
    check(constructedTypeArgName !in commandArgsMapping) {
        "Unlucky state! An argument for constructor $constructedTypeName has exactly the name $constructedTypeArgName"
    }
    val argsMapping = mapOf(constructedTypeArgName to constructedTypeName) + commandArgsMapping
    return ConstructorCallTemplate(
        template = "%$constructedTypeArgName:T($commandArgsTemplate)",
        namedArgsMapping = argsMapping,
    )
}
