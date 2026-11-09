package keiyoushi.webview.internal

import android.net.http.SslCertificate
import android.os.Handler
import android.os.Looper
import android.util.Log
import keiyoushi.network.Fetch
import keiyoushi.network.RateLimitInterceptor
import keiyoushi.network.SentByBrowser
import keiyoushi.network.kind
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.AbstractMap
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.function.Function
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.time.Duration.Companion.minutes

/** A route's host patterns (`example.com`, or `*.example.com` for its subdomains) and how their requests are sent. */
private typealias RouteEntry = Map.Entry<Set<String>, Function<Request, Response>>

/**
 * A local HTTP proxy that sends WebView's requests to a route's hosts through the route's interceptor and client, and
 * tunnels every other host as it is. It ends TLS to a route's hosts with [ProxyTls]'s certificate and only answers
 * clients that send its random credentials. All of the app's WebViews use it while any route is open.
 *
 * Every extension has its own copy of core, so the copies share one server through [shared], in JDK and OkHttp types
 * only. Its keys are only ever added to; [PROTOCOL_VERSION] goes up when a copy can no longer use another's server.
 */
internal object WebViewProxy {
    private const val TAG = "WebViewProxy"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val MAX_HEAD = 64 * 1024
    private const val MAX_BODY = 32 * 1024 * 1024

    private const val PROTOCOL_VERSION = 1

    private const val PROTOCOL = "protocol"
    private const val ROUTES = "routes"
    private const val SERVER = "server"
    private const val PORT = "port"
    private const val CERTIFICATE = "certificate"
    private const val REALM = "realm"
    private const val USERNAME = "username"
    private const val PASSWORD = "password"

    private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    private val HOP_BY_HOP = setOf(
        "connection", "proxy-connection", "keep-alive", "transfer-encoding", "te", "upgrade", "trailer",
        "proxy-authenticate", "proxy-authorization",
    )

    // OkHttp sets Host and Content-Length itself
    private val DROPPED_REQUEST_HEADERS = HOP_BY_HOP + setOf("host", "content-length")
    private val DROPPED_WEBSOCKET_HEADERS = setOf("proxy-connection", "proxy-authorization")

    /** An open route, and what a WebView needs to use the proxy. */
    class Route(
        val entry: RouteEntry,
        val port: Int,
        val certificate: ByteArray,
        val realm: String,
        val username: String,
        val password: String,
    ) {
        /** Whether a proxy auth request from [host] for [realm] is this proxy's. */
        fun asks(host: String?, realm: String?): Boolean = host == "127.0.0.1" && realm == this.realm

        /** Whether [certificate], from an SSL error, is this proxy's. */
        fun presents(certificate: SslCertificate): Boolean {
            val encoded = ProxyTls.encoded(certificate) ?: return false
            return MessageDigest.isEqual(encoded, this.certificate)
        }
    }

