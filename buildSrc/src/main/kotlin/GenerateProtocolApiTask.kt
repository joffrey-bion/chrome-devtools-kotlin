package org.hildan.chrome.devtools.build

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.model.*
import org.gradle.api.tasks.*
import org.hildan.chrome.devtools.protocol.generator.*
import javax.inject.*

@CacheableTask
abstract class GenerateProtocolApiTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    init {
        group = "protocol"
        description = "Generates protocol API classes (domains, requests, responses, target types...)"
    }

    /**
     * The paths to the JSON protocol descriptors.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    val protocolPaths: ConfigurableFileCollection = objectFactory.fileCollection()

    /**
     * The path to the JSON file describing the target types and their supported domains.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    val targetTypesPath: RegularFileProperty = objectFactory.fileProperty()

    /**
     * The path to the directory where the Kotlin protocol API classes should be generated.
     */
    @OutputDirectory
    val outputDirPath: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun generate() {
        println("Generating Chrome DevTools Protocol API...")
        Generator(
            protocolFiles = protocolPaths.map { it.toPath() },
            targetTypesFile = targetTypesPath.get().asFile.toPath(),
            generatedSourcesDir = outputDirPath.get().asFile.toPath(),
        ).generate()
        println("Done.")
    }
}
