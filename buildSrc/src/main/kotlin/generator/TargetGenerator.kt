package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hildan.chrome.devtools.build.json.TargetType
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.names.Annotations
import org.hildan.chrome.devtools.build.names.ExtDeclarations

private const val SESSION_ARG = "session"

fun createTargetInterface(targetName: String, domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.targetInterface(targetName)).apply {
        addKdoc("Represents the available domain APIs in $targetName targets")
        domains.forEach {
            addProperty(it.toPropertySpec())
        }
        addType(companionObjectWithSupportedDomains(domains))
    }.build()

private fun companionObjectWithSupportedDomains(domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.companionObjectBuilder().addProperty(supportedDomainsProperty(domains)).build()

private fun supportedDomainsProperty(domains: List<ChromeDPDomain>): PropertySpec =
    PropertySpec.builder("supportedDomains", LIST.parameterizedBy(String::class.asTypeName())).apply {
        addModifiers(KModifier.INTERNAL)
        val format = List(domains.size) { "%S" }.joinToString(separator = ", ", prefix = "listOf(", postfix = ")")
        initializer(format = format, *domains.map { it.names.domainName }.toTypedArray())
    }.build()

fun createSimpleAllTargetsImpl(domains: List<ChromeDPDomain>, targetTypes: List<TargetType>): TypeSpec =
    TypeSpec.classBuilder(ExtDeclarations.targetImplementation).apply {
        addKdoc("Implementation of all target interfaces by exposing all domain APIs")
        addModifiers(KModifier.INTERNAL)
        targetTypes.forEach {
            addSuperinterface(ExtDeclarations.targetInterface(it.name))
        }

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(SESSION_ARG, ExtDeclarations.chromeDPSession)
                .build()
        )

        domains.forEach { domain ->
            addProperty(domain.toPropertySpec {
                addModifiers(KModifier.OVERRIDE)
                delegate("lazy { %T(%N) }", domain.names.domainClassName, SESSION_ARG)
            })
        }
    }.build()

private fun ChromeDPDomain.toPropertySpec(configure: PropertySpec.Builder.() -> Unit = {}): PropertySpec =
    PropertySpec.builder(names.targetFieldName, names.domainClassName).apply {
        description?.let { addKdoc(it.escapeKDoc()) }
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        configure()
    }.build()
