package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPCommand
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent

private const val INPUT_ARG = "input"

private const val CONNECTION_ARG = "connection"

private val coroutineFlowClass = ClassName("kotlinx.coroutines.flow", "Flow")

fun ChromeDPCommand.createInputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(inputTypeName).apply {
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)
    }.build()

fun ChromeDPCommand.createOutputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(outputTypeName).apply {
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(returns)
    }.build()

fun ChromeDPDomain.createDomainClass(): TypeSpec = TypeSpec.classBuilder(domainClassName).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    primaryConstructor(FunSpec.constructorBuilder()
        .addModifiers(KModifier.INTERNAL)
        .addParameter(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
        .build())
    addProperty(PropertySpec.builder(CONNECTION_ARG, ExternalDeclarations.chromeConnectionClass)
        .addModifiers(KModifier.PRIVATE)
        .initializer(CONNECTION_ARG)
        .build())
    commands.forEach { cmd ->
        addFunction(cmd.createFunctionSpec(packageName))
    }
    events.forEach { event ->
        addFunction(event.createFunctionSpec(eventsSealedClassName))
    }
}.build()

private fun ChromeDPCommand.createFunctionSpec(domainPackage: String): FunSpec = FunSpec.builder(name).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    if (deprecated) {
        addAnnotation(ExternalDeclarations.deprecatedAnnotation)
    }
    if (experimental) {
        addAnnotation(ExternalDeclarations.experimentalAnnotation)
    }
    addModifiers(KModifier.SUSPEND)
    val inputArg = if (this@createFunctionSpec.parameters.isNotEmpty()) {
        addParameter(INPUT_ARG, ClassName(domainPackage, inputTypeName))
        INPUT_ARG
    } else {
        "null"
    }
    if (returns.isNotEmpty()) {
        returns(ClassName(domainPackage, outputTypeName))
        addStatement("""return connection.requestForResult("$domainName.$name", %L)""", inputArg)
    } else {
        addStatement("""connection.request("$domainName.$name", %L)""", inputArg)
    }
}.build()

private fun ChromeDPEvent.createFunctionSpec(eventsSealedClassName: ClassName): FunSpec =
    FunSpec.builder(name).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        if (deprecated) {
            addAnnotation(ExternalDeclarations.deprecatedAnnotation)
        }
        if (experimental) {
            addAnnotation(ExternalDeclarations.experimentalAnnotation)
        }
        returns(coroutineFlowClass.parameterizedBy(eventsSealedClassName.nestedClass(eventTypeName)))
        addStatement("""return connection.events(%S)""", "$domainName.$name")
    }.build()
