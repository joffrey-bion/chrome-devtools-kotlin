package org.hildan.chrome.devtools.domains.page

import kotlinx.coroutines.flow.first
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import java.nio.file.Path
import java.util.*

/**
 * Navigates the current page according to the provided [url], and suspends until the corresponding
 * `frameStoppedLoading` event is received.
 */
@Deprecated("This method is subject to race conditions. Prefer calling ChromePageSession.navigateAndAwaitPageLoad")
suspend fun PageDomain.navigateAndWaitLoading(url: String) {
    @Suppress("DEPRECATION")
    navigateAndWaitLoading(NavigateRequest(url = url))
}

/**
 * Navigates the current page according to the provided [navigateRequest], and suspends until the
 * corresponding `frameStoppedLoading` event is received.
 */
@Deprecated("This method is subject to race conditions. Prefer calling ChromePageSession.navigateAndAwaitPageLoad")
@OptIn(ExperimentalChromeApi::class)
suspend fun PageDomain.navigateAndWaitLoading(navigateRequest: NavigateRequest) {
    enable()
    val frameStoppedLoadingEvents = frameStoppedLoadingEvents()
    val frameId = navigate(navigateRequest).frameId
    // FIXME if we're too slow here, we can miss the frame event
    frameStoppedLoadingEvents.first { it.frameId == frameId }
}

/**
 * Captures a screenshot of the current page based on the given [options], and stores the resulting image into a new
 * file at the given [outputImagePath]. If the file already exists, it is overwritten.
 */
suspend fun PageDomain.captureScreenshotToFile(
    outputImagePath: Path,
    options: CaptureScreenshotRequest.Builder.() -> Unit = {},
) {
    val capture = captureScreenshot(options)
    val imageBytes = Base64.getDecoder().decode(capture.data)
    outputImagePath.toFile().writeBytes(imageBytes)
}

/**
 * Captures a screenshot of the current page based on the given [request], and store the resulting image into a new file
 * at the given [outputImagePath].
 */
@Deprecated(
    message = "Creating CaptureScreenshotRequest instances is not binary-forward-compatible, prefer the overload with" +
        " builder lambda"
)
suspend fun PageDomain.captureScreenshotToFile(
    outputImagePath: Path,
    request: CaptureScreenshotRequest,
) {
    val capture = captureScreenshot(request)
    val imageBytes = Base64.getDecoder().decode(capture.data)
    outputImagePath.toFile().writeBytes(imageBytes)
}
