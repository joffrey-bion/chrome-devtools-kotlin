package org.hildan.chrome.devtools.domains.target

import kotlinx.coroutines.flow.*
import org.hildan.chrome.devtools.domains.target.events.*
import org.hildan.chrome.devtools.protocol.*

/**
 * When collected, this flow enables target discovery and aggregates single-target events to provide updates to a
 * complete map of targets by ID.
 */
suspend fun TargetDomain.allTargetsFlow(): Flow<Map<TargetID, TargetInfo>> = events()
    .scan(emptyMap<TargetID, TargetInfo>()) { targets, event -> targets.updatedBy(event) }
    .onStart {
        // triggers target info events, including creation events for existing targets
        setDiscoverTargets(discover = true)
    }

@OptIn(ExperimentalChromeApi::class)
private fun Map<TargetID, TargetInfo>.updatedBy(event: TargetEvent): Map<TargetID, TargetInfo> = when (event) {
    is TargetEvent.TargetCreated -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetInfoChanged -> this + (event.targetInfo.targetId to event.targetInfo)
    is TargetEvent.TargetDestroyed -> this - event.targetId
    is TargetEvent.TargetCrashed -> this - event.targetId
    is TargetEvent.AttachedToTarget, //
    is TargetEvent.DetachedFromTarget, //
    is TargetEvent.ReceivedMessageFromTarget -> this // irrelevant events
}
