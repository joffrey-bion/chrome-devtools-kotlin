package org.hildan.chrome.devtools.sessions

import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.targets.*

/**
 * Finds page targets that were opened by this page.
 */
suspend fun PageSession.childPages(): List<TargetInfo> {
    val thisTargetId = metaData.targetId
    return target.getTargets().targetInfos.filter { it.type == TargetTypeNames.page && it.openerId == thisTargetId }
}
