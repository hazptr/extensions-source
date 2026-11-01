package eu.kanade.tachiyomi.extension.es.capibaratraductor

import android.util.Base64
import keiyoushi.utils.decodeHex
import keiyoushi.utils.readIntBigEndian

// Page url decoding from the site's callApi script
class LsToken(ls: String) {
    private val values: List<Int>

    init {
        val bytes = decodeToken(ls)
        val mask = bytes[0]
        values = LS_ROWS[mask and 7].mapIndexed { i, position ->
            (bytes[position] xor LS_MASK[(i + mask) and 7] xor mask) and 0xFF
        }
    }

    val period = values.subList(0, 3).fold(0) { acc, byte -> (acc shl 8) or byte }
    val maskedSeed = values.subList(3, 7).fold(0) { acc, byte -> (acc shl 8) or byte }
}

// Key blob from the site's HTML, named after a period and covering its two neighbours
class PageKey(private val period: Int, private val hex: String) {
    fun covers(other: Int) = other - period in -1..1

    fun word(other: Int): Int {
        val slot = other - period + 1
        return hex.decodeHex().readIntBigEndian(slot * 4) xor mix((period * 3 + slot) xor KEY_WORD_SALT)
    }
}

fun findPageKey(html: String, period: Int): PageKey? {
    val blobs = PAGE_KEY_REGEX.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
    return (period - 1..period + 1).firstNotNullOfOrNull { candidate ->
        blobs["_" + mix(candidate xor KEY_NAME_SALT).toUInt().toString(36)]?.let { PageKey(candidate, it) }
    }
}

fun decodePageUrl(token: String, seed: Int, index: Int, pageId: Long): String {
    val bytes = decodeToken(unwrapToken(token, index))

    val s0 = mix(seed xor STATE_SALT_0)
    val s1 = mix(s0 xor ((index + 1) * PAGE_MULTIPLIER))
    val s2 = mix(s1 xor URL_FIELD_KEY xor fnv1a(pageId.toString().map { it.code }))
    val random = Sfc32(s0, s1, s2, mix(s2 xor STATE_SALT_3))

    var word = 0
    val decoded = bytes.mapIndexed { i, byte ->
        if (i % 4 == 0) word = random.next()
        (byte xor (word ushr (i % 4 * 8))) and 0xFF
    }
    val body = decoded.drop(3)
    check(decoded[0] == TOKEN_VERSION && fnv1a(body) and 0xFFFF == (decoded[1] shl 8) or decoded[2]) { "Invalid page url" }

    return body.map { it.toByte() }.toByteArray().decodeToString()
}

// Indices of the decoy pages the site's reader removes
fun decodeDecoyPages(lh: String): List<Int> {
    val raw = Base64.decode(lh, Base64.DEFAULT).map { it.toInt() and 0xFF }
    val seed = LH_SEED_POSITIONS.fold(0) { acc, i -> (acc shl 8) or raw[i] }
    val rows = raw.filterIndexed { i, _ -> i !in LH_SEED_POSITIONS }

    val order = rows.indices.toMutableList()
    val shuffle = Mulberry32(seed xor LH_ORDER_SALT)
    for (i in order.lastIndex downTo 1) {
        val j = ((shuffle.next() * (i + 1)) ushr 32).toInt()
        order[i] = order[j].also { order[j] = order[i] }
    }

    val pad = Mulberry32(seed xor LH_PAD_SALT)
    val values = order.map { rows[it] xor (pad.next() ushr 24).toInt() }
    return List(values[0]) { (values[1 + it * 2] shl 8) or values[2 + it * 2] }
}

private fun decodeToken(token: String): List<Int> {
    val bytes = ArrayList<Int>(token.length * 3 / 4)
    var buffer = 0
    var bits = 0
    token.forEach { char ->
        buffer = (buffer shl 6) or TOKEN_ALPHABET.indexOf(char)
        bits += 6
        if (bits >= 8) {
            bits -= 8
            bytes += (buffer ushr bits) and 0xFF
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    return bytes
}

private fun unwrapToken(token: String, index: Int): String {
    val size = token.length
    val shifted = token.mapIndexed { i, char ->
        WRAP_ALPHABET[(WRAP_ALPHABET.indexOf(char) - i * 7 - 13 - index) and 63]
    }
    val rotation = (index * 3 + 1) % size
    val rotated = List(size) { shifted[(it + size - rotation) % size] }
    return String(CharArray(size) { if (it % 2 == 0) rotated[it / 2] else rotated[size - 1 - (it - 1) / 2] })
}

private fun mix(value: Int): Int {
    var hash = (value xor (value ushr 16)) * 2146121005
    hash = (hash xor (hash ushr 15)) * 2221713035L.toInt()
    return hash xor (hash ushr 16)
}

private fun fnv1a(bytes: List<Int>) = bytes.fold(2166136261L.toInt()) { hash, byte -> (hash xor byte) * 16777619 }

private class Sfc32(private var a: Int, private var b: Int, private var c: Int, private var d: Int) {
    init {
        repeat(16) { next() }
    }

    fun next(): Int {
        val output = a + b + d
        d += 1
        a = b xor (b ushr 9)
        b = c + (c shl 3)
        c = c.rotateLeft(21) + output
        return output
    }
}

private class Mulberry32(private var state: Int) {
    fun next(): Long {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
    }
}

private val PAGE_KEY_REGEX = Regex("""(_[0-9a-z]+)="([0-9a-f]{24})"""")
private const val TOKEN_VERSION = 4
private const val TOKEN_ALPHABET = "CDoIL78Qqwj9zKM6mOFdf0niV_AByGg4aNhbEetPsHr1x-TpZJYRk5lWu32vUcSX"
private const val WRAP_ALPHABET = "tgEib4lqMSha5TU13QX28deN-xuk6KywP0m_roGYvBWAnzFfjcJIVROp7CsDZ9HL"
private const val KEY_NAME_SALT = 1000283751
private const val KEY_WORD_SALT = 739982445
private const val STATE_SALT_0 = 1821285621
private const val STATE_SALT_3 = 374761393
private const val PAGE_MULTIPLIER = 2654435761L.toInt()
private const val URL_FIELD_KEY = 2246822519L.toInt()
private const val LH_ORDER_SALT = 1374496675
private const val LH_PAD_SALT = 795581489

private val LS_ROWS = listOf(
    listOf(12, 2, 13, 7, 1, 9, 8),
    listOf(11, 4, 5, 6, 3, 9, 12),
    listOf(9, 8, 2, 3, 1, 6, 13),
    listOf(8, 7, 2, 13, 1, 3, 6),
    listOf(13, 2, 11, 6, 4, 3, 8),
    listOf(6, 1, 3, 4, 7, 5, 11),
    listOf(4, 8, 7, 9, 1, 10, 13),
    listOf(8, 3, 6, 13, 12, 10, 11),
)
private val LS_MASK = listOf(234, 55, 192, 73, 249, 251, 2, 58)
private val LH_SEED_POSITIONS = listOf(2, 7, 11, 16)
