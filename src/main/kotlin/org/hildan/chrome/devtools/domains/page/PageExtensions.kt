package org.hildan.chrome.devtools.domains.page

import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import java.nio.file.Path
import java.util.*

@OptIn(ExperimentalChromeApi::class)
suspend fun PageDomain.navigateAndWaitLoading(navigateRequest: NavigateRequest) {
    enable()
    val frameStoppedLoadingEvents = frameStoppedLoading()
    val frameId = navigate(navigateRequest).frameId
    frameStoppedLoadingEvents.first { it.frameId == frameId }
}

suspend fun PageDomain.captureScreenshotToFile(outputImagePath: Path, request: CaptureScreenshotRequest) {
    val capture = captureScreenshot(request)
    val imageBytes = Base64.getDecoder().decode(capture.data)
    outputImagePath.toFile().writeBytes(imageBytes)
}
