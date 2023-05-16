package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import kotlinx.serialization.*
import org.hildan.chrome.devtools.protocol.model.ChromeDPDomain
import org.hildan.chrome.devtools.protocol.model.ChromeDPType
import org.hildan.chrome.devtools.protocol.model.DomainTypeDeclaration
import org.hildan.chrome.devtools.protocol.names.Annotations

fun ChromeDPDomain.createDomainTypesFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.packageName, fileName = names.typesFilename).apply {
        addAnnotation(Annotations.suppressWarnings)
        types.forEach { addDomainType(it) }
    }.build()

private fun FileSpec.Builder.addDomainType(typeDeclaration: DomainTypeDeclaration) {
    when (val type = typeDeclaration.type) {
        is ChromeDPType.Object -> addType(typeDeclaration.toDataClassTypeSpec(type))
        is ChromeDPType.Enum -> addType(typeDeclaration.toEnumTypeSpec(type))
        is ChromeDPType.NamedRef -> addTypeAlias(typeDeclaration.toTypeAliasSpec(type))
    }
}

private fun DomainTypeDeclaration.toDataClassTypeSpec(type: ChromeDPType.Object): TypeSpec =
    TypeSpec.classBuilder(names.declaredName).apply {
        addModifiers(KModifier.DATA)
        addCommonConfig(this@toDataClassTypeSpec)
        addPrimaryConstructorProps(type.properties)
    }.build()

private fun DomainTypeDeclaration.toEnumTypeSpec(type: ChromeDPType.Enum): TypeSpec =
    TypeSpec.enumBuilder(names.declaredName).apply {
        addCommonConfig(this@toEnumTypeSpec)
        type.enumValues.forEach {
            val enumValueKotlinName = it.dashesToCamelCase()
            val serialNameAnnotation = AnnotationSpec.builder(SerialName::class).addMember("%S", it).build()
            addEnumConstant(enumValueKotlinName, TypeSpec.anonymousClassBuilder().addAnnotation(serialNameAnnotation).build())
        }
    }.build()

private fun String.dashesToCamelCase(): String = replace(Regex("""-(\w)""")) { it.groupValues[1].uppercase() }

private fun TypeSpec.Builder.addCommonConfig(domainTypeDeclaration: DomainTypeDeclaration) {
    domainTypeDeclaration.description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDoc(domainTypeDeclaration.docUrl))
    if (domainTypeDeclaration.deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (domainTypeDeclaration.experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    addAnnotation(Annotations.serializable)
}

private fun DomainTypeDeclaration.toTypeAliasSpec(type: ChromeDPType.NamedRef): TypeAliasSpec =
    TypeAliasSpec.builder(names.declaredName, type.typeName).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        addKdoc(linkToDoc(docUrl))
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
    }.build()
