package org.hildan.chrome.devtools.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.hildan.chrome.devtools.protocol.json.ChromeJsonRepository
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

open class UpdateProtocolDefinitionsTask : DefaultTask() {

    init {
        group = "protocol"

        // never consider this task up-to-date, the point is to check for updates
        outputs.upToDateWhen { false }
    }

    @Input
    val branch: String = "master"

    @OutputDirectory
    val outputDir: Path = project.rootDir.resolve("protocol-definition").toPath()

    @TaskAction
    fun generate() {
        Files.createDirectories(outputDir)

        val descriptorUrls = ChromeJsonRepository.descriptorUrls(branch)
        descriptorUrls.all.forEach { url ->
            print("Downloading protocol JSON spec from $url ... ")
            downloadTextFile(url, outputDir)
            println("Done.")
        }

        val newVersion = ChromeJsonRepository.fetchProtocolNpmVersion(branch)
        outputDir.resolve("version.txt").toFile().writeText(newVersion.removePrefix("0.0."))
        println("Chrome Devtools Protocol definition updated to $newVersion")
    }
}

private fun downloadTextFile(url: URL, targetDir: Path) {
    val text = url.readText()
    val outputFilePath = targetDir.resolve(url.path.substringAfterLast('/'))
    outputFilePath.toFile().writeText(text)
}
