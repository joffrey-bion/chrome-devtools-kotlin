package org.hildan.chromium.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchQuery(
    val queryString: String,
    val searchOptions: SearchOptions,
    val snippetOptions: SnippetOptions = SnippetOptions(),
)

@Serializable
data class SearchOptions(
    val enableDiagnostics: Boolean = false,
    val exhaustive: Boolean = false,
    val numberOfContextLines: Int = 1,
    val pageSize: Int = 10,
    val pageToken: String = "",
    val pathPrefix: String = "",
    val repositoryScope: RepositoryScope,
    val retrieveMultibranchResults: Boolean = false,
    val savedQuery: String = "",
    val scoringModel: String = "",
    val showPersonalizedResults: Boolean = false,
)

@Serializable
data class RepositoryScope(val root: Root)

@Serializable
data class Root(val ossProject: String)

@Serializable
data class SnippetOptions(
    val minSnippetLinesPerFile: Int = 10,
    val minSnippetLinesPerPage: Int = 60,
    val numberOfContextLines: Int = 1,
)
