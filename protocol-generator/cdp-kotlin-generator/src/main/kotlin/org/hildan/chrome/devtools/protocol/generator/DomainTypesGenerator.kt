package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import org.hildan.chrome.devtools.protocol.model.ChromeDPDomain
import org.hildan.chrome.devtools.protocol.model.ChromeDPType
import org.hildan.chrome.devtools.protocol.model.DomainTypeDeclaration
import org.hildan.chrome.devtools.protocol.names.Annotations
import org.hildan.chrome.devtools.protocol.names.ExtDeclarations
import org.hildan.chrome.devtools.protocol.names.UndefinedEnumEntryName

fun ChromeDPDomain.createDomainTypesFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.packageName, fileName = names.typesFilename).apply {
        addAnnotation(Annotations.suppressWarnings)
        types.forEach { addDomainType(it, experimentalDomain = experimental) }
    }.build()

private fun FileSpec.Builder.addDomainType(typeDeclaration: DomainTypeDeclaration, experimentalDomain: Boolean) {
    when (val type = typeDeclaration.type) {
        is ChromeDPType.Object -> addType(typeDeclaration.toDataClassTypeSpec(type))
        is ChromeDPType.Enum -> addType(typeDeclaration.toEnumTypeSpec(type, experimentalDomain))
        is ChromeDPType.NamedRef -> addTypeAlias(typeDeclaration.toTypeAliasSpec(type))
    }
}

private fun DomainTypeDeclaration.toDataClassTypeSpec(type: ChromeDPType.Object): TypeSpec =
    TypeSpec.classBuilder(names.className).apply {
        addModifiers(KModifier.DATA)
        addKDocAndStabilityAnnotations(element = this@toDataClassTypeSpec)
        addAnnotation(Annotations.serializable)
        addPrimaryConstructorProps(type.properties)
    }.build()

private fun DomainTypeDeclaration.toEnumTypeSpec(type: ChromeDPType.Enum, experimentalDomain: Boolean): TypeSpec =
    TypeSpec.enumBuilder(names.className).apply {
        addKDocAndStabilityAnnotations(element = this@toEnumTypeSpec)
        type.enumValues.forEach {
            addEnumConstant(
                name = it.dashesToCamelCase(),
                typeSpec = TypeSpec.anonymousClassBuilder().addAnnotation(Annotations.serialName(it)).build()
            )
        }
        if (experimental || experimentalDomain) {
            require(type.enumValues.none { it.equals(UndefinedEnumEntryName, ignoreCase = true) }) {
                "Cannot synthesize the '$UndefinedEnumEntryName' value for experimental enum " +
                    "${names.declaredName} (of domain ${names.domain.domainName}) because it clashes with an " +
                    "existing value (case-insensitive). Values:\n - ${type.enumValues.joinToString("\n - ")}"
            }
            addEnumConstant(
                name = UndefinedEnumEntryName,
                typeSpec = TypeSpec.anonymousClassBuilder()
                    .addKdoc("This extra enum entry represents values returned by Chrome that were not defined in " +
                                 "the protocol (for instance new values that were added later).")
                    .build(),
            )

            // see https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#extending-the-behavior-of-the-plugin-generated-serializer
            val enumSerializer = serializerForEnumWithUnknown(names.className, type.enumValues)
            addType(enumSerializer)

            val serializerClass = names.className.nestedClass(enumSerializer.name!!)
            addAnnotation(Annotations.serializableWith(serializerClass))
            addAnnotation(Annotations.keepGeneratedSerializer)
        } else {
            addAnnotation(Annotations.serializable)
        }
    }.build()

private fun String.dashesToCamelCase(): String = replace(Regex("""-(\w)""")) { it.groupValues[1].uppercase() }

private fun serializerForEnumWithUnknown(enumClass: ClassName, enumValues: List<String>): TypeSpec =
    TypeSpec.objectBuilder("SerializerWithUnknown").apply {
        addModifiers(KModifier.INTERNAL)
        superclass(JsonTransformingSerializer::class.asClassName().parameterizedBy(enumClass))
        addSuperclassConstructorParameter("%T.generatedSerializer()", enumClass)
        addProperty(knownValuesProperty(enumValues))
        addFunction(FunSpec.builder("transformDeserialize").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("element", JsonElement::class)
            returns(JsonElement::class)
            addStatement("val jsonValue = element.%M.content", ExtDeclarations.Serialization.jsonPrimitiveExtension)
            addStatement(
                format = "return if (jsonValue in knownValues) element else %M(%S)",
                ExtDeclarations.Serialization.JsonPrimitiveFactory,
                UndefinedEnumEntryName,
            )
        }.build())
        addFunction(FunSpec.builder("transformSerialize").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("element", JsonElement::class)
            returns(JsonElement::class)
            addStatement("val jsonValue = element.%M.content", ExtDeclarations.Serialization.jsonPrimitiveExtension)
            addStatement(
                format = "require(jsonValue in knownValues) { %S }",
                "Cannot serialize the '$UndefinedEnumEntryName' enum value placeholder for $enumClass. " +
                    "Please use use one of the following valid values instead: $enumValues",
            )
            addStatement("return element")
        }.build())
    }.build()

private fun knownValuesProperty(values: List<String>): PropertySpec =
    PropertySpec.builder("knownValues", SET.parameterizedBy(String::class.asTypeName())).apply {
        addModifiers(KModifier.INTERNAL)
        val format = List(values.size) { "%S" }.joinToString(separator = ", ", prefix = "setOf(", postfix = ")")
        initializer(format = format, *values.toTypedArray())
    }.build()

private fun DomainTypeDeclaration.toTypeAliasSpec(type: ChromeDPType.NamedRef): TypeAliasSpec =
    TypeAliasSpec.builder(names.declaredName, type.typeName).apply {
        addKDocAndStabilityAnnotations(element = this@toTypeAliasSpec)
    }.build()
