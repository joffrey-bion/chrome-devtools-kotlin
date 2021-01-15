package org.hildan.chrome.devtools.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

private const val baseUrl = "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol"
private const val branch = "master"
private const val browserProtocolUrl = "$baseUrl/$branch/json/browser_protocol.json"
private const val jsProtocolUrl = "$baseUrl/$branch/json/js_protocol.json"
private const val packageJsonUrl = "$baseUrl/$branch/package.json"

private val protocolDescriptorUrls = listOf(browserProtocolUrl, jsProtocolUrl)

open class UpdateProtocolDefinitionsTask : DefaultTask() {

    init {
        // never consider this task up-to-date, the point is to check for updates
        outputs.upToDateWhen { false }
    }

    @OutputDirectory
    val outputDir: Path = project.rootDir.resolve("protocol").toPath()

    @TaskAction
    fun generate() {
        Files.createDirectories(outputDir)

        protocolDescriptorUrls.forEach { url ->
            print("Downloading protocol JSON spec from $url ... ")
            downloadTextFile(url, outputDir)
            println("Done.")
        }

        val newVersion = fetchProtocolNpmVersion()
        outputDir.resolve("version.txt").toFile().writeText(newVersion)
        println("Chrome Devtools Protocol definition updated to $newVersion")
    }
}

private fun fetchProtocolNpmVersion(): String {
    val packageJson = URL(packageJsonUrl).readText()
    return Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson)?.groupValues?.get(1) ?: "not-found"
}

private fun downloadTextFile(fileUrl: String, targetDir: Path) {
    val url = URL(fileUrl)
    val text = url.readText()
    val outputFilePath = targetDir.resolve(url.path.substringAfterLast('/'))
    outputFilePath.toFile().writeText(text)
}
