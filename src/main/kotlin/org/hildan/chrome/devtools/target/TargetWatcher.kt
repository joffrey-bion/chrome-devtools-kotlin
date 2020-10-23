package org.hildan.chrome.devtools.target

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.hildan.chrome.devtools.domains.target.SetDiscoverTargetsRequest
import org.hildan.chrome.devtools.domains.target.TargetID
import org.hildan.chrome.devtools.domains.target.TargetInfo
import org.hildan.chrome.devtools.domains.target.events.TargetEvent
import org.hildan.chrome.devtools.domains.target.events.TargetEvent.*
import org.hildan.chrome.devtools.protocol.ChromeBrowserSession

/**
 * Watches the available targets in this browser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun ChromeBrowserSession.watchTargetsIn(coroutineScope: CoroutineScope): StateFlow<Map<TargetID, TargetInfo>> {
    val targetsFlow = MutableStateFlow(emptyMap<TargetID, TargetInfo>())

    target.events().onEach { targetsFlow.value = targetsFlow.value.updatedBy(it) }.launchIn(coroutineScope)

    coroutineScope.launch {
        // triggers target info events
        target.setDiscoverTargets(SetDiscoverTargetsRequest(discover = true))
    }
    return targetsFlow
}

private fun Map<TargetID, TargetInfo>.updatedBy(event: TargetEvent): Map<TargetID, TargetInfo> = when (event) {
    is TargetCreatedEvent -> if (event.targetInfo.type == "page") this + (event.targetInfo.targetId to event.targetInfo) else this
    is TargetInfoChangedEvent -> if (event.targetInfo.type == "page") this + (event.targetInfo.targetId to event.targetInfo) else this
    is TargetDestroyedEvent -> this - event.targetId
    is TargetCrashedEvent -> this - event.targetId
    is AttachedToTargetEvent, is DetachedFromTargetEvent, is ReceivedMessageFromTargetEvent -> this // irrelevant events
}