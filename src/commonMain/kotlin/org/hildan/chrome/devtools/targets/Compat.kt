@file:Suppress("unused")

package org.hildan.chrome.devtools.targets

@Deprecated(
    message = "Renamed ChromeSessionMetaData",
    replaceWith = ReplaceWith(
        expression = "SessionMetaData",
        imports = ["org.hildan.chrome.devtools.targets.SessionMetaData"],
    ),
)
typealias ChromePageMetaData = SessionMetaData
