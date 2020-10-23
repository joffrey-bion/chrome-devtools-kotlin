package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

object ExternalDeclarations {
    const val rootPackageName: String = "org.hildan.chrome.devtools"

    val chromeConnectionClass = ClassName("$rootPackageName.protocol", "ChromeDPConnection")
    val chromeSessionClass = ClassName("$rootPackageName.protocol", "ChromeDPSession")
    val browserSessionClass = ClassName("$rootPackageName.protocol", "ChromeBrowserSession")
    val targetSessionClass = ClassName("$rootPackageName.protocol", "ChromeTargetSession")

    val sessionIdClass = ClassName("$rootPackageName.domains.target", "SessionID")
    val targetIdClass = ClassName("$rootPackageName.domains.target", "TargetID")
    val browserContextIdClass = ClassName("$rootPackageName.domains.browser", "BrowserContextID")

    val deprecatedAnnotation =
        AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Deprecated in the Chrome DevTools protocol\"")
            .build()

    val experimentalAnnotation = AnnotationSpec.builder(ClassName(rootPackageName, "ExperimentalChromeApi")).build()

    val serializableAnnotation = AnnotationSpec.builder(ClassName("kotlinx.serialization", "Serializable")).build()

    val jsonElementClass = ClassName("kotlinx.serialization.json", "JsonElement")
}
