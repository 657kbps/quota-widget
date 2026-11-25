package com.kuyermqi.quotawidget.domain.commandcode

import android.util.Log
import com.kuyermqi.quotawidget.commandcode.CommandCodeCallback
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Command Code CLI completes its browser sign-in by POSTing the API key
 * to a loopback callback server. Android has no such server, so the in-app
 * WebView flow hosts one for the duration of the sign-in.
 *
 * Binds an ephemeral port in [start] so [port] can be handed to the login page,
 * accepts on a dedicated daemon thread and serves each connection on a bounded
 * daemon pool so idle browser preconnects cannot stall real callbacks.
 */
internal class CommandCodeCallbackServer(
    private val expectedState: String,
    private val onCallback: (CommandCodeCallback) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val connections = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(
        WORKER_THREADS,
        WORKER_THREADS,
        WORKER_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "command-code-callback-worker").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { prestartAllCoreThreads() }

    @Volatile
    private var socket: ServerSocket? = null

    /** Loopback port actually bound by [start]; 0 until then. */
    @Volatile
    var port: Int = 0
        private set

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val server = try {
            ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
        } catch (e: IOException) {
            fail(e)
            return
        }
        socket = server
        port = server.localPort
        Thread({ acceptLoop(server) }, "command-code-callback").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket?.close() }
        socket = null
        workers.shutdownNow()
        connections.forEach { runCatching { it.close() } }
        connections.clear()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!closed.get()) {
            val connection = try {
                server.accept()
            } catch (e: IOException) {
                if (closed.get()) return
                fail(e)
                return
            }
            if (closed.get()) {
                runCatching { connection.close() }
                return
            }
            connections.add(connection)
            try {
                workers.execute { serve(connection) }
            } catch (e: RejectedExecutionException) {
                connections.remove(connection)
                runCatching { connection.close() }
            }
        }
    }

    private fun serve(connection: Socket) {
        try {
            connection.soTimeout = READ_TIMEOUT_MS
            connection.use { client ->
                try {
                    handle(client)
                } catch (e: RejectedRequest) {
                    respond(client, e.status, FAILURE_HTML)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "dropped callback connection: ${e.message}")
        } finally {
            connections.remove(connection)
        }
    }

    private fun handle(connection: Socket) {
        val input = BufferedInputStream(connection.getInputStream())
        val headerBudget = HeaderBudget(MAX_HEADER_BYTES)
        val requestLine = readLine(input, headerBudget) ?: return
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val fullPath = parts.getOrNull(1).orEmpty()
        val path = fullPath.substringBefore('?')
        val query = fullPath.substringAfter('?', missingDelimiterValue = "")
        val headers = mutableMapOf<String, String>()
        var headerLines = 0
        while (true) {
            val line = readLine(input, headerBudget) ?: break
            headerLines += 1
            if (headerLines > MAX_HEADER_LINES) throw RejectedRequest(HTTP_BAD_REQUEST)
            if (line.isEmpty()) break
            val index = line.indexOf(':')
            if (index > 0) {
                headers[line.substring(0, index).trim().lowercase()] =
                    line.substring(index + 1).trim()
            }
        }
        val body = readBody(input, headers["content-length"])
        when {
            method == "OPTIONS" -> respond(connection, 204, "")
            (method == "POST" && path == "/callback") ||
                (method == "GET" && path == "/callback" && query.isNotBlank()) ->
                handleCallback(connection, headers, body.ifBlank { query })
            method == "GET" && path == "/callback/complete" ->
                respond(connection, 200, SUCCESS_HTML)
            else -> respond(connection, 404, "Not Found")
        }
    }

    private fun handleCallback(
        connection: Socket,
        headers: Map<String, String>,
        body: String,
    ) {
        val contentType = headers["content-type"].orEmpty().lowercase()
        val looksLikeJson = body.trimStart().startsWith("{")
        val callback = if (contentType.startsWith("application/json") || looksLikeJson) {
            runCatching {
                val json = JSONObject(body)
                CommandCodeCallback(
                    apiKey = json.optString("apiKey"),
                    state = json.optString("state"),
                    userId = json.optString("userId"),
                    userName = json.optString("userName"),
                    keyName = json.optString("keyName"),
                )
            }.getOrNull()
        } else {
            CommandCodeClient.parseLoginCallback("http://127.0.0.1/callback?$body")
        }
        if (callback == null || callback.apiKey.isBlank()) {
            respond(connection, 400, FAILURE_HTML)
            return
        }
        if (callback.state != expectedState) {
            respond(connection, 403, FAILURE_HTML)
            onFailure("Command Code 登录状态校验失败，请重试")
            return
        }
        respond(connection, 200, SUCCESS_HTML)
        onCallback(callback)
    }

    private fun readBody(input: InputStream, contentLength: String?): String {
        if (contentLength == null) return ""
        val length = contentLength.toIntOrNull() ?: throw RejectedRequest(HTTP_BAD_REQUEST)
        if (length < 0) throw RejectedRequest(HTTP_BAD_REQUEST)
        if (length > MAX_BODY_BYTES) throw RejectedRequest(HTTP_PAYLOAD_TOO_LARGE)
        if (length == 0) return ""
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return bytes.decodeToString(0, offset)
    }

    private fun readLine(input: InputStream, budget: HeaderBudget): String? {
        val bytes = ByteArrayOutputStream(LINE_BUFFER_HINT)
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toByteArray().decodeToString()
            budget.consume()
            if (value == '\n'.code) {
                val raw = bytes.toByteArray()
                val end = if (raw.isNotEmpty() && raw[raw.size - 1] == '\r'.code.toByte()) {
                    raw.size - 1
                } else {
                    raw.size
                }
                return raw.decodeToString(0, end)
            }
            bytes.write(value)
        }
    }

    private fun respond(connection: Socket, status: Int, body: String) {
        val bytes = body.encodeToByteArray()
        val header = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            connection.getOutputStream().write(header.encodeToByteArray())
            connection.getOutputStream().write(bytes)
            connection.getOutputStream().flush()
        }
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        413 -> "Content Too Large"
        431 -> "Request Header Fields Too Large"
        else -> "Not Found"
    }

    private fun fail(e: Exception) {
        if (closed.get()) return
        Log.e(TAG, "callback server failed: ${e.message}")
        onFailure("Command Code 本地回调服务启动失败，请重试")
    }

    private class HeaderBudget(private val limit: Int) {
        private var consumed = 0

        fun consume() {
            consumed += 1
            if (consumed > limit) throw RejectedRequest(HTTP_REQUEST_HEADER_TOO_LARGE)
        }
    }

    private class RejectedRequest(val status: Int) : RuntimeException()

    companion object {
        private const val TAG = "CommandCodeLogin"
        private const val LOOPBACK = "127.0.0.1"
        private const val BACKLOG = 8
        private const val WORKER_THREADS = 4
        private const val QUEUE_CAPACITY = 8
        private const val WORKER_KEEP_ALIVE_MS = 0L
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_HEADER_LINES = 64
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val LINE_BUFFER_HINT = 128
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private const val HTTP_REQUEST_HEADER_TOO_LARGE = 431

        val SUCCESS_HTML = "<!doctype html><meta charset=\"utf-8\"><title>Command Code</title>" +
            "<body style=\"font:15px system-ui;padding:3em;text-align:center\">" +
            "<h2>登录成功</h2><p>可以返回配额监控了。</p></body>"
        val FAILURE_HTML = "<!doctype html><meta charset=\"utf-8\"><title>Command Code</title>" +
            "<body style=\"font:15px system-ui;padding:3em;text-align:center\">" +
            "<h2>登录未完成</h2><p>请返回应用重试。</p></body>"
    }
}
