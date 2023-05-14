package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.build.json.TargetType
import org.hildan.chrome.devtools.build.names.ExtDeclarations

fun createSessionInterfacesFileSpec(allTargets: List<TargetType>): FileSpec =
    FileSpec.builder(ExtDeclarations.sessionsPackage, ExtDeclarations.sessionsFileName).apply {
        allTargets.forEach {
            addType(createSessionInterface(it))
        }
    }.build()

private fun createSessionInterface(target: TargetType): TypeSpec =
    TypeSpec.interfaceBuilder(ExtDeclarations.sessionInterface(target)).apply {
        addKdoc("A session created when attaching to a target of type ${target.name}.")
        addSuperinterface(ExtDeclarations.childSessionInterface)
        addSuperinterface(ExtDeclarations.targetInterface(target))
    }.build()

fun createSessionAdaptersFileSpec(allTargets: List<TargetType>): FileSpec =
    FileSpec.builder(ExtDeclarations.sessionsPackage, ExtDeclarations.sessionAdaptersFileName).apply {
        allTargets.forEach {
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
                    %S
                }
                """.trimIndent(),
                sessionArg,
                targetInterface,
                CodeBlock.of(
                    "Cannot initiate a ${target.name} session with a target of type ${'$'}targetType (target ID: ${'$'}{%N.metaData.targetId})",
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