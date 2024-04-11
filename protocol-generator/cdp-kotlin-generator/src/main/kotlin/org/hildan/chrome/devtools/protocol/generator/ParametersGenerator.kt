package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.protocol.model.ChromeDPParameter

internal fun TypeSpec.Builder.addPrimaryConstructorProps(props: List<ChromeDPParameter>) {
    val parameterSpecs = props.map {
        it.toParameterSpec(
            // No need to add KDoc to the constructor param, adding it to the property is sufficient.
            // We don't add deprecated/experimental annotations here as they are already added on the property declaration.
            // Since both the property and the constructor arg are the same declaration, it would result in double
            // annotations.
            includeDocAndAnnotations = false,
        )
    }
    val propertySpecs = props.map {
        it.toPropertySpec {
            initializer(it.name) // necessary to merge primary constructor arguments and properties
        }
    }
    primaryConstructor(FunSpec.constructorBuilder().addParameters(parameterSpecs).build())
    addProperties(propertySpecs)
}

internal fun lambdaTypeWithBuilderReceiver(builderTypeName: TypeName) = LambdaTypeName.get(
    receiver = builderTypeName,
    parameters = emptyList(),
    returnType = Unit::class.asTypeName(),
)

internal fun ChromeDPParameter.toParameterSpec(includeDocAndAnnotations: Boolean = true): ParameterSpec =
    ParameterSpec.builder(name, type).apply {
        if (includeDocAndAnnotations) {
            addKDocAndStabilityAnnotations(this@toParameterSpec)
        }

        if (type.isNullable) {
            defaultValue("null")
        }
    }.build()

internal fun ChromeDPParameter.toPropertySpec(configure: PropertySpec.Builder.() -> Unit = {}): PropertySpec =
    PropertySpec.builder(name, type).apply {
        addKDocAndStabilityAnnotations(this@toPropertySpec)
        configure()
    }.build()
