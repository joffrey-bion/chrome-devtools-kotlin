package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.TypeName

fun String.escapeKDoc(): String = replace("%", "%%")

fun linkToDoc(docUrl: String) = "\n\n[Official\u00A0doc]($docUrl)"

internal data class ConstructorCallTemplate(
    val template: String,
    val namedArgsMapping: Map<String, *>,
)

internal fun constructorCallTemplate(constructedTypeName: TypeName, paramNames: List<String>): ConstructorCallTemplate {
    val constructedTypeArgName = "cdk_constructedTypeName"
    val commandArgsTemplate = paramNames.joinToString(", ") { "%$it:L" }
    val commandArgsMapping = paramNames.associate { it to it }
    check(constructedTypeArgName !in commandArgsMapping) {
        "Unlucky state! An argument for constructor $constructedTypeName has exactly the name $constructedTypeArgName"
    }
    val argsMapping = mapOf(constructedTypeArgName to constructedTypeName) + commandArgsMapping
    return ConstructorCallTemplate(
        template = "%$constructedTypeArgName:T($commandArgsTemplate)",
        namedArgsMapping = argsMapping,
    )
}
