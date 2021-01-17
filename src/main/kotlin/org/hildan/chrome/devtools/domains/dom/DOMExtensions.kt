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
