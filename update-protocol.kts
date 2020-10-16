import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val outputDir = Paths.get("protocol")
Files.createDirectories(outputDir)

object ChromeDevToolsRepo {
    val baseUrl = "https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol"
    val branch = "master"
    val browserProtocolUrl = "$baseUrl/$branch/json/browser_protocol.json"
    val jsProtocolUrl = "$baseUrl/$branch/json/js_protocol.json"
    val protocolUrls = listOf(browserProtocolUrl, jsProtocolUrl)
}

fun downloadTextFile(fileUrl: String, targetDir: Path) {
    val url = URL(fileUrl)
    val text = url.readText()
    val outputFilePath = targetDir.resolve(url.path.substringAfterLast('/'))
    outputFilePath.toFile().writeText(text)
}

ChromeDevToolsRepo.protocolUrls.forEach { url -> downloadTextFile(url, outputDir) }
