package org.hildan.chrome.devtools.domains.dom

suspend fun DOMDomain.getRootNodeId(): NodeId {
    val document = getDocument(GetDocumentRequest())
    return document.root.nodeId
}

suspend fun DOMDomain.findNodeBySelector(selector: String): NodeId? {
    val rootNodeId = getRootNodeId()
    val response = querySelector(QuerySelectorRequest(rootNodeId, selector))
    return if (response.nodeId == 0) null else response.nodeId
}

suspend fun DOMDomain.focusNodeBySelector(selector: String) {
    val nodeId = findNodeBySelector(selector) ?: error("Cannot focus: no node found using selector '$selector'")
    focus(FocusRequest(nodeId = nodeId))
}
