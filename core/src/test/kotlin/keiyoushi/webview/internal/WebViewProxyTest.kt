package keiyoushi.webview.internal

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/** The proxy's HTTP side, with the route's interceptor answering in place of the site. */
class WebViewProxyTest {
    private val routes = mutableListOf<WebViewProxy.Route>()
    private val seen = CopyOnWriteArrayList<Request>()

    @After
    fun closeRoutes() = routes.forEach(WebViewProxy::close)

    private fun open(answer: (Request) -> Response = { ok(it, "hello") }): WebViewProxy.Route {
        val interceptor = Interceptor { chain -> chain.request().also(seen::add).let(answer) }
        return WebViewProxy.open(setOf("a.test"), OkHttpClient(), interceptor) {}.also(routes::add)
    }

    @Test
    fun asksForCredentials() {
        val route = open()
        Socket(InetAddress.getLoopbackAddress(), route.port).use { socket ->
            socket.getOutputStream().write("CONNECT a.test:443 HTTP/1.1\r\nHost: a.test:443\r\n\r\n".toByteArray())
            val response = readResponse(socket.getInputStream())
            assertEquals(407, response.code)
            assertEquals("Basic realm=\"${route.realm}\"", response.headers["proxy-authenticate"])
        }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun answersRequestsInsideTheTunnel() {
        val route = open { request ->
            when (request.method) {
                "POST" -> ok(request, request.body!!.let { Buffer().also(it::writeTo).readUtf8() })
                else -> ok(request, "hello")
            }
        }
        tunnel(route).use { tls ->
            val output = tls.outputStream
            val input = tls.inputStream
            output.write("GET /x?q=1 HTTP/1.1\r\nHost: a.test\r\nX-Requested-With: app\r\nSec-Fetch-Dest: document\r\n\r\n".toByteArray())
            assertEquals(Answer(200, "hello"), readResponse(input).answer)
            // Same connection: a HEAD without a body, then a chunked POST
            output.write("HEAD /x HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            val head = readResponse(input, noBody = true)
            assertEquals(Answer(200, ""), head.answer)
            output.write("POST /y HTTP/1.1\r\nHost: a.test\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n".toByteArray())
            assertEquals(Answer(200, "abcde"), readResponse(input).answer)
        }
        assertEquals("https://a.test/x?q=1", seen[0].url.toString())
        assertNull(seen[0].header("X-Requested-With"))
        assertEquals(listOf("GET", "HEAD", "POST"), seen.map { it.method })
    }

    @Test
    fun streamsBodiesOfUnknownLength() {
        val route = open { request ->
            val body = Buffer().writeUtf8("streamed").asResponseBody("text/plain".toMediaType(), -1)
            ok(request, "").newBuilder().body(body).build()
        }
        tunnel(route).use { tls ->
            tls.outputStream.write("GET / HTTP/1.1\r\nHost: a.test\r\n\r\n".toByteArray())
            val response = readResponse(tls.inputStream)
            assertEquals("chunked", response.headers["transfer-encoding"])
            assertEquals(Answer(200, "streamed"), response.answer)
        }
    }

    @Test
    fun answersPlainHttpForItsHosts() {
        val route = open()
        Socket(InetAddress.getLoopbackAddress(), route.port).use { socket ->
            socket.getOutputStream().write("GET http://a.test/z HTTP/1.1\r\nHost: a.test\r\n${authorization(route)}\r\n".toByteArray())
            assertEquals(Answer(200, "hello"), readResponse(socket.getInputStream()).answer)
        }
        assertEquals("http://a.test/z", seen.single().url.toString())
    }

    @Test
    fun tunnelsOtherHostsAsTheyAre() {
        val route = open()
        ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { site ->
            thread { site.accept().use { it.getInputStream().copyTo(it.getOutputStream()) } }
            Socket(InetAddress.getLoopbackAddress(), route.port).use { socket ->
                val output = socket.getOutputStream()
                output.write("CONNECT 127.0.0.1:${site.localPort} HTTP/1.1\r\n${authorization(route)}\r\n".toByteArray())
                assertEquals(200, readResponse(socket.getInputStream(), noBody = true).code)
                output.write(byteArrayOf(1, 2, 3))
                assertArrayEquals(byteArrayOf(1, 2, 3), socket.getInputStream().readNBytes(3))
            }
        }
        assertTrue(seen.isEmpty())
    }

    private fun ok(request: Request, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("")
        .body(body.toResponseBody())
        .build()

    private fun authorization(route: WebViewProxy.Route): String {
        val credentials = Base64.getEncoder().encodeToString("${route.username}:${route.password}".toByteArray())
        return "Proxy-Authorization: Basic $credentials\r\n"
    }

    /** A TLS connection to a.test through the proxy, trusting only the proxy's certificate. */
    private fun tunnel(route: WebViewProxy.Route): SSLSocket {
        val socket = Socket(InetAddress.getLoopbackAddress(), route.port)
        socket.getOutputStream().write("CONNECT a.test:443 HTTP/1.1\r\nHost: a.test:443\r\n${authorization(route)}\r\n".toByteArray())
        assertEquals(200, readResponse(socket.getInputStream(), noBody = true).code)
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                check(chain[0].encoded.contentEquals(route.certificate))
            }
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return (context.socketFactory.createSocket(socket, "a.test", 443, true) as SSLSocket).apply { startHandshake() }
    }

    private data class Answer(val code: Int, val body: String)

    private class Received(val code: Int, val headers: Map<String, String>, val body: String) {
        val answer get() = Answer(code, body)
    }

    private fun readResponse(input: InputStream, noBody: Boolean = false): Received {
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                if (byte < 0 || byte == '\n'.code) return bytes.toString(Charsets.ISO_8859_1).removeSuffix("\r")
                bytes.write(byte)
            }
        }
        val code = line().split(' ')[1].toInt()
        val headers = generateSequence(::line).takeWhile { it.isNotEmpty() }
            .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val body = when {
            noBody -> ""
            headers["transfer-encoding"] == "chunked" -> buildString {
                while (true) {
                    val size = line().toInt(16)
                    if (size == 0) break
                    append(String(input.readNBytes(size)))
                    line()
                }
                line()
            }
            else -> String(input.readNBytes(headers["content-length"]?.toInt() ?: 0))
        }
        return Received(code, headers, body)
    }
}
