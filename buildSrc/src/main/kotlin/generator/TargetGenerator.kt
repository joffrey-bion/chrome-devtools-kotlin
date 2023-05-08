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

fun createAllDomainsTargetInterface(allTargets: List<TargetType>, allDomains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.allDomainsTargetInterface).apply {
        addKdoc(
            "Represents a fictional (unsafe) target with access to all possible domain APIs in all targets. " +
                    "The domains in this target type are not guaranteed to be supported by the debugger server, " +
                    "and runtime errors could occur."
        )
        allTargets.forEach { target ->
            addSuperinterface(ExtDeclarations.targetInterface(target.name))
        }

        // we want to add properties for all domains, including those who are technically not supported by any target
        val domainsPresentInATarget = allTargets.flatMapTo(mutableSetOf()) { it.supportedDomains }
        val danglingDomains = allDomains.filterNot { it.names.domainName in domainsPresentInATarget }
        danglingDomains.forEach {
            addProperty(it.toPropertySpec())
        }
        addType(companionObjectWithSupportedDomains(allDomains))
    }.build()

private fun companionObjectWithSupportedDomains(domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.companionObjectBuilder().addProperty(supportedDomainsProperty(domains)).build()

private fun supportedDomainsProperty(domains: List<ChromeDPDomain>): PropertySpec =
    PropertySpec.builder("supportedDomains", LIST.parameterizedBy(String::class.asTypeName())).apply {
        addModifiers(KModifier.INTERNAL)
        val format = List(domains.size) { "%S" }.joinToString(separator = ", ", prefix = "listOf(", postfix = ")")
        initializer(format = format, *domains.map { it.names.domainName }.toTypedArray())
    }.build()

fun createAllDomainsTargetImpl(targetTypes: List<TargetType>, domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.classBuilder(ExtDeclarations.allDomainsTargetImplementation).apply {
        addKdoc("Implementation of all target interfaces by exposing all domain APIs")
        addModifiers(KModifier.INTERNAL)
        addSuperinterface(ExtDeclarations.allDomainsTargetInterface)
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
        addKdoc(linkToDoc(docUrl))
        if (deprecated) {
            addAnnotation(Annotations.deprecatedChromeApi)
        }
        if (experimental) {
            addAnnotation(Annotations.experimentalChromeApi)
        }
        configure()
    }.build()
