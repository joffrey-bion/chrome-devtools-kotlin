package org.hildan.chrome.devtools.build.generator

fun String.escapeKDoc(): String = replace("%", "%%")

fun linkToDocSentence(docUrl: String) = "\n\n[Official\u00A0doc]($docUrl)"
