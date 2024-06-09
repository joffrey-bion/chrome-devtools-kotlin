package org.hildan.chrome.devtools.build

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.model.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import org.hildan.chrome.devtools.protocol.json.*
import java.net.*
import java.nio.file.*
import javax.inject.*
import kotlin.io.path.*

open class UpdateProtocolDefinitionsTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    init {
        group = "protocol"
        description = "Updates the local Chrome DevTools Protocol definition from the official repository"

        // never consider this task up-to-date, the point is to check for updates
        outputs.upToDateWhen { false }
    }

    /**
     * The branch from which to fetch the definitions.
     */
    @Input
    val branch: Property<String> = objectFactory.property<String>().convention("master")

    /**
     * The output directory to store/update the fetched definitions.
     */
    @OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    @TaskAction
    fun generate() {
        val outputDirPath = outputDir.get().asFile.toPath()
        val descriptorUrls = ChromeJsonRepository.descriptorUrls(branch.get())
        descriptorUrls.all.forEach { url ->
            print("Downloading protocol JSON spec from $url ... ")
            downloadTextFile(url, outputDirPath)
            println("Done.")
        }

        val newVersion = ChromeJsonRepository.fetchProtocolNpmVersion(branch.get())
        outputDirPath.resolve("version.txt").writeText(newVersion.removePrefix("0.0."))
        println("Chrome Devtools Protocol definition updated to $newVersion")
    }
}

private fun downloadTextFile(url: URL, targetDir: Path) {
    val text = url.readText()
    val outputFilePath = targetDir.resolve(url.path.substringAfterLast('/'))
    outputFilePath.toFile().writeText(text)
}
