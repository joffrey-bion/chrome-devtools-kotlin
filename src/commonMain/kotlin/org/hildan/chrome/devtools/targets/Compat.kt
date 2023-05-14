@file:Suppress("unused")

package org.hildan.chrome.devtools.targets

import org.hildan.chrome.devtools.sessions.*

@Deprecated(
    message = "Renamed PageSession",
    replaceWith = ReplaceWith(
        expression = "PageSession",
        imports = ["org.hildan.chrome.devtools.sessions.PageSession"],
    ),
)
typealias ChromePageSession = PageSession

@Deprecated(
    message = "Renamed PageTarget",
    replaceWith = ReplaceWith(
        expression = "PageTarget",
        imports = ["org.hildan.chrome.devtools.targets.PageTarget"],
    ),
)
typealias RenderFrameTarget = PageTarget

@Deprecated(
    message = "Renamed ChromeSessionMetaData",
    replaceWith = ReplaceWith(
        expression = "SessionMetaData",
        imports = ["org.hildan.chrome.devtools.sessions.SessionMetaData"],
    ),
)
typealias ChromePageMetaData = SessionMetaData

@Deprecated(
    message = "Renamed BrowserSession",
    replaceWith = ReplaceWith(
        expression = "BrowserSession",
        imports = ["org.hildan.chrome.devtools.sessions.BrowserSession"],
    ),
)
typealias ChromeBrowserSession = BrowserSession
