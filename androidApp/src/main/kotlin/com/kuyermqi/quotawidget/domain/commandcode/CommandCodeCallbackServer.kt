package com.kuyermqi.quotawidget.domain.commandcode

import android.util.Log
import com.kuyermqi.quotawidget.commandcode.CommandCodeCallback
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * The Command Code CLI completes its browser sign-in by POSTing the API key
 * to a loopback callback server. Android has no such server, so the in-app
 * WebView flow hosts one for the duration of the sign-in.
 */
internal class CommandCodeCallbackServer(
    private val port: Int,
    private val expectedState: String,
    private val onCallback: (CommandCodeCallback) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @Volatile
    private var socket: ServerSocket? = null
    @Volatile
    private var closed = false

    fun start() {
        Thread({
            try {
                val server = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
                socket = server
                while (!closed && !server.isClosed) {
                    val client = server.accept()
                    runCatching { handle(client) }
                }
            } catch (e: Exception) {
                if (!closed) {
                    Log.e(TAG, "callback server failed: ${e.message}")
                    onFailure("Command Code 本地回调服务启动失败，请重试")
                }
            }
        }, "command-code-callback").start()
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            val input = BufferedInputStream(connection.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val fullPath = parts.getOrNull(1).orEmpty()
            val path = fullPath.substringBefore('?')
            val query = fullPath.substringAfter('?', missingDelimiterValue = "")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val index = line.indexOf(':')
                if (index > 0) {
                    headers[line.substring(0, index).trim().lowercase()] =
                        line.substring(index + 1).trim()
                }
            }
            val body = readBody(input, headers["content-length"]?.toIntOrNull() ?: 0)
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
            val client = CommandCodeClient()
            try {
                client.parseLoginCallback("http://127.0.0.1/callback?$body")
            } finally {
                client.close()
            }
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

    private fun readBody(input: BufferedInputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return bytes.decodeToString(0, offset)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().decodeToString()
            if (value == '\n'.code) {
                var end = bytes.size
                if (end > 0 && bytes[end - 1] == '\r'.code.toByte()) end -= 1
                return bytes.subList(0, end).toByteArray().decodeToString()
            }
            bytes.add(value.toByte())
        }
    }

    private fun respond(connection: Socket, status: Int, body: String) {
        val statusText = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val bytes = body.encodeToByteArray()
        val header = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
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

    companion object {
        fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use {
            it.localPort
        }

        private const val TAG = "CommandCodeLogin"
        val SUCCESS_HTML = "<!doctype html><meta charset=\"utf-8\"><title>Command Code</title>" +
            "<body style=\"font:15px system-ui;padding:3em;text-align:center\">" +
            "<h2>登录成功</h2><p>可以返回配额监控了。</p></body>"
        val FAILURE_HTML = "<!doctype html><meta charset=\"utf-8\"><title>Command Code</title>" +
            "<body style=\"font:15px system-ui;padding:3em;text-align:center\">" +
            "<h2>登录未完成</h2><p>请返回应用重试。</p></body>"
    }
}
