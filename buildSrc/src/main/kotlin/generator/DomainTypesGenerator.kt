package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.ChromeDPType
import org.hildan.chrome.devtools.build.model.DomainTypeDeclaration
import org.hildan.chrome.devtools.build.names.Annotations

fun ChromeDPDomain.createDomainTypesFileSpec(): FileSpec =
    FileSpec.builder(packageName = names.packageName, fileName = names.typesFilename).apply {
        addAnnotation(Annotations.suppressWarnings)
        types.forEach { addDomainType(it) }
    }.build()

private fun FileSpec.Builder.addDomainType(typeDeclaration: DomainTypeDeclaration) {
    when (val type = typeDeclaration.type) {
        is ChromeDPType.Object -> addType(typeDeclaration.toDataClassTypeSpec(type))
        is ChromeDPType.NamedRef.Enum -> addType(typeDeclaration.toEnumTypeSpec(type))
        is ChromeDPType.NamedRef -> addTypeAlias(typeDeclaration.toTypeAliasSpec(type))
    }
}

private fun DomainTypeDeclaration.toDataClassTypeSpec(type: ChromeDPType.Object): TypeSpec =
    TypeSpec.classBuilder(names.declaredName).apply {
        addModifiers(KModifier.DATA)
        addCommonConfig(this@toDataClassTypeSpec)
        addPrimaryConstructorProps(type.properties)
    }.build()

private fun DomainTypeDeclaration.toEnumTypeSpec(type: ChromeDPType.NamedRef.Enum): TypeSpec =
    TypeSpec.enumBuilder(names.declaredName).apply {
        addCommonConfig(this@toEnumTypeSpec)
        type.enumValues.forEach { addEnumConstant(it) }
    }.build()

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
