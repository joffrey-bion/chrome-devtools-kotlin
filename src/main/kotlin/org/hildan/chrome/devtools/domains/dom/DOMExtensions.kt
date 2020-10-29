package org.hildan.chrome.devtools.domains.dom

/**
 * Retrieves the ID of the root node of the current document.
 */
suspend fun DOMDomain.getDocumentRootNodeId(): NodeId = getDocument(GetDocumentRequest()).root.nodeId

/**
 * Retrieves the ID of the node corresponding to the given [selector], or null if not found.
 *
 * Note that the returned [NodeId] cannot really be used to retrieve actual node information, and this is apparently
 * [by design of the DOM domain](https://github.com/ChromeDevTools/devtools-protocol/issues/20).
 * It can be used to perform other CDP commands that require a [NodeId], though.
 */
suspend fun DOMDomain.findNodeBySelector(selector: String): NodeId? {
    val rootNodeId = getDocumentRootNodeId()
    val response = querySelector(QuerySelectorRequest(rootNodeId, selector))
    return if (response.nodeId == 0) null else response.nodeId
}

/**
 * Moves the focus to the node corresponding to the given [selector], or null if not found.
 */
suspend fun DOMDomain.focusNodeBySelector(selector: String) {
    val nodeId = findNodeBySelector(selector) ?: error("Cannot focus: no node found using selector '$selector'")
    focus(FocusRequest(nodeId = nodeId))
}
