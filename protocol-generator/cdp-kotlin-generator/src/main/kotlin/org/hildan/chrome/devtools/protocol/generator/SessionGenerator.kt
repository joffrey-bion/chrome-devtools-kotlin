package org.hildan.chrome.devtools.protocol.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.protocol.json.TargetType
import org.hildan.chrome.devtools.protocol.names.ExtDeclarations

fun createSessionInterfacesFileSpec(allTargets: List<TargetType>): FileSpec =
    FileSpec.builder(ExtDeclarations.sessionsPackage, ExtDeclarations.sessionsFileName).apply {
        allTargets.forEach {
            addType(createSessionInterface(it))
        }
    }.build()

private fun createSessionInterface(target: TargetType): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.sessionInterface(target)).apply {
        addKdoc("A session created when attaching to a target of type ${target.kotlinName}.")
        addKdoc("\n\n")
        // using concatenated strings here so KotlinPoet handles line breaks when it sees fit
        // (this is important when using dynamic type names in the sentences)
        addKdoc(
            format = "The subset of domains available for this target type is not strictly defined by the protocol. " +
                "The subset provided in this interface is guaranteed to work on this target type. " +
                "However, some domains might be missing in this interface while being effectively supported by the " +
                "target. " +
                "If this is the case, you can use the [%N] function to access all domains.",
            ExtDeclarations.childSessionUnsafeFun,
        )
        addKdoc("\n\n")
        addKdoc(
            format = "As a subinterface of [%T], it inherits the generated domain properties that match the latest " +
                "Chrome DevToolsProtocol definitions. " +
                "As such, it is not stable for inheritance, as new properties can be added without major version bump" +
                " when the protocol changes. " +
                "It is however safe to use all non-experimental and non-deprecated domains defined here. " +
                "The experimental and deprecation cycles of the protocol are reflected in this interface with the " +
                "same guarantees.",
            ExtDeclarations.targetInterface(target),
        )
        addSuperinterface(ExtDeclarations.childSessionInterface)
        addSuperinterface(ExtDeclarations.targetInterface(target))
    }.build()

fun createSessionAdaptersFileSpec(childTargets: List<TargetType>): FileSpec =
    FileSpec.builder(ExtDeclarations.sessionsPackage, ExtDeclarations.sessionAdaptersFileName).apply {
        childTargets.forEach {
            addFunction(createAdapterExtension(it))
            addType(createSessionAdapterClass(it))
        }
    }.build()

private fun createSessionAdapterClass(target: TargetType): TypeSpec =
    TypeSpec.classBuilder(ExtDeclarations.sessionAdapter(target)).apply {
        addKdoc(
            "An adapter from a generic [%T] to a [%T].",
            ExtDeclarations.childSessionInterface,
            ExtDeclarations.sessionInterface(target)
        )
        addModifiers(KModifier.PRIVATE)

        val sessionArg = "session"
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(sessionArg, ExtDeclarations.childSessionInterface)
                .build()
        )

        val targetInterface = ExtDeclarations.targetInterface(target)
        addSuperinterface(ExtDeclarations.sessionInterface(target))
        addSuperinterface(ExtDeclarations.childSessionInterface, delegate = CodeBlock.of("%N", sessionArg))
        addSuperinterface(targetInterface, delegate = CodeBlock.of("%N.unsafe()", sessionArg))

        addInitializerBlock(
            CodeBlock.of(
                """
                val targetType = %N.metaData.targetType
                require(targetType in %T.supportedCdpTargets) {
                    %P
                }
                """.trimIndent(),
                sessionArg,
                targetInterface,
                CodeBlock.of(
                    "Cannot initiate a ${target.kotlinName} session with a target of type ${'$'}targetType (target ID: ${'$'}{%N.metaData.targetId})",
                    sessionArg
                )
            )
        )
    }.build()

private fun createAdapterExtension(target: TargetType): FunSpec {
    val sessionInterface = ExtDeclarations.sessionInterface(target)
    return FunSpec.builder("as${sessionInterface.simpleName}").apply {
        receiver(ExtDeclarations.childSessionInterface)
        addKdoc(
            "Adapts this [%T] to a [%T]. If the attached target is not of a compatible type, this function throws an exception.",
            ExtDeclarations.childSessionInterface,
            sessionInterface,
        )
        addCode("return %T(this)", ExtDeclarations.sessionAdapter(target))
        returns(sessionInterface)
    }.build()
}
