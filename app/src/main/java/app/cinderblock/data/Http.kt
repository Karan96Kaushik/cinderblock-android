package app.cinderblock.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

internal class HttpException(val code: Int, message: String) : Exception(message)

internal class StreamHandle {
    private val connection = AtomicReference<HttpURLConnection?>(null)
    fun attach(value: HttpURLConnection) {
        connection.set(value)
    }

    fun cancel() {
        connection.get()?.disconnect()
    }
}

internal fun httpRequest(
    url: String,
    method: String,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    handle: StreamHandle? = null,
    onChunk: ((String) -> Unit)? = null,
): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 20_000
        readTimeout = if (onChunk != null) 120_000 else 40_000
        doInput = true
        setRequestProperty("Accept", "application/json, text/plain, */*")
        setRequestProperty("User-Agent", "Cinderblock-Android")
        headers.forEach { (key, value) -> setRequestProperty(key, value) }
        if (body != null) {
            doOutput = true
            if (getRequestProperty("Content-Type") == null) {
                setRequestProperty("Content-Type", "application/json")
            }
        }
    }
    handle?.attach(connection)
    try {
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) {
            ""
        } else if (onChunk == null) {
            stream.bufferedReader().use { it.readText() }
        } else {
            val builder = StringBuilder()
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(1024)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    val chunk = String(buffer, 0, read)
                    builder.append(chunk)
                    onChunk(chunk)
                }
            }
            builder.toString()
        }
        if (code !in 200..299) {
            throw HttpException(code, text.ifBlank { "HTTP $code" })
        }
        return text
    } finally {
        connection.disconnect()
    }
}
