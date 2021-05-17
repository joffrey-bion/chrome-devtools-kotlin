package org.hildan.chrome.devtools.build.generator

fun String.escapeKDoc(): String = replace("%", "%%")

fun linkToDoc(docUrl: String) = "\n\n[Official\u00A0doc]($docUrl)"
