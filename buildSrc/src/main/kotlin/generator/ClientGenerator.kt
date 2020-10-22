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
    val connectionProp =
        PropertySpec.builder(SESSION_ARG, ExternalDeclarations.chromeSessionClass)
            .initializer(SESSION_ARG)
            .build()
    addProperty(connectionProp)
    domains.forEach {
        addProperty(PropertySpec.builder(it.name.asVariableName(), it.name.asClassName())
            .delegate("lazy { %T(%N) }", it.name.asClassName(), connectionProp)
            .build())
    }
    addFunction(FunSpec.builder("close")
        .addModifiers(KModifier.SUSPEND, KModifier.OPEN)
        .addCode("%N.close()", connectionProp)
        .build())
}.build()
