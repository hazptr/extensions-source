package eu.kanade.tachiyomi.extension.all.dlsite

import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.rangeSource
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer

// A page of a trial archive: its entry, read with a range request
class TrialInterceptor(private val host: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || request.url.host != host) return chain.proceed(request)

        val entry = fragment.parseAs<TrialEntry>()
        val rangeRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .range(dataRange(entry.offset, entry.compressedSize, entry.headerAllowance))
            .build()
        val response = chain.proceed(rangeRequest)
        if (!response.isSuccessful) return response

        val image = readEntry(response.rangeSource(), entry.compressedSize, entry.method).buffer()
        return response.newBuilder()
            .code(200)
            .message("OK")
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .body(image.asResponseBody(mediaType(entry.name).toMediaType()))
            .build()
    }

    private fun mediaType(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
