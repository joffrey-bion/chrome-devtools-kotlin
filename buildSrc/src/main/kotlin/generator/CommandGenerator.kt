package org.hildan.chrome.devtools.build.generator

import com.squareup.kotlinpoet.*
import org.hildan.chrome.devtools.build.model.ChromeDPCommand
import org.hildan.chrome.devtools.build.names.Annotations
import org.hildan.chrome.devtools.build.names.ExtDeclarations

internal fun ChromeDPCommand.createInputTypeSpec(): TypeSpec {
    return TypeSpec.classBuilder(names.inputTypeName).apply {
        addKdoc("Request object containing input parameters for the [%T.%N] command.",
            names.domain.domainClassName,
            names.methodName,
        )
        commonCommandType(this@createInputTypeSpec)
        addModifiers(KModifier.DATA)
        addPrimaryConstructorProps(parameters)

        if (parameters.any { it.optional }) {
            addType(createInputBuilderType())
        }
    }.build()
}

private fun ChromeDPCommand.createInputBuilderType(): TypeSpec {
    val cmd = this
    return TypeSpec.classBuilder(names.inputTypeBuilderName).apply {
        val (optionalProps, mandatoryProps) = cmd.parameters.partition { it.optional }
        addPrimaryConstructorProps(mandatoryProps)
        addProperties(optionalProps.map {
            it.toPropertySpec {
                mutable()
                initializer("null")
            }
        })

        val constructorCall = constructorCallTemplate(names.inputTypeName, cmd.parameters.map { it.name })
        addFunction(
            FunSpec.builder("build")
                .returns(names.inputTypeName)
                .addNamedCode("return ${constructorCall.template}", constructorCall.namedArgsMapping)
                .build()
        )
    }.build()
}

internal fun ChromeDPCommand.createOutputTypeSpec(): TypeSpec {
    val typeBuilder = if (returns.isEmpty()) {
        TypeSpec.objectBuilder(names.outputTypeName).apply {
            addKdoc("A dummy response object for the [%T.%N] command. This command doesn't return any result at " +
                "the moment, but this could happen in the future, or could have happened in the past. For forwards " +
                "and backwards compatibility of the command method, we still declare this class even without " +
                "properties.",
                names.domain.domainClassName,
                names.methodName,
            )
        }
    } else {
        TypeSpec.classBuilder(names.outputTypeName).apply {
            addKdoc("Response type for the [%T.%N] command.", names.domain.domainClassName, names.methodName)
            addModifiers(KModifier.DATA)
            addPrimaryConstructorProps(returns)
        }
    }
    return typeBuilder.apply {
        commonCommandType(this@createOutputTypeSpec)
    }.build()
}

private fun TypeSpec.Builder.commonCommandType(chromeDPCommand: ChromeDPCommand) {
    addAnnotation(Annotations.serializable)
    if (chromeDPCommand.deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (chromeDPCommand.experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
}

internal fun ChromeDPCommand.toNoArgFunctionSpec(sessionPropertyName: String): FunSpec =
    FunSpec.builder(names.methodName).apply {
        commonCommandFunction(command = this@toNoArgFunctionSpec)
        addStatement(
            "return %N.%M(%S, Unit)",
            sessionPropertyName,
            ExtDeclarations.sessionRequestExtension,
            names.fullCommandName,
        )
    }.build()

internal fun ChromeDPCommand.toFunctionSpecWithParams(sessionPropertyName: String): FunSpec =
    FunSpec.builder(names.methodName).apply {
        commonCommandFunction(command = this@toFunctionSpecWithParams)
        addKdoc(
            "\n\nNote: this function uses an input class, and constructing this class manually may lead to " +
                "incompatibilities if the class's contructor arguments change in the future. For maximum " +
                "compatibility, it is advised to use the overload of this function that directly takes the mandatory " +
                "parameters as arguments, and the optional ones from a configuration lambda."
        )
        val inputArg = ParameterSpec.builder(name = "input", type = names.inputTypeName).build()
        addParameter(inputArg)
        addStatement(
            "return %N.%M(%S, %N)",
            sessionPropertyName,
            ExtDeclarations.sessionRequestExtension,
            names.fullCommandName,
            inputArg,
        )
    }.build()

internal fun ChromeDPCommand.toDslFunctionSpec(): FunSpec {
    val (optionalParams, mandatoryParams) = parameters.partition { it.optional }
    return FunSpec.builder(names.methodName).apply {
        commonCommandFunction(command = this@toDslFunctionSpec)
        addParameters(mandatoryParams.map { it.toParameterSpec(includeDoc = true, includeAnnotations = true) })

        if (optionalParams.any()) {
            addAnnotation(Annotations.jvmOverloads)
            addModifiers(KModifier.INLINE)
            val initLambdaParam = initBuilderLambdaParam(names.inputTypeBuilderName, "optionalArgs") {
                defaultValue("{}")
            }
            addParameter(initLambdaParam)
            val builderConstructorCall = constructorCallTemplate(names.inputTypeBuilderName, mandatoryParams.map { it.name })
            addNamedCode("val builder = ${builderConstructorCall.template}\n", builderConstructorCall.namedArgsMapping)
            addStatement("val input = builder.apply(%N).build()", initLambdaParam)
        } else {
            val constructorCall = constructorCallTemplate(names.inputTypeName, mandatoryParams.map { it.name })
            addNamedCode("val input = ${constructorCall.template}\n", constructorCall.namedArgsMapping)
        }
        addStatement("return %N(input)", names.methodName)
    }.build()
}

private fun FunSpec.Builder.commonCommandFunction(command: ChromeDPCommand) {
    command.description?.let { addKdoc(it.escapeKDoc()) }
    addKdoc(linkToDoc(command.docUrl))
    if (command.deprecated) {
        addAnnotation(Annotations.deprecatedChromeApi)
    }
    if (command.experimental) {
        addAnnotation(Annotations.experimentalChromeApi)
    }
    addModifiers(KModifier.SUSPEND)
    returns(command.names.outputTypeName)
}

private fun initBuilderLambdaParam(
    builderTypeName: TypeName,
    name: String,
    configure: ParameterSpec.Builder.() -> Unit = {},
) = ParameterSpec.builder(name = name, type = lambdaTypeWithBuilderReceiver(builderTypeName)).apply(configure).build()
