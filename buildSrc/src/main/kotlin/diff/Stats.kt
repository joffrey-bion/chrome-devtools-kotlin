package org.hildan.chrome.devtools.build.diff

import kotlinx.serialization.Serializable

@Serializable
internal data class DomainDiffStats(
    val commandStats: CommandDiffStats,
    val eventStats: ParamDiffStats,
) {
    operator fun plus(other: DomainDiffStats) = DomainDiffStats(
        commandStats = commandStats + other.commandStats,
        eventStats = eventStats + other.eventStats,
    )

    companion object {
        val ZERO = DomainDiffStats(commandStats = CommandDiffStats.ZERO, eventStats = ParamDiffStats.ZERO)
    }
}

@Serializable
internal data class CommandDiffStats(
    val inputParams: ParamDiffStats,
    val outputParams: ParamDiffStats,
) {
    operator fun plus(other: CommandDiffStats) = CommandDiffStats(
        inputParams = inputParams + other.inputParams,
        outputParams = outputParams + other.outputParams,
    )

    companion object {
        val ZERO = CommandDiffStats(inputParams = ParamDiffStats.ZERO, outputParams = ParamDiffStats.ZERO)
    }
}

@Serializable
internal data class ParamDiffStats(
    val added: AddedOrRemovedStats,
    val removed: AddedOrRemovedStats,
    val changed: ChangedParamsStats,
    val maxMandatoryParams: Int,
    val zeroToSomeParams: Int,
    val someToZeroParams: Int,
    val zeroToSomeOptional: Int,
    val someToZeroOptional: Int,
) {
    operator fun plus(other: ParamDiffStats) = ParamDiffStats(
        added = added + other.added,
        removed = removed + other.removed,
        changed = changed + other.changed,
        maxMandatoryParams = maxOf(maxMandatoryParams, other.maxMandatoryParams),
        zeroToSomeParams = zeroToSomeParams + other.zeroToSomeParams,
        someToZeroParams = someToZeroParams + other.someToZeroParams,
        zeroToSomeOptional = zeroToSomeOptional + other.zeroToSomeOptional,
        someToZeroOptional = someToZeroOptional + other.someToZeroOptional,
    )

    companion object {
        val ZERO = ParamDiffStats(
            added = AddedOrRemovedStats.ZERO,
            removed = AddedOrRemovedStats.ZERO,
            changed = ChangedParamsStats.ZERO,
            maxMandatoryParams = 0,
            zeroToSomeParams = 0,
            someToZeroParams = 0,
            zeroToSomeOptional = 0,
            someToZeroOptional = 0,
        )
    }
}

@Serializable
internal data class AddedOrRemovedStats(
    val mandatoryNonXp: Int,
    val mandatoryXp: Int, // should never happen
    val optionalNonXp: Int,
    val optionalXp: Int,
) {
    operator fun plus(other: AddedOrRemovedStats) = AddedOrRemovedStats(
        mandatoryNonXp = mandatoryNonXp + other.mandatoryNonXp,
        mandatoryXp = mandatoryXp + other.mandatoryXp,
        optionalNonXp = optionalNonXp + other.optionalNonXp,
        optionalXp = optionalXp + other.optionalXp,
    )

    companion object {
        val ZERO = AddedOrRemovedStats(0, 0, 0, 0)
    }
}

@Serializable
internal data class ChangedParamsStats(
    val mandatoryToOptional: Int,
    val optionalToMandatoryNonXp: Int,
    val optionalToMandatoryXp: Int,
    val noLongerExperimental: Int,
    val becameExperimental: Int,
) {
    operator fun plus(other: ChangedParamsStats) = ChangedParamsStats(
        mandatoryToOptional = mandatoryToOptional + other.mandatoryToOptional,
        optionalToMandatoryNonXp = optionalToMandatoryNonXp + other.optionalToMandatoryNonXp,
        optionalToMandatoryXp = optionalToMandatoryXp + other.optionalToMandatoryXp,
        noLongerExperimental = noLongerExperimental + other.noLongerExperimental,
        becameExperimental = becameExperimental + other.becameExperimental,
    )

    companion object {
        val ZERO = ChangedParamsStats(0, 0, 0, 0, 0)
    }
}
