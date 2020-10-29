package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.model.ChromeDPType
import org.hildan.chrome.devtools.build.model.DomainTypeDeclaration

fun FileSpec.Builder.addDomainType(typeDeclaration: DomainTypeDeclaration) {
    when (val type = typeDeclaration.type) {
        is ChromeDPType.Object -> addType(typeDeclaration.toDataClassTypeSpec(type))
        is ChromeDPType.Enum -> addType(typeDeclaration.toEnumTypeSpec(type))
        is ChromeDPType.Array,
        is ChromeDPType.Primitive<*>,
        is ChromeDPType.Unknown -> addTypeAlias(typeDeclaration.toTypeAliasSpec())
        is ChromeDPType.Reference -> error("Type reference not allowed in domain type declaration")
    }
}

private fun DomainTypeDeclaration.toDataClassTypeSpec(type: ChromeDPType.Object): TypeSpec =
    TypeSpec.classBuilder(name).apply {
        addModifiers(KModifier.DATA)
        addCommonConfig(this@toDataClassTypeSpec)
        addPrimaryConstructorProps(type.properties)
    }.build()

private fun DomainTypeDeclaration.toEnumTypeSpec(type: ChromeDPType.Enum): TypeSpec =
    TypeSpec.enumBuilder(name).apply {
        addCommonConfig(this@toEnumTypeSpec)
        // TODO capitalize enum values with serialization annotation?
        type.enumValues.forEach { addEnumConstant(it) }
    }.build()

private fun TypeSpec.Builder.addCommonConfig(domainTypeDeclaration: DomainTypeDeclaration) {
    domainTypeDeclaration.description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDocSentence(domainTypeDeclaration.docUrl))
    if (domainTypeDeclaration.deprecated) {
        addAnnotation(ExternalDeclarations.deprecatedAnnotation)
    }
    if (domainTypeDeclaration.experimental) {
        addAnnotation(ExternalDeclarations.experimentalAnnotation)
    }
    addAnnotation(ExternalDeclarations.serializableAnnotation)
}

private fun DomainTypeDeclaration.toTypeAliasSpec(): TypeAliasSpec =
    TypeAliasSpec.builder(name, type.toTypeName(ExternalDeclarations.rootPackageName)).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        addKdoc(linkToDocSentence(docUrl))
        if (deprecated) {
            addAnnotation(ExternalDeclarations.deprecatedAnnotation)
        }
        if (experimental) {
            addAnnotation(ExternalDeclarations.experimentalAnnotation)
        }
    }.build()
