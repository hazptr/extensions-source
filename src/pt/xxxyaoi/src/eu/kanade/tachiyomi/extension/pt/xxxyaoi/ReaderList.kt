package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import keiyoushi.utils.parseAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64

internal object ReaderList {

    /** The page image urls in the reader's [lists], in page order, given [pageUrl], one of them. */
    fun pageUrls(lists: List<String>, pageUrl: String): List<String>? {
        val page = pageUrl.toHttpUrlOrNull() ?: return null
        return lists.firstNotNullOfOrNull { pageUrls(it, page.toString()) }
    }

    private fun pageUrls(list: String, pageUrl: String): List<String>? {
        val json = list.trim().reversed().decodeBase64()?.string(Charsets.ISO_8859_1) ?: return null
        val data = try {
            json.parseAs<JsonObject>()
        } catch (_: SerializationException) {
            return null
        }
        val (strings, numbers) = data.values.filterIsInstance<JsonPrimitive>().partition { it.isString }
        val keys = strings.map { it.content }.filter { it.isNotEmpty() }
        val counts = numbers.mapNotNull { it.intOrNull }
        val arrays = data.values.filterIsInstance<JsonArray>().mapNotNull { array ->
            array.map { entry -> (entry as? JsonPrimitive)?.contentOrNull?.decodeBase64()?.toByteArray()?.reversedArray() ?: return@mapNotNull null }
        }
        for (entries in arrays) {
            if (entries.isEmpty() || entries.any { it.size < SCHEME.length }) continue
            for (key in keys) {
                for (period in 1..MAX_PERIOD) {
                    for (shift in 0..255) {
                        val mask = ((entries[0][0] + shift) and 0xFF) xor key[0].code xor SCHEME[0].code
                        if (entries.any { decode(it, SCHEME.length, key, period, shift, mask) != SCHEME }) continue
                        val urls = entries.map { decode(it, it.size, key, period, shift, mask) }
                        val normalized = urls.map { it.toHttpUrlOrNull()?.toString() }
                        for (count in counts) {
                            val (shown, pages) = urls.take(count) to urls.drop(count)
                            if (pageUrl in normalized.drop(count) && pages.all { IMAGE_PATH in it } && shown.none { IMAGE_PATH in it }) return pages
                        }
                    }
                }
            }
        }
        return null
    }

    private fun decode(bytes: ByteArray, length: Int, key: String, period: Int, shift: Int, mask: Int) = ByteArray(length) { i ->
        (((bytes[i] + shift + i % period) and 0xFF) xor key[i % key.length].code xor mask).toByte()
    }.decodeToString()

    private const val SCHEME = "https://"

    private const val MAX_PERIOD = 32

    const val IMAGE_PATH = "/WP-manga/data/"
}
