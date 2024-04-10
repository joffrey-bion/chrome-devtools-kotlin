package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.FileSpec
import org.hildan.chrome.devtools.protocol.json.ChromeProtocolDescriptor
import org.hildan.chrome.devtools.protocol.json.TargetType
import org.hildan.chrome.devtools.protocol.json.pullNestedEnumsToTopLevel
import org.hildan.chrome.devtools.protocol.model.ChromeDPDomain
import org.hildan.chrome.devtools.protocol.model.toChromeDPDomain
import org.hildan.chrome.devtools.protocol.names.Annotations
import org.hildan.chrome.devtools.protocol.names.ExtDeclarations
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class Generator(
    private val protocolFiles: List<Path>,
    private val targetTypesFile: Path,
    private val generatedSourcesDir: Path
) {
    fun generate() {
        generatedSourcesDir.toFile().deleteRecursively()
        Files.createDirectories(generatedSourcesDir)

        val domains = loadProtocolDomains()
        domains.forEach(::generateDomainFiles)
        
        val domainsByName = domains.associateBy { it.names.domainName }

        val targets = TargetType.parseJson(targetTypesFile)
        targets.forEach { target ->
            generateTargetInterfaceFile(
                target = target,
                domains = target.supportedDomains.map {
                    domainsByName[it]
                        ?: error("Domain '$it' is not present in the protocol definitions, yet is marked as supported" +
                                     " for target type '${target.kotlinName}' (${target.chromiumAgentHostType})")
                },
            )
        }
        generateAllDomainsTargetInterfaceFile(allTargets = targets, allDomains = domains)
        generateAllDomainsTargetImplFile(allTargets = targets, allDomains = domains)
        generateChildSessionsFiles(childTargets = targets.filterNot { it.kotlinName == "Browser" })
    }

    private fun loadProtocolDomains(): List<ChromeDPDomain> {
        val descriptors = protocolFiles.map { ChromeProtocolDescriptor.fromJson(it.readText()) }
        if (descriptors.distinctBy { it.version }.size > 1) {
            error("Some descriptors have differing versions: ${descriptors.map { it.version }}")
        }
        return descriptors.flatMap { it.domains }.map { it.pullNestedEnumsToTopLevel().toChromeDPDomain() }
    }

    private fun generateTargetInterfaceFile(target: TargetType, domains: List<ChromeDPDomain>) {
        val targetInterface = ExtDeclarations.targetInterface(target)
        FileSpec.builder(targetInterface.packageName, targetInterface.simpleName)
            .addAnnotation(Annotations.suppressWarnings)
            .addType(createTargetInterface(target, domains))
            .build()
            .writeTo(generatedSourcesDir)
    }

    private fun generateAllDomainsTargetInterfaceFile(allTargets: List<TargetType>, allDomains: List<ChromeDPDomain>) {
        val targetInterface = ExtDeclarations.allDomainsTargetInterface
        FileSpec.builder(targetInterface.packageName, targetInterface.simpleName)
            .addAnnotation(Annotations.suppressWarnings)
            .addType(createAllDomainsTargetInterface(allTargets, allDomains))
            .build()
            .writeTo(generatedSourcesDir)
    }

    private fun generateAllDomainsTargetImplFile(allTargets: List<TargetType>, allDomains: List<ChromeDPDomain>) {
        val targetClass = ExtDeclarations.allDomainsTargetImplementation
        FileSpec.builder(targetClass.packageName, targetClass.simpleName)
            .addAnnotation(Annotations.suppressWarnings)
            .addType(createAllDomainsTargetImpl(allTargets, allDomains))
            .build()
            .writeTo(generatedSourcesDir)
    }

    private fun generateChildSessionsFiles(childTargets: List<TargetType>) {
        createSessionInterfacesFileSpec(childTargets).writeTo(generatedSourcesDir)
        createSessionAdaptersFileSpec(childTargets).writeTo(generatedSourcesDir)
    }

    private fun generateDomainFiles(domain: ChromeDPDomain) {
        if (domain.types.isNotEmpty()) {
            domain.createDomainTypesFileSpec().writeTo(generatedSourcesDir)
        }
        if (domain.events.isNotEmpty()) {
            domain.createDomainEventTypesFileSpec().writeTo(generatedSourcesDir)
        }
        domain.createDomainFileSpec().writeTo(generatedSourcesDir)
    }
}
