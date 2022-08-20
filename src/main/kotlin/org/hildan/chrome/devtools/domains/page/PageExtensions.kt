package org.hildan.chrome.devtools.domains.page

import java.nio.file.Path
import java.util.*

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
