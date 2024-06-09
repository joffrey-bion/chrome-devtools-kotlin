package org.hildan.chrome.devtools.build

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import org.hildan.chrome.devtools.protocol.generator.*
import java.io.*

@CacheableTask
abstract class GenerateProtocolApiTask : DefaultTask() {

    init {
        group = "protocol"
        description = "Generates protocol API classes (domains, requests, responses, target types...)"
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    val protocolPaths: ConfigurableFileCollection = project.files(
        "protocol-definition/browser_protocol.json",
        "protocol-definition/js_protocol.json",
    )

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    val targetTypesPath: File = project.file("protocol-definition/target_types.json")

    @OutputDirectory
    val outputDirPath: File = project.file("src/commonMain/generated")

    @TaskAction
    fun generate() {
        println("Generating Chrome DevTools Protocol API...")
        Generator(
            protocolFiles = protocolPaths.map { it.toPath() },
            targetTypesFile = targetTypesPath.toPath(),
            generatedSourcesDir = outputDirPath.toPath(),
        ).generate()
        println("Done.")
    }
}
