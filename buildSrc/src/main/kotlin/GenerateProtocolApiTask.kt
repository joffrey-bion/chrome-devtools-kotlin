package org.hildan.chrome.devtools.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.hildan.chrome.devtools.build.generator.Generator
import java.nio.file.Path
import java.nio.file.Paths

open class GenerateProtocolApiTask : DefaultTask() {

    @InputFiles
    val protocolPaths = listOf(Paths.get("protocol/browser_protocol.json"), Paths.get("protocol/js_protocol.json"))

    @OutputDirectory
    val outputDirPath: Path = project.rootDir.resolve("src/main/generated").toPath()

    @TaskAction
    fun generate() {
        println("Generating Chrome DevTools Protocol API...")
        Generator(protocolPaths, outputDirPath).generate()
        println("Done.")
    }
}
