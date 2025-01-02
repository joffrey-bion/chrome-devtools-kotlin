package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
        is ChromeDPType.Enum -> addTypes(typeDeclaration.toEnumAndSerializerTypeSpecs(type, experimentalDomain))
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

private fun DomainTypeDeclaration.toEnumAndSerializerTypeSpecs(type: ChromeDPType.Enum, experimentalDomain: Boolean): List<TypeSpec> =
    if (experimental || experimentalDomain) {
        val serializerTypeSpec = serializerForFCEnum(names.className, type.enumValues)
        val serializerClass = ClassName(names.packageName, serializerTypeSpec.name!!)
        listOf(serializerTypeSpec, toFCEnumTypeSpec(type, serializerClass))
    } else {
        listOf(toStableEnumTypeSpec(type))
    }

private fun DomainTypeDeclaration.toStableEnumTypeSpec(type: ChromeDPType.Enum): TypeSpec =
    TypeSpec.enumBuilder(names.className).apply {
        addKDocAndStabilityAnnotations(element = this@toStableEnumTypeSpec)
        type.enumValues.forEach {
            addEnumConstant(
                name = protocolEnumEntryNameToKotlinName(it),
                typeSpec = TypeSpec.anonymousClassBuilder().addAnnotation(Annotations.serialName(it)).build()
            )
        }
        addAnnotation(Annotations.serializable)
    }.build()

private fun DomainTypeDeclaration.toFCEnumTypeSpec(type: ChromeDPType.Enum, serializerClass: ClassName): TypeSpec =
    TypeSpec.interfaceBuilder(names.className).apply {
        addModifiers(KModifier.SEALED)
        addKDocAndStabilityAnnotations(element = this@toFCEnumTypeSpec)
        addAnnotation(Annotations.serializableWith(serializerClass))

        type.enumValues.forEach {
            addType(TypeSpec.objectBuilder(protocolEnumEntryNameToKotlinName(it)).apply {
                addModifiers(KModifier.DATA)
                addSuperinterface(names.className)

                // For calls to serializers made directly with this sub-object instead of the FC enum's interface.
                // Example: Json.encodeToString(AXPropertyName.url)
                // (and not Json.encodeToString<AXPropertyName>(AXPropertyName.url))
                addAnnotation(Annotations.serializableWith(serializerClass))
            }.build())
        }
        require(type.enumValues.none { it.equals(UndefinedEnumEntryName, ignoreCase = true) }) {
            "Cannot synthesize the '$UndefinedEnumEntryName' value for experimental enum " +
                "${names.declaredName} (of domain ${names.domain.domainName}) because it clashes with an " +
                "existing value (case-insensitive). Values:\n - ${type.enumValues.joinToString("\n - ")}"
        }
        addType(notInProtocolClassTypeSpec(serializerClass))
    }.build()

private fun DomainTypeDeclaration.notInProtocolClassTypeSpec(serializerClass: ClassName) =
    TypeSpec.classBuilder(UndefinedEnumEntryName).apply {
        addModifiers(KModifier.DATA)
        addSuperinterface(names.className)

        // For calls to serializers made directly with this sub-object instead of the FC enum's interface.
        // Example: Json.encodeToString(AXPropertyName.NotDefinedInProtocol("notRendered"))
        // (and not Json.encodeToString<AXPropertyName>(AXPropertyName.NotDefinedInProtocol("notRendered"))
        addAnnotation(Annotations.serializableWith(serializerClass))

        addKdoc(
            "This extra enum entry represents values returned by Chrome that were not defined in " +
                "the protocol (for instance new values that were added later)."
        )
        primaryConstructor(FunSpec.constructorBuilder().apply {
            addParameter("value", String::class)
        }.build())
        addProperty(PropertySpec.builder("value", String::class).initializer("value").build())
    }.build()

private fun serializerForFCEnum(fcEnumClass: ClassName, enumValues: List<String>): TypeSpec =
    TypeSpec.objectBuilder("${fcEnumClass.simpleName}Serializer").apply {
        addModifiers(KModifier.PRIVATE)
        superclass(ExtDeclarations.fcEnumSerializer.parameterizedBy(fcEnumClass))
        addSuperclassConstructorParameter("%T::class", fcEnumClass)

        addFunction(FunSpec.builder("fromCode").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("code", String::class)
            returns(fcEnumClass)
            beginControlFlow("return when (code)")
            enumValues.forEach {
                addCode("%S -> %T\n", it, fcEnumClass.nestedClass(protocolEnumEntryNameToKotlinName(it)))
            }
            addCode("else -> %T(code)", fcEnumClass.nestedClass(UndefinedEnumEntryName))
            endControlFlow()
        }.build())

        addFunction(FunSpec.builder("codeOf").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("value", fcEnumClass)
            returns(String::class)
            beginControlFlow("return when (value)")
            enumValues.forEach {
                addCode("is %T -> %S\n", fcEnumClass.nestedClass(protocolEnumEntryNameToKotlinName(it)), it)
            }
            addCode("is %T -> value.value", fcEnumClass.nestedClass(UndefinedEnumEntryName))
            endControlFlow()
        }.build())
    }.build()

private fun protocolEnumEntryNameToKotlinName(protocolName: String) = protocolName.dashesToCamelCase()

private fun String.dashesToCamelCase(): String = replace(Regex("""-(\w)""")) { it.groupValues[1].uppercase() }

private fun DomainTypeDeclaration.toTypeAliasSpec(type: ChromeDPType.NamedRef): TypeAliasSpec =
    TypeAliasSpec.builder(names.declaredName, type.typeName).apply {
        addKDocAndStabilityAnnotations(element = this@toTypeAliasSpec)
    }.build()
