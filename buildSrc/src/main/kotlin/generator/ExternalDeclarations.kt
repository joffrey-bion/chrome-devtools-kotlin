package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

object ExternalDeclarations {
    const val rootPackageName: String = "org.hildan.chrome.devtools"
    private const val targetsPackage = "$rootPackageName.targets"
    private const val protocolPackage = "$rootPackageName.protocol"

    val chromeDPSessionClass = ClassName(protocolPackage, "ChromeDPSession")

    val deprecatedAnnotation =
        AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Deprecated in the Chrome DevTools protocol\"")
            .build()

    val experimentalAnnotation = AnnotationSpec.builder(ClassName(protocolPackage, "ExperimentalChromeApi")).build()

    val serializableAnnotation = AnnotationSpec.builder(ClassName("kotlinx.serialization", "Serializable")).build()

    val jsonElementClass = ClassName("kotlinx.serialization.json", "JsonElement")

    fun targetInterface(targetName: String): ClassName = ClassName(targetsPackage, "${targetName}Target")

    val targetImplementationClass = ClassName(targetsPackage, "SimpleTarget")
}
