package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent

fun ChromeDPDomain.createEventSealedClass(): TypeSpec = TypeSpec.classBuilder(eventsParentClassName).apply {
    addAnnotation(Annotations.serializable)
    addModifiers(KModifier.SEALED)
    events.forEach {
        addType(it.createEventSubTypeSpec(eventsParentClassName))
    }
}.build()

private fun ChromeDPEvent.createEventSubTypeSpec(parentSealedClass: ClassName): TypeSpec = if (parameters.isEmpty()) {
    TypeSpec.objectBuilder(eventTypeName).apply {
        configureCommonSettings(this@createEventSubTypeSpec, parentSealedClass)
    }.build()
} else {
    TypeSpec.classBuilder(eventTypeName).apply {
        configureCommonSettings(this@createEventSubTypeSpec, parentSealedClass)
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)
    }.build()
}

private fun TypeSpec.Builder.configureCommonSettings(chromeDPEvent: ChromeDPEvent, parentSealedClass: ClassName) {
    chromeDPEvent.description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDocSentence(chromeDPEvent.docUrl))
    superclass(parentSealedClass)
    if (chromeDPEvent.deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (chromeDPEvent.experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    addAnnotation(Annotations.serializable)
}
