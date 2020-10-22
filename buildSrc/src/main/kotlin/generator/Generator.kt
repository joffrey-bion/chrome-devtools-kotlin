package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.FileSpec
import org.hildan.chrome.devtools.build.json.ChromeProtocolDescriptor
import org.hildan.chrome.devtools.build.model.ChromeDPDomain
import org.hildan.chrome.devtools.build.model.sanitize
import java.nio.file.Files
import java.nio.file.Path

class Generator(
    private val protocolFiles: List<Path>,
    private val generatedSourcesDir: Path
) {
    fun generate() {
        val descriptors = protocolFiles.map { ChromeProtocolDescriptor.parseJson(it) }
        if (!haveSameVersion(descriptors)) {
            println("Some descriptors have differing versions: ${descriptors.map { it.version }}")
        }
        generatedSourcesDir.toFile().deleteRecursively()
        Files.createDirectories(generatedSourcesDir)
        val domains = descriptors.flatMap { it.domains }.map { sanitize(it) }
        domains.forEach(::generateDomainFiles)
        generateClientFile(domains)
    }

    private fun haveSameVersion(descriptors: List<ChromeProtocolDescriptor>): Boolean =
        descriptors.distinctBy { it.version == descriptors[0].version }.size <= 1

    private fun generateClientFile(domains: List<ChromeDPDomain>) {
        FileSpec.builder(packageName = ExternalDeclarations.rootPackageName, fileName = "ChromeApi").apply {
            addType(createClientClass(domains))
        }.build().writeTo(generatedSourcesDir)
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

    private fun ChromeDPDomain.createDomainTypesFileSpec(): FileSpec =
        FileSpec.builder(packageName = packageName, fileName = "${name}Types").apply {
            types.forEach { addDomainType(it) }
        }.build()

    private fun ChromeDPDomain.createDomainEventTypesFileSpec(): FileSpec =
        FileSpec.builder(packageName = eventsPackageName, fileName = "${name}Events").apply {
            addType(createEventSealedClass())
        }.build()

    private fun ChromeDPDomain.createDomainFileSpec(): FileSpec =
        FileSpec.builder(packageName = packageName, fileName = "${name}Domain").apply {
            commands.forEach {
                if (it.parameters.isNotEmpty()) addType(it.createInputTypeSpec())
                if (it.returns.isNotEmpty()) addType(it.createOutputTypeSpec())
            }
            addType(createDomainClass())
        }.build()
}

fun String.escapeKDoc(): String = replace("%", "%%")
