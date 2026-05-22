package com.spop.poverlay.route

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset

data class RouteUploadPortalState(
    val isRunning: Boolean = false,
    val url: String? = null,
    val message: String? = null
)

class RouteUploadServer(
    private val routeStore: RouteStore,
    private val onImported: (ImportedRoute) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun start(scope: CoroutineScope): String {
        stop()
        val socket = ServerSocket(0)
        serverSocket = socket
        val url = "http://${localIpAddress()}:${socket.localPort}/"
        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        handleClient(client)
                    }
                }.onFailure {
                    if (!socket.isClosed) {
                        Timber.e(it, "Route upload server request failed")
                    }
                }
            }
        }
        return url
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        val input = client.getInputStream()
        val requestBytes = readHttpRequest(input)
        val headerEnd = requestBytes.indexOfHeaderEnd()
        if (headerEnd < 0) {
            client.writeResponse(400, "text/plain", "Bad request")
            return
        }

        val headerText = requestBytes.copyOfRange(0, headerEnd).toString(HeaderCharset)
        val requestLine = headerText.lineSequence().firstOrNull().orEmpty()
        when {
            requestLine.startsWith("GET / ") || requestLine.startsWith("GET / HTTP") -> {
                client.writeResponse(200, "text/html", uploadPage())
            }
            requestLine.startsWith("POST /upload ") || requestLine.startsWith("POST /upload?") -> {
                handleUpload(client, headerText, requestBytes.copyOfRange(headerEnd + 4, requestBytes.size))
            }
            else -> client.writeResponse(404, "text/plain", "Not found")
        }
    }

    private fun handleUpload(client: Socket, headerText: String, body: ByteArray) {
        val contentType = headerText.lineSequence()
            .firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }
            .orEmpty()
        val boundary = contentType.substringAfter("boundary=", missingDelimiterValue = "")
            .trim()
            .removeSurrounding("\"")
        if (boundary.isBlank()) {
            client.writeResponse(400, "text/html", resultPage("Could not read upload form boundary."))
            return
        }

        val upload = parseMultipartUpload(body, boundary)
        if (upload == null || upload.content.isBlank()) {
            client.writeResponse(400, "text/html", resultPage("Choose a GPX file and try again."))
            return
        }

        runCatching {
            routeStore.saveGpx(upload.fileName.substringBeforeLast('.'), upload.content)
        }.onSuccess { route ->
            onImported(route)
            onMessage("Imported ${route.name}")
            client.writeResponse(200, "text/html", resultPage("Imported ${route.name}. You can return to Switchback."))
            stop()
        }.onFailure {
            Timber.e(it, "Route portal import failed")
            onMessage("Could not import GPX route")
            client.writeResponse(400, "text/html", resultPage("Could not import that GPX file."))
        }
    }

    private fun parseMultipartUpload(body: ByteArray, boundary: String): UploadedGpx? {
        val text = body.toString(HeaderCharset)
        val marker = "--$boundary"
        val parts = text.split(marker)
        val filePart = parts.firstOrNull {
            it.contains("name=\"gpx\"", ignoreCase = true) ||
                    it.contains("name=\"file\"", ignoreCase = true)
        } ?: return null
        val headerEnd = filePart.indexOf("\r\n\r\n")
        if (headerEnd < 0) {
            return null
        }
        val partHeaders = filePart.substring(0, headerEnd)
        val fileName = partHeaders.substringAfter("filename=\"", "route.gpx")
            .substringBefore("\"")
            .ifBlank { "route.gpx" }
        val content = filePart.substring(headerEnd + 4)
            .removeSuffix("\r\n")
            .removeSuffix("--")
            .trim()
        return UploadedGpx(fileName = fileName, content = content)
    }

    private fun readHttpRequest(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val headerBytes = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var headerEnd = -1
        while (headerEnd < 0) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            output.write(buffer, 0, read)
            headerBytes.write(buffer, 0, read)
            headerEnd = output.toByteArray().indexOfHeaderEnd()
        }
        val requestSoFar = output.toByteArray()
        val headerText = if (headerEnd >= 0) {
            requestSoFar.copyOfRange(0, headerEnd).toString(HeaderCharset)
        } else {
            ""
        }
        val contentLength = headerText.lineSequence()
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val bodyRead = if (headerEnd >= 0) requestSoFar.size - (headerEnd + 4) else 0
        var remaining = (contentLength - bodyRead).coerceAtLeast(0).coerceAtMost(MaxUploadBytes)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) {
                break
            }
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun Socket.writeResponse(status: Int, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val statusText = if (status == 200) "OK" else "Error"
        val header = "HTTP/1.1 $status $statusText\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        getOutputStream().write(header.toByteArray(Charsets.UTF_8))
        getOutputStream().write(bodyBytes)
        getOutputStream().flush()
    }

    private fun uploadPage(): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Switchback Route Upload</title>
          <style>
            body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#09090b;color:#fff;margin:0;padding:32px}
            main{max-width:520px;margin:auto}
            h1{color:#34d399}
            input,button{font-size:18px;width:100%;box-sizing:border-box;margin-top:16px}
            input{padding:16px;background:#18181b;color:#fff;border:1px solid #3f3f46;border-radius:8px}
            button{padding:16px;border:0;border-radius:8px;background:#10b981;color:#000;font-weight:800}
            p{color:#a1a1aa;line-height:1.45}
          </style>
        </head>
        <body>
          <main>
            <h1>Switchback</h1>
            <h2>Add Route</h2>
            <p>Choose a GPX file from your phone or computer. Keep this device on the same Wi-Fi as the bike.</p>
            <form method="post" action="/upload" enctype="multipart/form-data">
              <input type="file" name="gpx" accept=".gpx,application/gpx+xml,text/xml">
              <button type="submit">Upload Route</button>
            </form>
          </main>
        </body>
        </html>
    """.trimIndent()

    private fun resultPage(message: String): String = """
        <!doctype html>
        <html>
        <head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Switchback</title></head>
        <body style="font-family:system-ui;background:#09090b;color:white;padding:32px">
          <h1 style="color:#34d399">Switchback</h1>
          <p style="font-size:20px">$message</p>
          <a style="color:#34d399" href="/">Upload another route</a>
        </body>
        </html>
    """.trimIndent()

    private fun localIpAddress(): String {
        NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                return@forEach
            }
            networkInterface.inetAddresses.toList().forEach { address ->
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: "127.0.0.1"
                }
            }
        }
        return "127.0.0.1"
    }

    private fun ByteArray.indexOfHeaderEnd(): Int {
        for (index in 0 until size - 3) {
            if (this[index] == '\r'.code.toByte() &&
                this[index + 1] == '\n'.code.toByte() &&
                this[index + 2] == '\r'.code.toByte() &&
                this[index + 3] == '\n'.code.toByte()
            ) {
                return index
            }
        }
        return -1
    }

    private data class UploadedGpx(
        val fileName: String,
        val content: String
    )

    private companion object {
        const val MaxUploadBytes = 8 * 1024 * 1024
        val HeaderCharset: Charset = Charset.forName("ISO-8859-1")
    }
}