    // Every change to it, and the override calls that go with one, happen under its lock
    @Suppress("UNCHECKED_CAST")
    private val shared: MutableMap<String, Any> = System.getProperties().let { properties ->
        val fresh = ConcurrentHashMap<String, Any>()
        (properties.putIfAbsent("keiyoushi.webview-proxy", fresh) ?: fresh) as MutableMap<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private val routes: MutableList<RouteEntry>
        get() = shared.getOrPut(ROUTES) { CopyOnWriteArrayList<RouteEntry>() } as MutableList<RouteEntry>

    private val mainHandler = Handler(Looper.getMainLooper())
    private val connections: MutableSet<Socket> = Collections.synchronizedSet(mutableSetOf())
    private val workers = Executors.newCachedThreadPool { Thread(it, TAG).apply { isDaemon = true } }

    // The Proxy-Authorization of the server this copy runs
    @Volatile
    private var authorization: ByteArray? = null

    // Plain http requests to hosts without a route, sent as they come
    private val plainSend: Function<Request, Response> by lazy {
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
        Function { client.newCall(it).execute() }
    }

    /**
     * Sends requests to [hosts] and their subdomains through [interceptor], then [client], until [close], and points all
     * of the app's WebViews at the proxy meanwhile. [onReady] runs on the main thread once they use it, or with false if
     * WebView refused. Throws if another extension runs a server this copy can't use.
     */
    fun open(hosts: Set<String>, client: OkHttpClient, interceptor: Interceptor?, onReady: (applied: Boolean) -> Unit): Route {
        // WebView follows redirects, keeps its own cookies and cache, paces its loads and shows the site's challenges
        val routeClient = client.newBuilder()
            .apply {
                // The app's, by name as in KeiSource
                interceptors().removeAll {
                    it.javaClass.simpleName == "CloudflareInterceptor" || it is RateLimitInterceptor.TaggingInterceptor
                }
                networkInterceptors().removeAll { it is RateLimitInterceptor }
                if (interceptor != null) interceptors().add(0, interceptor)
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .build()
        // Tagged with this copy's core classes, which its client's interceptors know. Chrome never sends X-Requested-With
        val send = Function<Request, Response> { request ->
            val sent = request.newBuilder()
                .removeHeader("X-Requested-With")
                .kind(kindOf(request.headers))
                .tag(SentByBrowser::class.java, SentByBrowser)
                .build()
            routeClient.newCall(sent).execute()
        }
        val patterns = hosts.map { it.lowercase(Locale.ROOT) }.flatMap { listOf(it, "*.$it") }.toSet()
        val entry: RouteEntry = AbstractMap.SimpleImmutableEntry(patterns, send)
        synchronized(shared) {
            if (shared[SERVER] == null) {
                startServer()
            } else {
                check(shared[PROTOCOL] == PROTOCOL_VERSION) { "Another extension runs an incompatible WebView proxy" }
            }
            routes += entry
            val route = Route(
                entry,
                port = shared[PORT] as Int,
                certificate = shared[CERTIFICATE] as ByteArray,
                realm = shared[REALM] as String,
                username = shared[USERNAME] as String,
                password = shared[PASSWORD] as String,
            )
            mainHandler.post {
                val applying = entry in routes && WebViewGlueBridge.setProxyOverride(route.port) { onReady(true) }
                if (!applying) onReady(false)
            }
            return route
        }
    }

    /** Ends [route]; the server and WebView's override end with the last route. */
    fun close(route: Route) {
        synchronized(shared) {
            if (!routes.remove(route.entry) || routes.isNotEmpty()) return
            (shared.remove(SERVER) as Closeable).close()
            listOf(PROTOCOL, PORT, CERTIFICATE, REALM, USERNAME, PASSWORD).forEach(shared::remove)
            mainHandler.post { WebViewGlueBridge.clearProxyOverride() }
        }
    }

    // Holding the lock on shared. Any app can reach the port, but only WebViews told the random credentials get answers
    private fun startServer() {
        val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val username = randomHex()
        val password = randomHex()
        authorization = ("Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())).toByteArray()
        shared[SERVER] = Closeable {
            server.closeQuietly()
            // Tunnels WebView still holds open would otherwise outlive the server
            synchronized(connections) { connections.toList() }.forEach { it.closeQuietly() }
        }
        shared[PROTOCOL] = PROTOCOL_VERSION
        shared[PORT] = server.localPort
        shared[CERTIFICATE] = ProxyTls.certificate.encoded
        shared[REALM] = "keiyoushi-${randomHex()}"
        shared[USERNAME] = username
        shared[PASSWORD] = password
        Thread({ acceptLoop(server) }, TAG).apply { isDaemon = true }.start()
    }

    private fun randomHex() = ByteArray(16).also(SecureRandom()::nextBytes).toHexString()

    private fun routeFor(host: String): RouteEntry? = routes.lastOrNull { (patterns) ->
        patterns.any { host == it || (it.startsWith("*.") && host.endsWith(it.substring(1))) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (true) {
            // Throws once the last route closes the server
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            connections += socket
            workers.execute { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 1.minutes.inWholeMilliseconds.toInt()
            serve(socket)
        } catch (_: IOException) {
            // Either side ended the connection
        } catch (e: Throwable) {
            // Uncaught on this thread, it would crash the app; the route's interceptor and client run here too
            Log.e(TAG, "connection", e)
        } finally {
            socket.closeQuietly()
            connections -= socket
        }
    }

    private fun serve(socket: Socket) {
        val output = socket.getOutputStream()
        // Unbuffered, so the TLS hello after a CONNECT stays in the socket for the handshake
        val head = readHead(socket.getInputStream()) ?: return
        if (!head.isAuthorized()) return output.askForCredentials()
        // Only the first head waits against the clock: WebView keeps its connections open, and a tunnel may stay quiet
        // on its side while the site sends
        socket.soTimeout = 0
        val input = BufferedInputStream(socket.getInputStream())
        if (head.method != "CONNECT") return exchangePlain(input, output, head)
        val origin = "https://${head.target}".toHttpUrlOrNull() ?: return output.respond(400)
        val upstream = if (routeFor(origin.host) == null) connect(origin) ?: return output.respond(502) else null
        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        output.flush()
        if (upstream != null) return pipe(input, output, upstream)
        // WebView offers only HTTP/1.1 for its WebSockets, which carry no X-Requested-With: they go to the site as they
        // are, in WebView's own TLS
        if (ProxyTls.offeredProtocols(socket) == listOf("http/1.1")) return pipe(input, output, connect(origin) ?: return)
        val tls = ProxyTls.accept(socket)
        exchangeTls(BufferedInputStream(tls.inputStream), tls.outputStream, origin)
    }

    /** The requests of a TLS tunnel to [origin], in turn. */
    private fun exchangeTls(input: InputStream, output: OutputStream, origin: HttpUrl) {
        while (true) {
            val head = readHead(input) ?: return
            val route = routeFor(origin.host) ?: return output.respond(403)
            val url = origin.resolve(head.target) ?: return output.respond(400)
            if (head.isWebSocket) return webSocket(head, url, input, output)
            if (!forward(route.value, head, url, input, output) || head.closes) return
        }
    }

    /** Plain http requests, each naming its URL in full; one connection can carry requests to several hosts. */
    private fun exchangePlain(input: InputStream, output: OutputStream, first: Head) {
        var head = first
        while (true) {
            val url = head.target.toHttpUrlOrNull()?.takeUnless { it.isHttps } ?: return output.respond(400)
            val send = routeFor(url.host)?.value ?: plainSend
            if (head.isWebSocket) return webSocket(head, url, input, output)
            if (!forward(send, head, url, input, output) || head.closes) return
            head = readHead(input) ?: return
            if (!head.isAuthorized()) return output.askForCredentials()
        }
    }

    /** Sends one request with [send] and writes its response; false when the connection has to end. */
    private fun forward(send: Function<Request, Response>, head: Head, url: HttpUrl, input: InputStream, output: OutputStream): Boolean {
        // OkHttp requires a body on POST, PUT and PATCH
        val body = readBody(input, head.headers) ?: ByteArray(0)
        val request = Request.Builder()
            .url(url)
            .headers(head.headers(DROPPED_REQUEST_HEADERS))
            .method(head.method, body.takeIf { head.method in BODY_METHODS || it.isNotEmpty() }?.toRequestBody())
            .build()
        val response = try {
            send.apply(request)
        } catch (_: IOException) {
            output.respond(502)
            return false
        }
        response.use { writeResponse(it, head.method, output) }
        return true
    }

    private fun writeResponse(response: Response, method: String, output: OutputStream) {
        val noBody = method == "HEAD" || response.code in 100..199 || response.code == 204 || response.code == 304
        val length = response.body.contentLength()
        val chunked = !noBody && length < 0
        val head = buildString {
            append("HTTP/1.1 ${response.code} ${response.message}\r\n")
            response.headers.forEach { (name, value) ->
                val lower = name.lowercase(Locale.ROOT)
                if (lower !in HOP_BY_HOP && (lower != "content-length" || noBody)) append("$name: $value\r\n")
            }
            if (chunked) {
                append("Transfer-Encoding: chunked\r\n")
            } else if (!noBody) {
                append("Content-Length: $length\r\n")
            }
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        if (!noBody) response.body.byteStream().copyFlushing(output, chunked)
        output.flush()
    }

    /** A WebSocket goes to the site as WebView sent it, over a connection of its own: OkHttp can't pass its frames on. */
    private fun webSocket(head: Head, url: HttpUrl, input: InputStream, output: OutputStream) {
        // As sent, unless it's a plain http request's full URL
        val target = head.target.takeIf { it.startsWith("/") } ?: (url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty())
        val request = buildString {
            append("${head.method} $target HTTP/1.1\r\n")
            head.headers.forEach { (name, value) ->
                if (name.lowercase(Locale.ROOT) !in DROPPED_WEBSOCKET_HEADERS) append("$name: $value\r\n")
            }
            append("\r\n")
        }
        val socket = connect(url) ?: return output.respond(502)
        try {
            val upstream = if (url.isHttps) tlsTo(socket, url) ?: return output.respond(502) else socket
            pipe(input, output, upstream, request.toByteArray(Charsets.ISO_8859_1))
        } finally {
            socket.closeQuietly()
            connections -= socket
        }
    }

    /** TLS to [url]'s host over [socket], its certificate checked for that host; null when the handshake fails. */
    private fun tlsTo(socket: Socket, url: HttpUrl): SSLSocket? {
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, url.host, url.port, true) as SSLSocket
        return try {
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()
            tls
        } catch (_: IOException) {
            tls.closeQuietly()
            null
        }
    }

    /** A connection to [url]'s host, through the device's proxy like WebView's own; null when it fails. */
    private fun connect(url: HttpUrl): Socket? {
        val proxy = ProxySelector.getDefault()?.select(URI("https", null, url.host, url.port, null, null, null))
            ?.firstOrNull { it.type() == Proxy.Type.HTTP }
            ?.address() as? InetSocketAddress
        val socket = Socket()
        connections += socket
        return try {
            if (proxy == null) {
                socket.connect(InetSocketAddress(url.host, url.port), CONNECT_TIMEOUT_MS)
            } else {
                socket.connect(InetSocketAddress(proxy.hostString, proxy.port), CONNECT_TIMEOUT_MS)
                val authority = if (':' in url.host) "[${url.host}]:${url.port}" else "${url.host}:${url.port}"
                socket.soTimeout = CONNECT_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n".toByteArray())
                    flush()
                }
                // Unbuffered, so nothing of the tunnel's own data is read with the reply; its status code is the target
                if (readHead(socket.getInputStream())?.target != "200") throw IOException("CONNECT refused")
            }
            socket
        } catch (_: IOException) {
            socket.closeQuietly()
            connections -= socket
            null
        }
    }

    /** Sends [first] to [upstream], then copies both ways until either side ends, and closes [upstream]. */
    private fun pipe(input: InputStream, output: OutputStream, upstream: Socket, first: ByteArray = ByteArray(0)) {
        connections += upstream
        try {
            upstream.soTimeout = 0
            val upstreamOutput = upstream.getOutputStream()
            upstreamOutput.write(first)
            upstreamOutput.flush()
            val back = workers.submit {
                try {
                    upstream.getInputStream().copyFlushing(output)
                } catch (_: IOException) {
                    // Either side ended the connection
                } finally {
                    output.closeQuietly()
                }
            }
            input.copyFlushing(upstreamOutput)
            upstream.shutdownOutput()
            back.get()
        } finally {
            upstream.closeQuietly()
            connections -= upstream
        }
    }

    // Flushed as it comes, for streams such as server-sent events
    private fun InputStream.copyFlushing(output: OutputStream, chunked: Boolean = false) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (chunked) output.write("${Integer.toHexString(read)}\r\n".toByteArray())
            output.write(buffer, 0, read)
            if (chunked) output.write("\r\n".toByteArray())
            output.flush()
        }
        if (chunked) output.write("0\r\n\r\n".toByteArray())
    }

    private fun Head.isAuthorized(): Boolean {
        val sent = headers["Proxy-Authorization"]?.toByteArray() ?: return false
        return MessageDigest.isEqual(sent, authorization ?: return false)
    }

    private fun OutputStream.askForCredentials() = respond(407, "Proxy-Authenticate: Basic realm=\"${shared[REALM]}\"\r\n")

    private fun OutputStream.respond(code: Int, headers: String = "") {
        write("HTTP/1.1 $code \r\n${headers}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        flush()
    }

    /** The core's [Fetch.Kind] for a request's Fetch metadata: the order its headers go out in and its Priority. */
    private fun kindOf(headers: Headers): Fetch.Kind = when (headers["Sec-Fetch-Dest"]?.lowercase(Locale.ROOT)) {
        "document", "iframe", "frame" -> Fetch.Kind.DOCUMENT
        "image" -> Fetch.Kind.IMAGE
        "empty" -> Fetch.Kind.XHR
        null -> if (headers["Sec-Fetch-Mode"] == "navigate") Fetch.Kind.DOCUMENT else Fetch.Kind.XHR
        else -> Fetch.Kind.SCRIPT
    }

    /** A request line, or for a status line its code as [target], and the headers. */
    private class Head(val method: String, val target: String, val headers: List<Pair<String, String>>) {
        val isWebSocket get() = headers["Upgrade"].equals("websocket", ignoreCase = true)
        val closes get() = headers["Connection"].equals("close", ignoreCase = true)

        fun headers(dropped: Set<String>): Headers = Headers.Builder().apply {
            headers.forEach { (name, value) -> if (name.lowercase(Locale.ROOT) !in dropped) addUnsafeNonAscii(name, value) }
        }.build()
    }

    private operator fun List<Pair<String, String>>.get(name: String) = find { (key) -> key.equals(name, ignoreCase = true) }?.second

    /** The next head, or null at the end of the connection. */
    private fun readHead(input: InputStream): Head? {
        var size = 0
        fun line() = readLine(input)?.also {
            size += it.length + 2
            if (size > MAX_HEAD) throw IOException("Head too large")
        }
        var line = line() ?: return null
        // A stray CRLF between requests
        if (line.isEmpty()) line = line() ?: return null
        val parts = line.split(' ', limit = 3)
        if (parts.size < 2) throw IOException("Not HTTP")
        val headers = mutableListOf<Pair<String, String>>()
        while (true) {
            val header = line() ?: throw IOException("Truncated")
            if (header.isEmpty()) return Head(parts[0], parts[1], headers)
            val colon = header.indexOf(':')
            if (colon <= 0) throw IOException("Bad header")
            headers += header.substring(0, colon).trim() to header.substring(colon + 1).trim()
        }
    }

    /** The body [headers] announce, or null if there's none. */
    private fun readBody(input: InputStream, headers: List<Pair<String, String>>): ByteArray? {
        if (headers["Transfer-Encoding"]?.contains("chunked", ignoreCase = true) == true) {
            val body = ByteArrayOutputStream()
            while (true) {
                val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16)?.takeIf { it >= 0 }
                    ?: throw IOException("Bad chunk")
                if (size == 0) break
                if (body.size() + size > MAX_BODY) throw IOException("Body too large")
                body.write(input.readExactly(size))
                readLine(input)
            }
            // Trailers, up to the empty line
            do {
                val trailer = readLine(input) ?: throw IOException("Truncated")
            } while (trailer.isNotEmpty())
            return body.toByteArray()
        }
        val length = headers["Content-Length"]?.toIntOrNull() ?: return null
        if (length !in 0..MAX_BODY) throw IOException("Bad length")
        return input.readExactly(length)
    }

    /** One line without its CRLF, read byte by byte, or null at the end of the stream. */
    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.size() == 0) null else throw IOException("Truncated")
            if (byte == '\n'.code) return line.toString("ISO-8859-1").removeSuffix("\r")
            if (line.size() == MAX_HEAD) throw IOException("Line too long")
            line.write(byte)
        }
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) throw IOException("Truncated")
            offset += read
        }
        return bytes
    }
}

internal fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    }
}
