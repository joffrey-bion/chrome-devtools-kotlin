package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hildan.chrome.devtools.build.model.ChromeDPCommand
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent
import kotlinx.serialization.DeserializationStrategy
import org.hildan.chrome.devtools.build.names.Annotations
import org.hildan.chrome.devtools.build.names.ExtClasses

private const val INPUT_ARG = "input"
private const val SESSION_ARG = "session"
private const val DESERIALIZERS_PROP = "deserializersByEventName"

private val deserializerClassName = DeserializationStrategy::class.asClassName()
private val serializerFun = MemberName("kotlinx.serialization", "serializer")
private val coroutineFlowClass = ClassName("kotlinx.coroutines.flow", "Flow")

private fun mapOfDeserializers(eventsSealedClassName: ClassName): ParameterizedTypeName {
    val deserializerClass = deserializerClassName.parameterizedBy(WildcardTypeName.producerOf(eventsSealedClassName))
    return MAP.parameterizedBy(String::class.asTypeName(), deserializerClass)
}

fun ChromeDPDomain.createDomainFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.packageName, fileName = names.filename).apply {
        addAnnotation(Annotations.suppressWarnings)
        commands.forEach { cmd ->
            if (cmd.parameters.isNotEmpty()) {
                addType(cmd.createInputTypeSpec())
            }
            if (cmd.returns.isNotEmpty()) {
                addType(cmd.createOutputTypeSpec())
            }
        }
        addType(createDomainClass())
    }.build()

private fun ChromeDPCommand.createInputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(names.inputTypeName).apply {
        addKdoc("Request object containing input parameters for the [%T.%N] command.",
            names.domain.domainClassName,
            names.methodName)
        addAnnotation(Annotations.serializable)
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)
    }.build()

private fun ChromeDPCommand.createOutputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(names.outputTypeName).apply {
        addKdoc("Response type for the [%T.%N] command.", names.domain.domainClassName, names.methodName)
        addAnnotation(Annotations.serializable)
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(returns)
    }.build()

private fun ChromeDPDomain.createDomainClass(): TypeSpec = TypeSpec.classBuilder(names.domainClassName).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDoc(docUrl))
    if (deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    primaryConstructor(FunSpec.constructorBuilder()
        .addModifiers(KModifier.INTERNAL)
        .addParameter(SESSION_ARG, ExtClasses.chromeDPSession)
        .build())
    addProperty(PropertySpec.builder(SESSION_ARG, ExtClasses.chromeDPSession)
        .addModifiers(KModifier.PRIVATE)
        .initializer(SESSION_ARG)
        .build())
    if (events.isNotEmpty()) {
        addAllEventsFunction(this@createDomainClass)
        events.forEach { event ->
            addFunction(event.toSubscribeFunctionSpec())
        }
    }
    commands.forEach { cmd ->
        addFunction(cmd.toFunctionSpec())
    }
}.build()

private fun ChromeDPCommand.toFunctionSpec(): FunSpec = FunSpec.builder(names.methodName).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDoc(docUrl))
    if (deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    addModifiers(KModifier.SUSPEND)
    val inputArg = if (this@toFunctionSpec.parameters.isNotEmpty()) {
        addParameter(INPUT_ARG, names.inputTypeName)
        INPUT_ARG
    } else {
        "Unit"
    }
    val returnType = if (returns.isEmpty()) Unit::class.asTypeName() else names.outputTypeName
    returns(returnType)
    addStatement("return %N.request(%S, %L)", SESSION_ARG, names.fullCommandName, inputArg)
}.build()

private fun ChromeDPEvent.toSubscribeFunctionSpec(): FunSpec =
    FunSpec.builder(names.methodName).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        addKdoc(linkToDoc(docUrl))
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        returns(coroutineFlowClass.parameterizedBy(names.eventTypeName))
        addStatement("return %N.events(%S)", SESSION_ARG, names.fullEventName)
    }.build()

private fun TypeSpec.Builder.addAllEventsFunction(domain: ChromeDPDomain) {
    addProperty(PropertySpec.builder(DESERIALIZERS_PROP, mapOfDeserializers(domain.names.eventsParentClassName))
        .addKdoc("Mapping between events and their deserializer.")
        .addModifiers(KModifier.PRIVATE)
        .initializer(domain.deserializersMapCodeBlock())
        .build())
    addFunction(FunSpec.builder("events")
        .addKdoc("Subscribes to all events related to this domain.")
        .returns(coroutineFlowClass.parameterizedBy(domain.names.eventsParentClassName))
        .addCode("return %N.events(%N)", SESSION_ARG, DESERIALIZERS_PROP)
        .build())
}

private fun ChromeDPDomain.deserializersMapCodeBlock(): CodeBlock = CodeBlock.builder().apply {
    add("mapOf(\n")
    events.forEach { e ->
        add("%S to %M<%T>(),\n", e.names.fullEventName, serializerFun, e.names.eventTypeName)
    }
    add(")")
}.build()
