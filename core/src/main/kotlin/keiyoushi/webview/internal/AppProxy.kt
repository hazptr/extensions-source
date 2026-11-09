package keiyoushi.webview.internal

import android.net.http.SslCertificate
import android.os.Build
import eu.kanade.tachiyomi.network.LocalProxy
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.WebViewProxy
import keiyoushi.network.Fetch
import keiyoushi.network.RateLimitInterceptor
import keiyoushi.network.SentByBrowser
import keiyoushi.network.kind
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.util.Locale

/** Routes on the app's WebView proxy, with requests sent through a source's client the way a browser sends them. */
internal object AppProxy {
    private val network: NetworkHelper by injectLazy()

    /** The app's WebView proxy; null where the app has none (before tachiyomix 1.7) or WebView can't be proxied. */
    val proxy: WebViewProxy?
        get() = try {
            network.webViewProxy
        } catch (_: LinkageError) {
            null
        }

    /** Sends the app's WebView requests to [hosts] and their subdomains through [interceptor], then [client]. */
    suspend fun route(proxy: WebViewProxy, hosts: Set<String>, client: OkHttpClient, interceptor: Interceptor?): LocalProxy {
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
        return proxy.route(hosts) { request ->
            val sent = request.newBuilder()
                .removeHeader("X-Requested-With")
                .kind(kindOf(request.headers))
                .tag(SentByBrowser::class.java, SentByBrowser)
                .build()
            routeClient.newCall(sent).execute()
        }
    }

    /** The core's [Fetch.Kind] for a request's Fetch metadata: the order its headers go out in and its Priority. */
    private fun kindOf(headers: Headers): Fetch.Kind = when (headers["Sec-Fetch-Dest"]?.lowercase(Locale.ROOT)) {
        "document", "iframe", "frame" -> Fetch.Kind.DOCUMENT
        "image" -> Fetch.Kind.IMAGE
        "empty" -> Fetch.Kind.XHR
        null -> if (headers["Sec-Fetch-Mode"] == "navigate") Fetch.Kind.DOCUMENT else Fetch.Kind.XHR
        else -> Fetch.Kind.SCRIPT
    }
}

/** Whether a proxy auth request from [host] for [realm] is this route's proxy's. */
internal fun LocalProxy.asks(host: String?, realm: String?): Boolean = host == "127.0.0.1" && realm == this.realm

/** Whether [certificate], from an SSL error, is this route's proxy's. */
internal fun LocalProxy.presents(certificate: SslCertificate): Boolean {
    val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        certificate.x509Certificate?.encoded
    } else {
        SslCertificate.saveState(certificate)?.getByteArray("x509-certificate")
    }
    return encoded != null && MessageDigest.isEqual(encoded, this.certificate)
}
