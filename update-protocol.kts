import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ChromeDevToolsRepo {
    val baseUrl = "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol"
    val branch = "master"
    val packageJsonUrl = "$baseUrl/$branch/package.json"
    val browserProtocolUrl = "$baseUrl/$branch/json/browser_protocol.json"
    val jsProtocolUrl = "$baseUrl/$branch/json/js_protocol.json"
    val protocolUrls = listOf(browserProtocolUrl, jsProtocolUrl)

    val npmVersion: String
        get() {
            val packageJson = URL(packageJsonUrl).readText()
            return Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson)?.groupValues?.get(1) ?: "not-found"
        }
}

fun downloadTextFile(fileUrl: String, targetDir: Path) {
    val url = URL(fileUrl)
    val text = url.readText()
    val outputFilePath = targetDir.resolve(url.path.substringAfterLast('/'))
    outputFilePath.toFile().writeText(text)
}

fun updateProtocolFiles() {
    val outputDir = Paths.get("protocol")
    Files.createDirectories(outputDir)

    ChromeDevToolsRepo.protocolUrls.forEach { url ->
        print("Downloading protocol JSON spec from $url ... ")
        downloadTextFile(url, outputDir)
        println("Done.")
    }

    println("Chrome Devtools Protocol definition updated to ${ChromeDevToolsRepo.npmVersion}")
}

updateProtocolFiles()