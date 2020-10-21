package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

object ExternalDeclarations {
    const val rootPackageName: String = "org.hildan.chrome.devtools"

    val chromeSessionClass = ClassName("$rootPackageName.protocol", "ChromeDPSession")

    val deprecatedAnnotation =
        AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Deprecated in the Chrome DevTools protocol\"")
            .build()

    val experimentalAnnotation = AnnotationSpec.builder(ClassName(rootPackageName, "ExperimentalChromeApi")).build()
}
