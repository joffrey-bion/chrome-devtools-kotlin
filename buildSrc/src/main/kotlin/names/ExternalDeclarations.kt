package org.hildan.chrome.devtools.build.names

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

    /**
     * Suppress common warnings in generated files:
     *
     * - RedundantVisibilityModifier: necessary because public keyword cannot be removed
     * - DEPRECATION: the warning still occurs if a deprecated function uses a deprecated type as parameter type
     * - EXPERIMENTAL_API_USAGE: for data classes with params of experimental types, the warning doesn't go away by
     * annotating the relevant property/constructor-arg with experimental annotation. The whole class/constructor
     * would need to be annotated as experimental, which is not desirable.
     */
    val suppressWarnings = suppress("RedundantVisibilityModifier", "DEPRECATION", "EXPERIMENTAL_API_USAGE")

    private fun suppress(vararg warningTypes: String) = AnnotationSpec.builder(Suppress::class)
        .addMember(format = warningTypes.joinToString { "%S" }, *warningTypes)
        .build()
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
