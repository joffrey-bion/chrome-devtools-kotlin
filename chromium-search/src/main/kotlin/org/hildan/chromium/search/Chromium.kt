package org.hildan.chromium.search

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*

fun main(): Unit = runBlocking {
    val response = ChromiumSearch().search("""session\s*->\s*CreateAndAddHandler""")
    response.searchResults.forEach { searchResult ->
        val fileResult = searchResult.fileSearchResult

        val targetType = fileResult.targetTypeName()

        val domainNames = fileResult.snippets.flatMap { it.snippetLines }.map { extractDomainName(it.lineText!!) }
        println("$targetType : $domainNames")
    }
}

private fun FileSearchResult.targetTypeName() = fileSpec.path
    .removePrefix("content/browser/devtools/")
    .removeSuffix("_devtools_agent_host.cc")
    .snakeToCamelCase()

private fun String.snakeToCamelCase(): String = replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
    .replaceFirstChar { it.titlecase() }

private val regex = Regex(""".*session\s*->\s*CreateAndAddHandler<protocol::(.*)Handler>.*""")

private fun extractDomainName(lineText: String): String {
    val match = regex.matchEntire(lineText) ?: error("Domain regex didn't match '$lineText'")
    return match.groupValues[1]
}

private const val grimoireApiKey = "AIzaSyCqPSptx9mClE5NU4cpfzr6cgdO_phV1lM"

class ChromiumSearch(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    },
) {
    private val baseUrl = "https://grimoireoss-pa.clients6.google.com/v1"

    suspend fun search(textQuery: String) = search(chromiumCodeQuery(textQuery))

    suspend fun search(searchQuery: SearchQuery): SearchResponse {
        val response = httpClient.post("$baseUrl/contents/search?alt=json&key=$grimoireApiKey") {
            headers {
                contentType(ContentType.Application.Json)
                append(HttpHeaders.Referrer, "https://source.chromium.org/")
            }
            setBody(searchQuery)
        }
        return response.body()
    }
}

private fun chromiumCodeQuery(query: String) = SearchQuery(
    queryString = query,
    searchOptions = SearchOptions(
        numberOfContextLines = 0, // limits to only the matching lines
        pageSize = 25, // the page size is for the number of results, which are the *files* that match (not lines)
        repositoryScope = RepositoryScope(
            root = Root(ossProject = "chromium"),
        ),
    ),
    snippetOptions = SnippetOptions(
        minSnippetLinesPerFile = 100, // number of "uncollapsed" snippets, so effectively a max num of snippets returned
        minSnippetLinesPerPage = 100, // these seem to be max, not min
        numberOfContextLines = 0, // limits to only the matching lines
    ),
)
