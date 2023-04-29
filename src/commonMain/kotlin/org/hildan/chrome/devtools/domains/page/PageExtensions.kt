package org.hildan.chrome.devtools.domains.page

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes the base64 data of the screenshot into image bytes.
 */
@OptIn(ExperimentalEncodingApi::class)
fun CaptureScreenshotResponse.decodeData(): ByteArray = Base64.decode(data)
