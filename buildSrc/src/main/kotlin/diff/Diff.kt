package org.hildan.chrome.devtools.build.diff

import org.hildan.chrome.devtools.protocol.json.*

internal fun diffDomains(oldDomains: List<JsonDomain>, newDomains: List<JsonDomain>): DomainDiffStats {
    val domainsDiff = diffBy(oldDomains, newDomains) { it.domain }
    if (domainsDiff.added.isNotEmpty()) {
        println("${domainsDiff.added.size} added domains: ${domainsDiff.added.map { it.domain }}")
    }
    if (domainsDiff.removed.isNotEmpty()) {
        println("${domainsDiff.removed.size} removed domains: ${domainsDiff.removed.map { it.domain }}")
    }
    return domainsDiff.common
        .map { (old, new) -> diffDomain(old, new) }
        .fold(DomainDiffStats.ZERO, DomainDiffStats::plus)
}

private fun diffDomain(oldDomain: JsonDomain, newDomain: JsonDomain) = DomainDiffStats(
    commandStats = diffCommands(oldDomain.commands, newDomain.commands),
    eventStats = diffEvents(oldDomain.events, newDomain.events),
)

private fun diffCommands(oldCommands: List<JsonDomainCommand>, newCommands: List<JsonDomainCommand>): CommandDiffStats {
    val commandsDiff = diffBy(oldCommands, newCommands) { it.name }
    if (commandsDiff.added.isNotEmpty()) {
        println("${commandsDiff.added.size} added commands: ${commandsDiff.added.map { it.name }}")
    }
    if (commandsDiff.removed.isNotEmpty()) {
        println("${commandsDiff.removed.size} removed commands: ${commandsDiff.removed.map { it.name }}")
    }
    return commandsDiff.common
        .map { (old, new) -> diffCommand(old, new) }
        .fold(CommandDiffStats.ZERO, CommandDiffStats::plus)
}

private fun diffCommand(oldCommand: JsonDomainCommand, newCommand: JsonDomainCommand) = CommandDiffStats(
    inputParams = diffParams(oldCommand.parameters, newCommand.parameters),
    outputParams = diffParams(oldCommand.returns, newCommand.returns),
)

private fun diffEvents(oldEvents: List<JsonDomainEvent>, newEvents: List<JsonDomainEvent>): ParamDiffStats {
    val eventsDiff = diffBy(oldEvents, newEvents) { it.name }
    return eventsDiff.common
        .map { (old, new) -> diffParams(old.parameters, new.parameters) }
        .fold(ParamDiffStats.ZERO, ParamDiffStats::plus)
}

private fun diffParams(oldParams: List<JsonDomainParameter>, newParams: List<JsonDomainParameter>): ParamDiffStats {
    val diff = diffBy(oldParams, newParams) { it.name }
    diff.common.filter { (old, new) -> !old.experimental && new.experimental }.forEach { (old, _) ->
        println("WARN: param ${old.name} became experimental")
    }
    return ParamDiffStats(
        added = diff.added.stats(),
        removed = diff.removed.stats(),
        changed = ChangedParamsStats(
            mandatoryToOptional = diff.common.count { (old, new) -> !old.optional && new.optional },
            optionalToMandatoryNonXp = diff.common.count { (old, new) ->
                (old.optional && !new.optional && !new.experimental).also {
                    if (it) {
                        check(!old.experimental) {
                            "param should not become non experimental and mandatory at the same time"
                        }
                    }
                }
            },
            optionalToMandatoryXp = diff.common.count { (old, new) ->
                (old.optional && !new.optional && new.experimental).also {
                    if (it) {
                        check(old.experimental) {
                            "param should not become experimental and mandatory at the same time"
                        }
                    }
                }
            },
            noLongerExperimental = diff.common.count { (old, new) -> old.experimental && !new.experimental },
            becameExperimental = diff.common.count { (old, new) -> !old.experimental && new.experimental },
        ),
        maxMandatoryParams = maxOf(oldParams.count { !it.optional }, newParams.count { !it.optional }),
        zeroToSomeParams = if (oldParams.none() && newParams.any()) 1 else 0,
        someToZeroParams = if (oldParams.any() && newParams.none()) 1 else 0,
        zeroToSomeOptional = if (oldParams.none { it.optional } && newParams.any { it.optional }) 1 else 0,
        someToZeroOptional = if (oldParams.any { it.optional } && newParams.none { it.optional }) 1 else 0,
    )
}

private fun Iterable<JsonDomainParameter>.stats() = AddedOrRemovedStats(
    mandatoryNonXp = count { !it.optional && !it.experimental },
    mandatoryXp =  count { !it.optional && it.experimental },
    optionalNonXp =  count { it.optional && !it.experimental },
    optionalXp =  count { it.optional && it.experimental },
)

private fun <T, K> diffBy(old: Iterable<T>, new: Iterable<T>, keySelector: (T) -> K): CollectionDiff<T> {
    val oldByKey = old.associateBy(keySelector)
    val newByKey = new.associateBy(keySelector)
    val commonKeys = oldByKey.keys intersect newByKey.keys
    return CollectionDiff(
        added = (newByKey - commonKeys).values,
        removed = (oldByKey - commonKeys).values,
        common = commonKeys.map { InBoth(old = oldByKey.getValue(it), new = newByKey.getValue(it)) },
    )
}

private data class CollectionDiff<T>(
    val added: Collection<T>,
    val removed: Collection<T>,
    val common: List<InBoth<T>>,
)

private data class InBoth<T>(
    val old: T,
    val new: T,
)
