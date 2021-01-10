package org.hildan.chrome.devtools.domains.page

import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import java.nio.file.Path
import java.util.*

/**
 * Navigates the current page according to the provided [url], and suspends until the corresponding
 * `frameStoppedLoading` event is received.
 */
@Deprecated("This method is subject to race conditions. Prefer calling it on the ChromePageSession itself instead")
suspend fun PageDomain.navigateAndWaitLoading(url: String) {
    navigateAndWaitLoading(NavigateRequest(url = url))
}

/**
 * Navigates the current page according to the provided [navigateRequest], and suspends until the
 * corresponding `frameStoppedLoading` event is received.
 */
@Deprecated("This method is subject to race conditions. Prefer calling it on the ChromePageSession itself instead")
@OptIn(ExperimentalChromeApi::class)
suspend fun PageDomain.navigateAndWaitLoading(navigateRequest: NavigateRequest) {
    enable()
    val frameStoppedLoadingEvents = frameStoppedLoading()
    val frameId = navigate(navigateRequest).frameId
    // FIXME if we're too slow here, we can miss the frame event
    frameStoppedLoadingEvents.first { it.frameId == frameId }
}

/**
 * Captures a screenshot of the current page based on the given [request], and store the resulting image into a new file
 * at the given [outputImagePath].
 */
suspend fun PageDomain.captureScreenshotToFile(
    outputImagePath: Path,
    request: CaptureScreenshotRequest = CaptureScreenshotRequest()
) {
    val capture = captureScreenshot(request)
    val imageBytes = Base64.getDecoder().decode(capture.data)
    outputImagePath.toFile().writeBytes(imageBytes)
}
