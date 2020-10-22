package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPParameter

fun TypeSpec.Builder.addPrimaryConstructorProps(props: List<ChromeDPParameter>) {
    primaryConstructor(FunSpec.constructorBuilder().addParameters(props.map { it.toParameterSpec() }).build())
    addProperties(props.map { it.toPropertySpec() })
}

private fun ChromeDPParameter.toParameterSpec(): ParameterSpec =
    ParameterSpec.builder(name, getTypeName(ExternalDeclarations.rootPackageName)).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        // We don't handle deprecated/experimental here as it's already added on the property declaration
        // Since both the property and the constructor arg are the same declaration, it would result in double
        // annotations

        // we make experimental fields nullable too because they might not be present in the JSON
        if (optional || experimental) {
            defaultValue("null")
        }
    }.build()

private fun ChromeDPParameter.toPropertySpec(): PropertySpec =
    PropertySpec.builder(name, getTypeName(ExternalDeclarations.rootPackageName)).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        if (deprecated) {
            addAnnotation(ExternalDeclarations.deprecatedAnnotation)
        }
        if (experimental) {
            addAnnotation(ExternalDeclarations.experimentalAnnotation)
        }
        mutable(false)
        initializer(name) // necessary to merge primary constructor arguments and properties
    }.build()

private fun ChromeDPParameter.getTypeName(rootPackageName: String): TypeName {
    val typeName = type.toTypeName(rootPackageName)
    // we make experimental fields nullable too because they might not be present in the JSON
    return if (optional || experimental) typeName.copy(nullable = true) else typeName
}
