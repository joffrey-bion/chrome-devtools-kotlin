package org.hildan.chrome.devtools.build

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.hildan.chrome.devtools.protocol.generator.*

@CacheableTask
abstract class GenerateProtocolApiTask : DefaultTask() {

    init {
        group = "protocol"
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    val protocolPaths = project.files(
        "protocol-definition/browser_protocol.json",
        "protocol-definition/js_protocol.json",
    )

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFile
    val targetTypesPath = project.file("protocol-definition/target_types.json")

    @OutputDirectory
    val outputDirPath = project.file("src/commonMain/generated")

    @TaskAction
    fun generate() {
        println("Generating Chrome DevTools Protocol API...")
        Generator(protocolPaths.map { it.toPath() }, targetTypesPath.toPath(), outputDirPath.toPath()).generate()
        println("Done.")
    }
}
