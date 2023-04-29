package org.hildan.chrome.devtools.targets

/**
 * The name of the possible target types, as defined in
 * [Chromium's code](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc;l=64-73).
 */
internal object TargetTypeNames {
    const val tab = "tab"
    const val page = "page"
    const val iFrame = "iframe"
    const val worker = "worker"
    const val sharedWorker = "shared_worker"
    const val serviceWorker = "service_worker"
    const val browser = "browser"
    const val webview = "webview"
    const val other = "other"
    const val auctionWorklet = "auction_worklet"
}
