package org.hildan.chrome.devtools.domains.page

import java.nio.file.Path
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.writeBytes

/**
 * Captures a screenshot of the current page based on the given [options], and stores the resulting image into a new
 * file at the given [outputImagePath]. If the file already exists, it is overwritten.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun PageDomain.captureScreenshotToFile(
    outputImagePath: Path,
    options: CaptureScreenshotRequest.Builder.() -> Unit = {},
) {
    val capture = captureScreenshot(options)
    val imageBytes = capture.decodeData()
    outputImagePath.writeBytes(imageBytes)
}
