package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPEvent

fun ChromeDPDomain.createEventSealedClass(): TypeSpec = TypeSpec.classBuilder(eventsParentClassName).apply {
    addModifiers(KModifier.SEALED)
    events.forEach {
        addType(it.createEventSubTypeSpec(eventsParentClassName))
    }
}.build()

private fun ChromeDPEvent.createEventSubTypeSpec(parentSealedClass: ClassName): TypeSpec = if (parameters.isEmpty()) {
    TypeSpec.objectBuilder(eventTypeName).superclass(parentSealedClass).build()
} else {
    TypeSpec.classBuilder(eventTypeName).apply {
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)
        superclass(parentSealedClass)
    }.build()
}
