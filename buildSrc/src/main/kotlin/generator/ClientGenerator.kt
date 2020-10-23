package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.asClassName
import org.hildan.chrome.devtools.build.model.asVariableName

private const val CONNECTION_ARG = "connection"
private const val PARENT_ARG = "parent"
private const val SESSION_ARG = "sessionId"
private const val TARGET_ARG = "targetId"
private const val CONTEXT_ARG = "browserContextId"

fun createBrowserSessionClass(domains: List<ChromeDPDomain>, name: String): TypeSpec = TypeSpec.classBuilder(name).apply {
    superclass(ExternalDeclarations.chromeSessionClass)
    addSuperclassConstructorParameter(CONNECTION_ARG)
    addSuperclassConstructorParameter("null")

    primaryConstructor(FunSpec.constructorBuilder().apply {
        addModifiers(KModifier.INTERNAL)
        addParameter(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
    }.build())

    domains.forEach {
        addProperty(it.toPropertySpec())
    }
}.build()

fun createTargetSessionClass(domains: List<ChromeDPDomain>, name: String): TypeSpec = TypeSpec.classBuilder(name).apply {
    superclass(ExternalDeclarations.chromeSessionClass)
    addSuperclassConstructorParameter(CONNECTION_ARG)
    addSuperclassConstructorParameter(SESSION_ARG)

    primaryConstructor(FunSpec.constructorBuilder().apply {
        addModifiers(KModifier.INTERNAL)
        addParameter(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
        addParameter(SESSION_ARG, ExternalDeclarations.sessionIdClass)
        addParameter(PARENT_ARG, ExternalDeclarations.browserSessionClass)
        addParameter(TARGET_ARG, ExternalDeclarations.targetIdClass)
        addParameter(ParameterSpec.builder(CONTEXT_ARG, ExternalDeclarations.browserContextIdClass.copy(nullable = true))
            .defaultValue("null")
            .build())
    }.build())

    addProperty(PropertySpec.builder(PARENT_ARG, ExternalDeclarations.browserSessionClass)
        .initializer(PARENT_ARG)
        .build())
    addProperty(PropertySpec.builder(TARGET_ARG, ExternalDeclarations.targetIdClass)
        .initializer(TARGET_ARG)
        .build())
    addProperty(PropertySpec.builder(CONTEXT_ARG, ExternalDeclarations.browserContextIdClass.copy(nullable = true))
        .initializer(CONTEXT_ARG)
        .build())

    domains.forEach {
        addProperty(it.toPropertySpec())
    }
}.build()

private fun ChromeDPDomain.toPropertySpec(): PropertySpec =
    PropertySpec.builder(name.asVariableName(), name.asClassName()).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        delegate("lazy { %T(this) }", name.asClassName())
    }.build()
