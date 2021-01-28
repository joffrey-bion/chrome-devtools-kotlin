package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.Serializable

const val ROOT_PACKAGE_NAME = "org.hildan.chrome.devtools"

object ExtClasses {

    private const val targetsPackage = "$ROOT_PACKAGE_NAME.targets"

    private const val protocolPackage = "$ROOT_PACKAGE_NAME.protocol"

    val chromeDPSession = ClassName(protocolPackage, "ChromeDPSession")

    val experimentalChromeApi = ClassName(protocolPackage, "ExperimentalChromeApi")

    val targetImplementation = ClassName(targetsPackage, "SimpleTarget")

    fun targetInterface(targetName: String): ClassName = ClassName(targetsPackage, "${targetName}Target")
}

object Annotations {

    val serializable = AnnotationSpec.builder(Serializable::class).build()

    val deprecatedChromeApi = AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Deprecated in the Chrome DevTools protocol\"")
            .build()

    val experimentalChromeApi = AnnotationSpec.builder(ExtClasses.experimentalChromeApi).build()
}

object DocUrls {

    private const val docsBaseUrl = "https://chromedevtools.github.io/devtools-protocol/tot"

    fun domain(domainName: String) = "$docsBaseUrl/$domainName"

    fun type(domainName: String, typeName: String) = docElementUrl(domainName, "type", typeName)

    fun command(domainName: String, commandName: String) = docElementUrl(domainName, "method", commandName)

    fun event(domainName: String, eventName: String) = docElementUrl(domainName, "event", eventName)

    private fun docElementUrl(domainName: String, elementType: String, elementName: String) =
        "${domain(domainName)}/#$elementType-$elementName"
}
