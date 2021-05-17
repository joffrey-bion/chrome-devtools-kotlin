package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPParameter
import org.hildan.chrome.devtools.build.names.Annotations

fun TypeSpec.Builder.addPrimaryConstructorProps(props: List<ChromeDPParameter>) {
    primaryConstructor(FunSpec.constructorBuilder().addParameters(props.map { it.toParameterSpec() }).build())
    addProperties(props.map { it.toPropertySpec() })
}

private fun ChromeDPParameter.toParameterSpec(): ParameterSpec =
    ParameterSpec.builder(name, type).apply {
        // No need to add KDoc to the constructor param, adding it to the property is sufficient

        // We don't add deprecated/experimental annotations here as they are already added on the property declaration.
        // Since both the property and the constructor arg are the same declaration, it would result in double
        // annotations.

        if (type.isNullable) {
            defaultValue("null")
        }
    }.build()

private fun ChromeDPParameter.toPropertySpec(): PropertySpec =
    PropertySpec.builder(name, type).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        mutable(false)
        initializer(name) // necessary to merge primary constructor arguments and properties
    }.build()
