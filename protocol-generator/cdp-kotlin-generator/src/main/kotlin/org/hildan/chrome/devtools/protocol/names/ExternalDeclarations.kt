package org.hildan.chrome.devtools.protocol.names

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hildan.chrome.devtools.protocol.json.*

const val ROOT_PACKAGE_NAME = "org.hildan.chrome.devtools"

/**
 * Represents the naming contract for declarations that are used from non-generated code.
 */
object ExtDeclarations {

    const val sessionsPackage = "$ROOT_PACKAGE_NAME.sessions"
    private const val targetsPackage = "$ROOT_PACKAGE_NAME.targets"
    private const val protocolPackage = "$ROOT_PACKAGE_NAME.protocol"

    val chromeDPSession = ClassName(protocolPackage, "ChromeDPSession")
    val sessionRequestExtension = MemberName(protocolPackage, "request")
    val sessionTypedEventsExtension = MemberName(protocolPackage, "typedEvents")

    val experimentalChromeApi = ClassName(protocolPackage, "ExperimentalChromeApi")

    val fcEnumSerializer = ClassName(protocolPackage, "FCEnumSerializer")

    val allDomainsTargetInterface = ClassName(targetsPackage, "AllDomainsTarget")
    val allDomainsTargetImplementation = ClassName(targetsPackage, "UberTarget")

    const val sessionsFileName = "ChildSessions"
    const val sessionAdaptersFileName = "ChildSessionAdapters"
    val childSessionInterface = ClassName(sessionsPackage, "ChildSession")
    val childSessionUnsafeFun = childSessionInterface.member("unsafe")

    fun targetInterface(target: TargetType): ClassName = ClassName(targetsPackage, "${target.kotlinName}Target")
    fun sessionInterface(target: TargetType): ClassName = ClassName(sessionsPackage, "${target.kotlinName}Session")
    fun sessionAdapter(target: TargetType): ClassName = ClassName(sessionsPackage, "${target.kotlinName}SessionAdapter")
}

object Annotations {

    val serializable = AnnotationSpec.builder(Serializable::class).build()

    fun serialName(name: String) = AnnotationSpec.builder(SerialName::class).addMember("%S", name).build()

    fun serializableWith(serializerClass: ClassName) = AnnotationSpec.builder(Serializable::class)
        .addMember("with = %T::class", serializerClass)
        .build()

    val jvmOverloads = AnnotationSpec.builder(JvmOverloads::class).build()

    val deprecatedChromeApi = AnnotationSpec.builder(Deprecated::class)
            .addMember("message = \"Deprecated in the Chrome DevTools protocol\"")
            .build()

    val experimentalChromeApi = AnnotationSpec.builder(ExtDeclarations.experimentalChromeApi).build()

    /**
     * Annotation to suppress common warnings in generated files.
     */
    val suppressWarnings = suppress(
        // because not all files need all these suppressions
        "KotlinRedundantDiagnosticSuppress",
        // necessary because public keyword cannot be removed
        "RedundantVisibilityModifier",
        // the warning occurs if a deprecated function uses a deprecated type as parameter type
        "DEPRECATION",
        // for data classes with params of experimental types, the warning doesn't go away by
        // annotating the relevant property/constructor-arg with experimental annotation. The whole class/constructor
        // would need to be annotated as experimental, which is not desirable
        "OPT_IN_USAGE",
        // we add @SerializableWith on each sub-object in forward-compatible enum interfaces to avoid issues when using
        // the serializers of the subtypes directly. The serialization plugin complains because we're using the parent
        // interface serializer on each subtype instead of a KSerializer<Subtype>, which is technically not safe in
        // general. We accept the tradeoff in our case, which is that this will throw ClassCaseException:
        // val value: AXPropertyName.level = Json.decodeFromString<AXPropertyName.level>("\"url\"")
        "SERIALIZER_TYPE_INCOMPATIBLE",
    )

    @Suppress("SameParameterValue")
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
