package org.hildan.chromium.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse (
    val searchResults: List<SearchResult>,
    val estimatedResultCount: String,
    val exhaustive: Boolean,
    val requestToken: String
)

@Serializable
data class SearchResult (
    val fileSearchResult: FileSearchResult,
    val resultToken: String
)

@Serializable
data class FileSearchResult (
    val fileSpec: FileSpec,
    val snippets: List<Snippet>,
    val numberOfMatchingLines: Long
)

@Serializable
data class FileSpec (
    val sourceRoot: SourceRoot,
    val path: String,
    val type: String
)

@Serializable
data class SourceRoot (
    val repositoryKey: RepositoryKey,
    val refSpec: String
)

@Serializable
data class RepositoryKey (
    val repositoryName: String,
    val ossProject: String
)

@Serializable
data class Snippet (
    val snippetLines: List<SnippetLine>
)

@Serializable
data class SnippetLine (
    val lineText: String? = null,
    val matchingRanges: MatchingRanges,
    val lineNumber: String,
    val tokens: List<Token>? = null,
    val ranges: List<Range>? = null
)

@Serializable
data class MatchingRanges (
    val lineNumber: String,
    val columnRanges: List<Range>? = null
)

@Serializable
data class Range (
    val startIndex: Long? = null,
    val length: Long
)

@Serializable
data class Token (
    val tokenType: String,
    val range: Range
)
