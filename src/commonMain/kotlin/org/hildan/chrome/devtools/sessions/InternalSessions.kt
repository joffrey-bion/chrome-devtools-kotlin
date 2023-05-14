package org.hildan.chrome.devtools.sessions

import org.hildan.chrome.devtools.domains.target.*
import org.hildan.chrome.devtools.protocol.ChromeDPSession
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.protocol.withSession
import org.hildan.chrome.devtools.targets.*
import org.hildan.chrome.devtools.targets.UberTarget

internal fun ChromeDPSession.toBrowserSession(): BrowserSession = BrowserSessionImpl(this)

private sealed class AbstractSession(
    protected val session: ChromeDPSession,
    protected val targetImplementation: UberTarget,
) : ChromeSession {

    override fun unsafe(): AllDomainsTarget = targetImplementation

    override suspend fun closeWebSocket() {
        session.closeWebSocket()
    }
}

private class BrowserSessionImpl(
    session: ChromeDPSession,
    targetImplementation: UberTarget = UberTarget(session),
) : AbstractSession(session, targetImplementation), BrowserSession, BrowserTarget by targetImplementation {

    @OptIn(ExperimentalChromeApi::class)
    override suspend fun attachToTarget(targetId: TargetID): ChildSession {
        // We use the "flatten" mode because it's required by our implementation of the protocol
        // (namely, we specify sessionId as part of the request frames directly, see RequestFrame)
        val sessionId = target.attachToTarget(targetId = targetId) { flatten = true }.sessionId
        val targetInfo = target.getTargetInfo { this.targetId = targetId }.targetInfo
        return ChildSessionImpl(
            session = session.connection.withSession(sessionId = sessionId),
            parent = this,
            metaData = MetaData(sessionId, targetInfo),
        )
    }

    override suspend fun close() {
        closeWebSocket()
    }
}

private class ChildSessionImpl(
    session: ChromeDPSession,
    override val parent: BrowserSession,
    override val metaData: MetaData,
    targetImplementation: UberTarget = UberTarget(session),
) : AbstractSession(session, targetImplementation),
    ChildSession,
    AllDomainsTarget by targetImplementation {

    override suspend fun detach() {
        parent.target.detachFromTarget { sessionId = session.sessionId }
    }

    @OptIn(ExperimentalChromeApi::class)
    override suspend fun close(keepBrowserContext: Boolean) {
        parent.target.closeTarget(targetId = metaData.targetId)

        if (!keepBrowserContext && !metaData.target.browserContextId.isNullOrEmpty()) {
            parent.target.disposeBrowserContext(metaData.target.browserContextId)
        }
    }
}

private data class MetaData(
    override val sessionId: SessionID,
    val target: TargetInfo,
) : SessionMetaData {
    override val targetId: TargetID
        get() = target.targetId

    override val targetType: String
        get() = target.type
}
