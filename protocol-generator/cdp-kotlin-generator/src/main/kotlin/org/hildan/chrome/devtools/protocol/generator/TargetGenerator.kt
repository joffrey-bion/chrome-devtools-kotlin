package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hildan.chrome.devtools.protocol.json.TargetType
import org.hildan.chrome.devtools.protocol.model.ChromeDPDomain
import org.hildan.chrome.devtools.protocol.names.Annotations
import org.hildan.chrome.devtools.protocol.names.ExtDeclarations

private const val SESSION_ARG = "session"

fun createTargetInterface(target: TargetType, domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.targetInterface(target)).apply {
        addKdoc("""
            Represents the available domain APIs in ${target.kotlinName} targets.
            
            The subset of domains available for this target type is not strictly defined by the protocol. The
            subset provided in this interface is guaranteed to work on this target type. However, some
            domains might be missing in this interface while being effectively supported by the target. If
            this is the case, you can always use the [%T] interface instead. 
            
            This interface is generated to match the latest Chrome DevToolsProtocol definitions. 
            It is not stable for inheritance, as new properties can be added without major version bump when
            the protocol changes. It is however safe to use all non-experimental and non-deprecated domains 
            defined here. The experimental and deprecation cycles of the protocol are reflected in this 
            interface with the same guarantees.
        """.trimIndent(), ExtDeclarations.allDomainsTargetInterface)
        domains.forEach {
            addProperty(it.toPropertySpec())
        }
        addType(
            TypeSpec.companionObjectBuilder()
                .addProperty(supportedDomainsProperty(domains))
                .addProperty(supportedCdpTargetsProperty(target.supportedCdpTargets))
                .build()
        )
    }.build()

fun createAllDomainsTargetInterface(allTargets: List<TargetType>, allDomains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.allDomainsTargetInterface).apply {
        addKdoc("""
            Represents a fictional (unsafe) target with access to all possible domain APIs in all targets.
            
            Every target supports only a subset of the protocol domains. Since these subsets are not clearly
            defined by the protocol definitions, the subset available in each target-specific interface is
            not strictly guaranteed to cover all the domains that are *effectively* supported by a target.
            
            This interface is an escape hatch to provide access to all domain APIs in case some domain is
            missing in the target-specific interface. It should be used with care, only when you know for
            sure that the domains you are using are effectively supported by the real attached target,
            otherwise you'll get runtime errors.
            
            This interface is generated to match the latest Chrome DevToolsProtocol definitions. 
            It is not stable for inheritance, as new properties can be added without major version bump when
            the protocol changes.
        """.trimIndent())

        allTargets.forEach { target ->
            addSuperinterface(ExtDeclarations.targetInterface(target))
        }

        // All domains supported by at least one target will be implicitly brought by the corresponding superinterface,
        // but we also need to add properties for domains that are technically not supported by any target interface.
        val domainsPresentInATarget = allTargets.flatMapTo(mutableSetOf()) { it.supportedDomains }
        val danglingDomains = allDomains.filterNot { it.names.domainName in domainsPresentInATarget }
        danglingDomains.forEach {
            addProperty(it.toPropertySpec())
        }
        addType(
            TypeSpec.companionObjectBuilder()
                .addProperty(supportedDomainsProperty(allDomains))
                .build()
        )
    }.build()

private fun supportedDomainsProperty(domains: List<ChromeDPDomain>): PropertySpec =
    PropertySpec.builder("supportedDomains", SET.parameterizedBy(String::class.asTypeName())).apply {
        addModifiers(KModifier.INTERNAL)
        val format = List(domains.size) { "%S" }.joinToString(separator = ", ", prefix = "setOf(", postfix = ")")
        initializer(format = format, *domains.map { it.names.domainName }.toTypedArray())
    }.build()

private fun supportedCdpTargetsProperty(cdpTargets: List<String>): PropertySpec =
    PropertySpec.builder("supportedCdpTargets", SET.parameterizedBy(String::class.asTypeName())).apply {
        addModifiers(KModifier.INTERNAL)
        val format = List(cdpTargets.size) { "%S" }.joinToString(separator = ", ", prefix = "setOf(", postfix = ")")
        initializer(format = format, *cdpTargets.toTypedArray())
    }.build()

fun createAllDomainsTargetImpl(targetTypes: List<TargetType>, domains: List<ChromeDPDomain>): TypeSpec =
    TypeSpec.classBuilder(ExtDeclarations.allDomainsTargetImplementation).apply {
        addKdoc("Implementation of all target interfaces by exposing all domain APIs")
        addModifiers(KModifier.INTERNAL)
        addSuperinterface(ExtDeclarations.allDomainsTargetInterface)
        targetTypes.forEach {
            addSuperinterface(ExtDeclarations.targetInterface(it))
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
