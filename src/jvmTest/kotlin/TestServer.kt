import com.sun.net.httpserver.*
import org.junit.jupiter.api.extension.*
import java.io.*
import java.net.*

class TestResourcesServerExtension : Extension, BeforeAllCallback, AfterAllCallback {
    private lateinit var httpServer: HttpServer

    val port: Int
        get() = httpServer.address.port

    override fun beforeAll(context: ExtensionContext?) {
        httpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10)
        httpServer.createContext("/") { exchange ->
            if (exchange.requestMethod != "GET") {
                exchange.respondInvalidMethod(listOf("GET"))
                return@createContext
            }
            val resourcePath = exchange.requestURI.path.removePrefix("/")
            exchange.respondWithResource(resourcePath)
        }
        httpServer.start()
    }

    override fun afterAll(context: ExtensionContext?) {
        httpServer.stop(0)
    }
}

/**
 * Creates an HTTP server that serves Java resources from the current program at '/'.
 */
suspend fun withResourceHttpServer(block: suspend (port: Int) -> Unit) {
    val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10)
    try {
        httpServer.createContext("/") { exchange ->
            if (exchange.requestMethod != "GET") {
                exchange.respondInvalidMethod(listOf("GET"))
                return@createContext
            }
            val resourcePath = exchange.requestURI.path.removePrefix("/")
            exchange.respondWithResource(resourcePath)
        }

        httpServer.start()

        block(httpServer.address.port)
    } finally {
        httpServer.stop(15)
    }
}

private fun HttpExchange.respondInvalidMethod(supportedMethods: List<String>) {
    responseHeaders.add("Allow", supportedMethods.joinToString(", "))
    sendResponse(code = 405)
}

private fun HttpExchange.sendResponse(code: Int, body: String? = null) {
    if (body == null) {
        sendResponseHeaders(code, -1)
        responseBody.close()
        return
    }
    val bytes = body.encodeToByteArray()
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.buffered().use { it.write(bytes) }
}

private fun HttpExchange.respondWithResource(resourcePath: String) {
    val resStream = ClassLoader.getSystemResourceAsStream(resourcePath)
    if (resStream == null) {
        sendResponseHeaders(404, -1)
        responseBody.close()
        return
    }
    sendResponseHeaders(200, 0)
    resStream.useAndWriteTo(responseBody)
}

private fun InputStream.useAndWriteTo(stream: OutputStream) {
    use { input -> input.copyTo(stream) }
    stream.flush()
    stream.close()
}
