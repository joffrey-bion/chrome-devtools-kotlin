package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.protocol.model.ChromeDPDomain
import org.hildan.chrome.devtools.protocol.model.ChromeDPEvent
import org.hildan.chrome.devtools.protocol.names.Annotations

fun ChromeDPDomain.createDomainEventTypesFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.eventsPackageName, fileName = names.eventsFilename).apply {
        addAnnotation(Annotations.suppressWarnings)
        addType(createEventSealedClass())
    }.build()

private fun ChromeDPDomain.createEventSealedClass(): TypeSpec =
    TypeSpec.classBuilder(names.eventsParentClassName)
        .apply {
        addAnnotation(Annotations.serializable)
        addModifiers(KModifier.SEALED)
        events.forEach {
            addType(it.createEventSubTypeSpec(names.eventsParentClassName))
        }
    }.build()

private fun ChromeDPEvent.createEventSubTypeSpec(parentSealedClass: ClassName): TypeSpec = if (parameters.isEmpty()) {
    TypeSpec.objectBuilder(names.eventTypeName).apply {
        configureCommonSettings(this@createEventSubTypeSpec, parentSealedClass)
    }.build()
} else {
    TypeSpec.classBuilder(names.eventTypeName).apply {
        configureCommonSettings(this@createEventSubTypeSpec, parentSealedClass)
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)
    }.build()
}

private fun TypeSpec.Builder.configureCommonSettings(chromeDPEvent: ChromeDPEvent, parentSealedClass: ClassName) {
    addKDocAndStabilityAnnotations(chromeDPEvent)
    superclass(parentSealedClass)
    addAnnotation(Annotations.serializable)
}
