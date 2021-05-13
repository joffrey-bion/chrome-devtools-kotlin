package org.hildan.chrome.devtools.domains.dom

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Retrieves the root [Node] of the current document.
 */
suspend fun DOMDomain.getDocumentRoot(): Node = getDocument(GetDocumentRequest()).root

/**
 * Retrieves the ID of the root node of the current document.
 */
suspend fun DOMDomain.getDocumentRootNodeId(): NodeId = getDocumentRoot().nodeId

/**
 * Retrieves the ID of the node corresponding to the given [selector], or null if not found.
 *
 * Note that the returned [NodeId] cannot really be used to retrieve actual node information, and this is apparently
 * [by design of the DOM domain](https://github.com/ChromeDevTools/devtools-protocol/issues/20).
 * It can be used to perform other CDP commands that require a [NodeId], though.
 */
suspend fun DOMDomain.findNodeBySelector(selector: String): NodeId? =
    querySelectorOnNode(getDocumentRootNodeId(), selector)

/**
 * Retrieves the ID of the node corresponding to the given [selector], and retries until there is a match using the
 * given [pollingPeriod].
 *
 * This method may suspend forever if the [selector] never matches any node.
 * The caller is responsible for using [withTimeout][kotlinx.coroutines.withTimeout] or similar cancellation mechanisms
 * around calls to this method if handling this case is necessary.
 *
 * Note that the returned [NodeId] cannot really be used to retrieve actual node information, and this is apparently
 * [by design of the DOM domain](https://github.com/ChromeDevTools/devtools-protocol/issues/20).
 * It can be used to perform other CDP commands that require a [NodeId], though.
 */
@ExperimentalTime
suspend fun DOMDomain.awaitNodeBySelector(selector: String, pollingPeriod: Duration): NodeId =
    awaitNodeBySelector(selector, pollingPeriod.toLongMilliseconds())

/**
 * Retrieves the ID of the node corresponding to the given [selector], and retries until there is a match using the
 * given [pollingPeriodMillis].
 *
 * This method may suspend forever if the [selector] never matches any node.
 * The caller is responsible for using [withTimeout][kotlinx.coroutines.withTimeout] or similar cancellation mechanisms
 * around calls to this method if handling this case is necessary.
 *
 * Note that the returned [NodeId] cannot really be used to retrieve actual node information, and this is apparently
 * [by design of the DOM domain](https://github.com/ChromeDevTools/devtools-protocol/issues/20).
 * It can be used to perform other CDP commands that require a [NodeId], though.
 */
suspend fun DOMDomain.awaitNodeBySelector(selector: String, pollingPeriodMillis: Long = 200): NodeId {
    while (coroutineContext.isActive) {
        // it looks like we do need to get a new document at each poll otherwise we may not see the new nodes
        val nodeId = findNodeBySelector(selector)
        if (nodeId != null) {
            return nodeId
        }
        delay(pollingPeriodMillis)
    }
    error("Cancelled while awaiting node by selector \"$selector\"")
}

private suspend fun DOMDomain.querySelectorOnNode(nodeId: NodeId, selector: String): NodeId? {
    val response = querySelector(QuerySelectorRequest(nodeId, selector))
    return if (response.nodeId == 0) null else response.nodeId
}

/**
 * Retrieves the ID of the node corresponding to the given [selector], or throw an exception if not found.
 *
 * Note that the returned [NodeId] cannot really be used to retrieve actual node information, and this is apparently
 * [by design of the DOM domain](https://github.com/ChromeDevTools/devtools-protocol/issues/20).
 * It can be used to perform other CDP commands that require a [NodeId], though.
 */
suspend fun DOMDomain.getNodeBySelector(selector: String): NodeId =
    findNodeBySelector(selector) ?: error("DOM node not found with selector: $selector")

/**
 * Moves the focus to the node corresponding to the given [selector], or null if not found.
 */
suspend fun DOMDomain.focusNodeBySelector(selector: String) {
    val nodeId = findNodeBySelector(selector) ?: error("Cannot focus: no node found using selector '$selector'")
    focus(FocusRequest(nodeId = nodeId))
}

/**
 * Gets the attributes of the node corresponding to the given [nodeSelector], or null if the selector didn't match
 * any node.
 */
suspend fun DOMDomain.getAttributes(nodeSelector: String): DOMAttributes? =
    findNodeBySelector(nodeSelector)?.let { nodeId -> getAttributes(nodeId) }

/**
 * Gets the attributes of the node corresponding to the given [nodeId].
 */
suspend fun DOMDomain.getAttributes(nodeId: NodeId): DOMAttributes =
    getAttributes(GetAttributesRequest(nodeId)).attributes.asDOMAttributes()

/**
 * Gets the value of the attribute [attributeName] of the node corresponding to the given [nodeSelector], or null if
 * the selector didn't match any node or if the attribute was not present on the node.
 */
suspend fun DOMDomain.getAttributeValue(nodeSelector: String, attributeName: String): String? =
    getAttributes(nodeSelector)?.get(attributeName)

/**
 * Gets the value of the attribute [attributeName] of the node corresponding to the given [nodeId], or null if
 * the attribute was not present on the node.
 */
suspend fun DOMDomain.getAttributeValue(nodeId: NodeId, attributeName: String): String? =
    getAttributes(nodeId)[attributeName]

/**
 * Sets the attribute of the given [name] to the given [value] on the node corresponding to the given [nodeSelector].
 * Throws an exception if the selector didn't match any node.
 */
suspend fun DOMDomain.setAttributeValue(nodeSelector: String, name: String, value: String) {
    setAttributeValue(nodeId = getNodeBySelector(nodeSelector), name, value)
}

/**
 * Sets the attribute of the given [name] to the given [value] on the node corresponding to the given [nodeId].
 */
suspend fun DOMDomain.setAttributeValue(nodeId: NodeId, name: String, value: String) {
    setAttributeValue(SetAttributeValueRequest(nodeId, name, value))
}

/**
 * Returns boxes for the node corresponding to the given [selector], or null if the selector didn't match any node.
 */
suspend fun DOMDomain.getBoxModel(selector: String): BoxModel? = findNodeBySelector(selector)?.let { getBoxModel(it) }

/**
 * Returns boxes for the node corresponding to the given [nodeId].
 *
 * [Official doc](https://chromedevtools.github.io/devtools-protocol/tot/DOM/#method-getBoxModel)
 */
suspend fun DOMDomain.getBoxModel(nodeId: NodeId): BoxModel = getBoxModel(GetBoxModelRequest(nodeId)).model
