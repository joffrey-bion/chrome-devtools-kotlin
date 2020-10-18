package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPDomain

private const val CONNECTION_ARG = "connection"

fun createClientClass(domains: List<ChromeDPDomain>): TypeSpec = TypeSpec.classBuilder("ChromeDPClient").apply {
    addModifiers(KModifier.OPEN)
    primaryConstructor(FunSpec.constructorBuilder()
        .addModifiers(KModifier.INTERNAL)
        .addParameter(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
        .build())
    val connectionProp =
        PropertySpec.builder(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
            .initializer(CONNECTION_ARG)
            .build()
    addProperty(connectionProp)
    domains.forEach {
        addProperty(PropertySpec.builder(it.decapitalizedName, it.domainClassName)
            .delegate("lazy { %T(%N) }", it.domainClassName, connectionProp)
            .build())
    }
    addFunction(FunSpec.builder("close")
        .addModifiers(KModifier.SUSPEND, KModifier.OPEN)
        .addCode("%N.close()", connectionProp)
        .build())
}.build()
