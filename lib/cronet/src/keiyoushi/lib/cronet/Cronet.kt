package keiyoushi.lib.cronet

import android.os.Build
import eu.kanade.tachiyomi.network.NetworkHelper
import keiyoushi.lib.runtime.Runtime
import keiyoushi.utils.applicationContext
import okhttp3.Authenticator
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.connection.RealCall
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.lang.reflect.Constructor

/**
 * Sends the client's requests through the device's Cronet (Android 14's HttpEngine, else Google Play
 * services), so TLS and HTTP/2 look like Chrome; the rest of the client (interceptors, cookies, cache,
 * redirects) works as on OkHttp. Needs the Keiyoushi Runtime extension installed; without it, or without Cronet on
 * the device, requests stay on OkHttp, as do clients with their own DNS, TLS, proxy or authenticator settings.
 */
fun OkHttpClient.Builder.cronet(): OkHttpClient.Builder = apply {
    if (System.getProperty("java.vm.name") == "Dalvik") addInterceptor(CronetInterceptor)
}

/** Runs the rest of OkHttp's chain itself, with Cronet in place of OkHttp's network call */
private object CronetInterceptor : Interceptor {
    private val network: NetworkHelper by injectLazy()

    private val bridge by lazy { okHttpInterceptor("okhttp3.internal.http.BridgeInterceptor") }
    private val cache by lazy { okHttpInterceptor("okhttp3.internal.cache.CacheInterceptor") }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain is CronetChain) return chain.proceed(chain.request())
        val server = CronetRuntime.server() ?: return chain.proceed(chain.request())
        val client = (chain.call() as RealCall).client
        if (!usesAppNetwork(client)) return chain.proceed(chain.request())

        val interceptors = runCatching {
            buildList {
                addAll(client.interceptors.let { it.subList(it.indexOf(this@CronetInterceptor) + 1, it.size) })
                add(FollowUpInterceptor(client))
                add(bridge.getOrThrow()(client.cookieJar))
                add(cache.getOrThrow()(client.cache))
                addAll(client.networkInterceptors)
                // A runtime that's gone mid-session (the Cronet extension was updated) throws errors, not IOExceptions
                add(
                    Interceptor { chain ->
                        try {
                            server.intercept(chain)
                        } catch (e: IOException) {
                            throw e
                        } catch (e: Throwable) {
                            throw IOException(e)
                        }
                    },
                )
            }
        }.getOrElse { return chain.proceed(chain.request()) }
        return CronetChain(chain, interceptors, 0, chain.request()).proceed(chain.request())
    }

    /** Cronet resolves, connects and verifies on its own: the system DNS (no DoH) and the app's TLS and proxy */
    private fun usesAppNetwork(client: OkHttpClient): Boolean {
        val app = network.client
        return client.dns === Dns.SYSTEM &&
            client.sslSocketFactory === app.sslSocketFactory &&
            client.hostnameVerifier === app.hostnameVerifier &&
            client.certificatePinner == app.certificatePinner &&
            client.proxy == app.proxy &&
            client.proxySelector === app.proxySelector &&
            client.authenticator === Authenticator.NONE
    }

    /** OkHttp 5.4 reads the cookie jar and cache from the chain, earlier versions take them as arguments */
    private fun okHttpInterceptor(name: String): Result<(Any?) -> Interceptor> = runCatching {
        val type = Class.forName(name)
        val withArg: Constructor<*>? = type.constructors.firstOrNull { it.parameterTypes.size == 1 }
        val noArg = if (withArg == null) type.getConstructor() else null
        return@runCatching { arg -> (withArg?.newInstance(arg) ?: noArg!!.newInstance()) as Interceptor }
    }
}

private object CronetRuntime {
    // Android 14's HttpEngine, or Play services
    private val deviceHasCronet by lazy {
        Build.VERSION.SDK_INT >= 34 ||
            runCatching { applicationContext.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess
    }

    fun server(): Interceptor? = runCatching {
        Runtime.loadClass("cronet.CronetRuntime", prompt = deviceHasCronet)
            ?.getMethod("server", android.content.Context::class.java)
            ?.invoke(null, applicationContext) as Interceptor?
    }.getOrNull()
}
