package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.asClassName
import org.hildan.chrome.devtools.build.model.asVariableName

private const val SESSION_ARG = "session"

fun createClientClass(domains: List<ChromeDPDomain>): TypeSpec = TypeSpec.classBuilder("ChromeApi").apply {
    addModifiers(KModifier.OPEN)
    primaryConstructor(FunSpec.constructorBuilder()
        .addModifiers(KModifier.INTERNAL)
        .addParameter(SESSION_ARG, ExternalDeclarations.chromeSessionClass)
        .build())
    addProperty(PropertySpec.builder(SESSION_ARG, ExternalDeclarations.chromeSessionClass)
        .initializer(SESSION_ARG)
        .build())
    domains.forEach {
        addProperty(it.toPropertySpec())
    }
}.build()

private fun ChromeDPDomain.toPropertySpec(): PropertySpec =
    PropertySpec.builder(name.asVariableName(), name.asClassName()).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        delegate("lazy { %T(%N) }", name.asClassName(), SESSION_ARG)
    }.build()
