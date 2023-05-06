package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.DeserializationStrategy
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent
import org.hildan.chrome.devtools.build.names.Annotations
import org.hildan.chrome.devtools.build.names.ExtDeclarations

private const val SESSION_PROP = "session"
private const val DESERIALIZERS_PROP = "deserializersByEventName"

private val deserializerClassName = DeserializationStrategy::class.asClassName()
private val serializerFun = MemberName("kotlinx.serialization", "serializer")
private val coroutineFlowClass = ClassName("kotlinx.coroutines.flow", "Flow")

private fun mapOfDeserializers(eventsSealedClassName: ClassName): ParameterizedTypeName {
    val deserializerClass = deserializerClassName.parameterizedBy(eventsSealedClassName)
    return MAP.parameterizedBy(String::class.asTypeName(), deserializerClass)
}

fun ChromeDPDomain.createDomainFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.packageName, fileName = names.filename).apply {
        addAnnotation(Annotations.suppressWarnings)
        commands.forEach { cmd ->
            // We don't need to create the input type all the time for backwards compatiblity, because it never
            // happened that all parameters of a command were removed. Therefore, we will never "drop" an exiting
            // input type. Note that when we generate methods with such input type, we also always have an overload
            // without it (unless there are now mandatory parameters of course), so this ensures compatibility in case
            // the command didn't have any parameters in the past and only has optional ones now.
            if (cmd.parameters.isNotEmpty()) {
                addType(cmd.createInputTypeSpec())
            }
            // we always create the output type for forwards/backwards binary compatibility of command methods
            addType(cmd.createOutputTypeSpec())
        }
        addType(createDomainClass())
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
        .addParameter(SESSION_PROP, ExtDeclarations.chromeDPSession)
        .build())
    addProperty(PropertySpec.builder(SESSION_PROP, ExtDeclarations.chromeDPSession)
        .addModifiers(KModifier.PRIVATE)
        .initializer(SESSION_PROP)
        .build())
    if (events.isNotEmpty()) {
        addAllEventsFunction(this@createDomainClass)
        events.forEach { event ->
            addFunction(event.toSubscribeFunctionSpec())
            addFunction(event.toLegacySubscribeFunctionSpec())
        }
    }
    commands.forEach { cmd ->
        if (cmd.parameters.isNotEmpty()) {
            addFunction(cmd.toFunctionSpecWithParams(SESSION_PROP))
            addFunction(cmd.toDslFunctionSpec())
        } else {
            addFunction(cmd.toNoArgFunctionSpec(SESSION_PROP))
        }
    }
}.build()

private fun ChromeDPEvent.toSubscribeFunctionSpec(): FunSpec =
    FunSpec.builder(names.flowMethodName).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        addKdoc(linkToDoc(docUrl))
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        returns(coroutineFlowClass.parameterizedBy(names.eventTypeName))
        addStatement("return %N.%M(%S)", SESSION_PROP, ExtDeclarations.sessionTypedEventsExtension, names.fullEventName)
    }.build()

private fun ChromeDPEvent.toLegacySubscribeFunctionSpec(): FunSpec =
    FunSpec.builder(names.legacyMethodName).apply {
        addAnnotation(AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Events subscription methods were renamed with the -Events suffix.\"")
            .addMember("replaceWith = ReplaceWith(\"%N()\")", names.flowMethodName)
            .build())
        returns(coroutineFlowClass.parameterizedBy(names.eventTypeName))
        addStatement("return %N()", names.flowMethodName)
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
        .addCode("return %N.%M(%N)", SESSION_PROP, ExtDeclarations.sessionTypedEventsExtension, DESERIALIZERS_PROP)
        .build())
}

private fun ChromeDPDomain.deserializersMapCodeBlock(): CodeBlock = CodeBlock.builder().apply {
    add("mapOf(\n")
    events.forEach { e ->
        add("%S to %M<%T>(),\n", e.names.fullEventName, serializerFun, e.names.eventTypeName)
    }
    add(")")
}.build()
