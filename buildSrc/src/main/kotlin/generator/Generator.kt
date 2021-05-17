package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FileSpec
import org.hildan.chrome.devtools.build.json.ALL_DOMAINS_TARGET
import org.hildan.chrome.devtools.build.json.ChromeProtocolDescriptor
import org.hildan.chrome.devtools.build.json.TargetType
import org.hildan.chrome.devtools.build.json.pullNestedEnumsToTopLevel
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.toChromeDPDomain
import org.hildan.chrome.devtools.build.names.Annotations
import org.hildan.chrome.devtools.build.names.ExtClasses
import java.nio.file.Files
import java.nio.file.Path

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

        val targets = TargetType.parseJson(targetTypesFile) + TargetType(ALL_DOMAINS_TARGET, domains.map { it.names.domainName })
        targets.forEach { target ->
            generateTargetInterfaceFile(targetName = target.name, domains = domains.filter { it.names.domainName in target.supportedDomains })
        }
        generateSimpleTargetFile(domains = domains, targetTypes = targets)
    }

    private fun loadProtocolDomains(): List<ChromeDPDomain> {
        val descriptors = protocolFiles.map { ChromeProtocolDescriptor.parseJson(it) }
        if (descriptors.distinctBy { it.version }.size > 1) {
            error("Some descriptors have differing versions: ${descriptors.map { it.version }}")
        }
        return descriptors.flatMap { it.domains }.map { it.pullNestedEnumsToTopLevel().toChromeDPDomain() }
    }

    private fun generateTargetInterfaceFile(targetName: String, domains: List<ChromeDPDomain>) {
        val targetInterface = ExtClasses.targetInterface(targetName)
        FileSpec.builder(targetInterface.packageName, targetInterface.simpleName)
            .addAnnotation(Annotations.suppressWarnings)
            .addType(createTargetInterface(targetName, domains))
            .build()
            .writeTo(generatedSourcesDir)
    }

    private fun generateSimpleTargetFile(domains: List<ChromeDPDomain>, targetTypes: List<TargetType>) {
        val targetClass = ExtClasses.targetImplementation
        FileSpec.builder(targetClass.packageName, targetClass.simpleName)
            .addAnnotation(Annotations.suppressWarnings)
            .addType(createSimpleAllTargetsImpl(domains, targetTypes))
            .build()
            .writeTo(generatedSourcesDir)
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
