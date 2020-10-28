package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.hildan.chrome.devtools.build.json.TargetType
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.asClassName
import org.hildan.chrome.devtools.build.model.asVariableName

private const val SESSION_ARG = "session"

fun createTargetInterface(targetName: String, domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.interfaceBuilder(ExternalDeclarations.targetInterface(targetName)).apply {
        addKdoc("Represents the available domain APIs in $targetName targets")
        domains.forEach {
            addProperty(it.toPropertySpec())
        }
    }.build()

fun createSimpleAllTargetsImpl(domains: List<ChromeDPDomain>, targetTypes: List<TargetType>): TypeSpec =
    TypeSpec.classBuilder(ExternalDeclarations.targetImplementationClass).apply {
        addKdoc("Implementation of all target interfaces by exposing all domain APIs")
        addModifiers(KModifier.INTERNAL)
        targetTypes.forEach {
            addSuperinterface(ExternalDeclarations.targetInterface(it.name))
        }

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(SESSION_ARG, ExternalDeclarations.chromeDPSessionClass)
                .build()
        )

        domains.forEach { domain ->
            addProperty(domain.toPropertySpec {
                addModifiers(KModifier.OVERRIDE)
                delegate("lazy { %T(%N) }", domain.name.asClassName(), SESSION_ARG)
            })
        }
    }.build()

private fun ChromeDPDomain.toPropertySpec(configure: PropertySpec.Builder.() -> Unit = {}): PropertySpec =
    PropertySpec.builder(name.asVariableName(), name.asClassName()).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        if (deprecated) {
            addAnnotation(ExternalDeclarations.deprecatedAnnotation)
        }
        if (experimental) {
            addAnnotation(ExternalDeclarations.experimentalAnnotation)
        }
        configure()
    }.build()
