package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hildan.chrome.devtools.build.model.ChromeDPCommand
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent
import org.hildan.chrome.devtools.build.model.asClassName
import kotlinx.serialization.DeserializationStrategy

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

fun ChromeDPCommand.createInputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(inputTypeName ?: error("trying to build input type for no-arg command")).apply {
        addKdoc("Request object containing input parameters for the [%T.%N] command.", domainName.asClassName(), name)
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

fun ChromeDPCommand.createOutputTypeSpec(): TypeSpec =
    TypeSpec.classBuilder(outputTypeName).apply {
        addKdoc("Response type for the [%T.%N] command.", domainName.asClassName(), name)
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

fun ChromeDPDomain.createDomainClass(): TypeSpec = TypeSpec.classBuilder(name.asClassName()).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDocSentence(docUrl))
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

private fun ChromeDPCommand.toFunctionSpec(): FunSpec = FunSpec.builder(name).apply {
    description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDocSentence(docUrl))
    if (deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    addModifiers(KModifier.SUSPEND)
    val inputArg = if (inputTypeName != null) {
        addParameter(INPUT_ARG, inputTypeName)
        INPUT_ARG
    } else {
        "Unit"
    }
    returns(outputTypeName)
    addStatement("return %N.request(%S, %L)", SESSION_ARG, "$domainName.$name", inputArg)
}.build()

private fun ChromeDPEvent.toSubscribeFunctionSpec(): FunSpec =
    FunSpec.builder(name).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        addKdoc(linkToDocSentence(docUrl))
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        returns(coroutineFlowClass.parameterizedBy(eventTypeName))
        addStatement("return %N.events(%S)", SESSION_ARG, "$domainName.$name")
    }.build()

private fun TypeSpec.Builder.addAllEventsFunction(domain: ChromeDPDomain) {
    addProperty(PropertySpec.builder(DESERIALIZERS_PROP, mapOfDeserializers(domain.eventsParentClassName))
        .addKdoc("Mapping between events and their deserializer.")
        .addModifiers(KModifier.PRIVATE)
        .initializer(domain.deserializersMapCodeBlock())
        .build())
    addFunction(FunSpec.builder("events")
        .addKdoc("Subscribes to all events related to this domain.")
        .returns(coroutineFlowClass.parameterizedBy(domain.eventsParentClassName))
        .addCode("return %N.events(%N)", SESSION_ARG, DESERIALIZERS_PROP)
        .build())
}

private fun ChromeDPDomain.deserializersMapCodeBlock(): CodeBlock = CodeBlock.builder().apply {
    add("mapOf(\n")
    events.forEach { e ->
        add("%S to %M<%T>(),\n", "${e.domainName}.${e.name}", serializerFun, e.eventTypeName)
    }
    add(")")
}.build()
