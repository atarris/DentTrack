package com.stocktrack.app.web

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** Header names are in lower case. */
    val headers: Map<String, String>,
    val body: ByteArray,
    val remoteAddress: String
)

class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val headers: List<Pair<String, String>> = emptyList()
)

/**
 * A very small HTTP/1.1 server: one request per connection, with limits on size and time, so that
 * a slow or broken client cannot tie up the tablet. Only clients for which [allowClient] is true
 * are answered.
 */
class HttpServer(
    private val port: Int,
    private val allowClient: (InetAddress) -> Boolean,
    private val handler: (HttpRequest) -> HttpResponse
) {
    private var socket: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null

    /** The port really in use (differs from [port] when that was 0, as in the tests). */
    val boundPort: Int get() = socket?.localPort ?: -1

    /** Throws when the port cannot be opened. */
    fun start() {
        val server = ServerSocket()
        server.reuseAddress = true
        // IPv4 only: that is what a clinic network hands out, and it keeps the address check simple.
        server.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), BACKLOG)
        val workers = ThreadPoolExecutor(
            WORKERS, WORKERS, 30, TimeUnit.SECONDS, ArrayBlockingQueue(QUEUE)
        ).apply { allowCoreThreadTimeOut(true) }
        socket = server
        pool = workers
        Thread({ acceptLoop(server, workers) }, "DentTrack-web").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { socket?.close() }
        pool?.shutdownNow()
        socket = null
        pool = null
    }

    private fun acceptLoop(server: ServerSocket, workers: ThreadPoolExecutor) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                return // closed by stop()
            }
            if (!allowClient(client.inetAddress)) {
                runCatching { client.close() }
                continue
            }
            try {
                workers.execute { serve(client) }
            } catch (e: RejectedExecutionException) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = READ_TIMEOUT_MS
                val deadline = System.currentTimeMillis() + TOTAL_TIMEOUT_MS
                val response = try {
                    val request = readRequest(socket.getInputStream(), socket.inetAddress.hostAddress.orEmpty(), deadline)
                    runCatching { handler(request) }.getOrElse { plain(500, "Server error") }
                } catch (e: BadRequest) {
                    plain(e.status, e.message.orEmpty())
                }
                write(socket, response)
            }
        }
    }

    private class BadRequest(val status: Int, message: String) : Exception(message)

    private fun readRequest(input: InputStream, remote: String, deadline: Long): HttpRequest {
        val head = readHead(input, deadline)
        val lines = String(head, Charsets.ISO_8859_1).split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.") || !parts[1].startsWith("/")) {
            throw BadRequest(400, "Bad request")
        }
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        if (headers.containsKey("transfer-encoding")) throw BadRequest(411, "Length required")
        val length = headers["content-length"]?.let { it.toIntOrNull() ?: throw BadRequest(400, "Bad request") } ?: 0
        if (length < 0) throw BadRequest(400, "Bad request")
        if (length > MAX_BODY) throw BadRequest(413, "Too large")
        val body = readBody(input, length, deadline)

        val target = parts[1]
        val mark = target.indexOf('?')
        val path = if (mark < 0) target else target.substring(0, mark)
        val query = if (mark < 0) emptyMap() else parseQuery(target.substring(mark + 1))
        return HttpRequest(parts[0], path, query, headers, body, remote)
    }

    /** Reads up to the blank line that ends the headers, and leaves the body in the stream. */
    private fun readHead(input: InputStream, deadline: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            if (System.currentTimeMillis() > deadline) throw BadRequest(408, "Timeout")
            val b = input.read()
            if (b < 0) throw BadRequest(400, "Bad request")
            if (out.size() >= MAX_HEAD) throw BadRequest(431, "Headers too large")
            out.write(b)
            matched = when {
                b == END_OF_HEAD[matched].toInt() -> matched + 1
                b == END_OF_HEAD[0].toInt() -> 1
                else -> 0
            }
        }
        return out.toByteArray().copyOf(out.size() - 4)
    }

    private fun readBody(input: InputStream, length: Int, deadline: Long): ByteArray {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            if (System.currentTimeMillis() > deadline) throw BadRequest(408, "Timeout")
            val n = input.read(body, read, length - read)
            if (n < 0) throw BadRequest(400, "Bad request")
            read += n
        }
        return body
    }

    private fun parseQuery(text: String): Map<String, String> =
        text.split("&").mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            runCatching {
                URLDecoder.decode(pair.substring(0, eq), "UTF-8") to URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            }.getOrNull()
        }.toMap()

    private fun plain(status: Int, text: String) =
        HttpResponse(status, "text/plain; charset=utf-8", text.toByteArray(Charsets.UTF_8))

    private fun write(socket: Socket, response: HttpResponse) {
        val head = StringBuilder()
            .append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
            .append("Content-Type: ").append(response.contentType).append("\r\n")
            .append("Content-Length: ").append(response.body.size).append("\r\n")
            .append("Connection: close\r\n")
        response.headers.forEach { (name, value) -> head.append(name).append(": ").append(value).append("\r\n") }
        head.append("\r\n")
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(response.body)
        out.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        411 -> "Length Required"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        else -> "Internal Server Error"
    }

    private companion object {
        const val BACKLOG = 16
        const val WORKERS = 4
        const val QUEUE = 16
        const val MAX_HEAD = 16 * 1024
        const val MAX_BODY = 64 * 1024
        const val READ_TIMEOUT_MS = 10_000
        const val TOTAL_TIMEOUT_MS = 20_000L
        val END_OF_HEAD = "\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
    }
}

/** True for clients on the tablet's own network; requests from anywhere else are never answered. */
fun isLocalNetwork(address: InetAddress): Boolean =
    address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
