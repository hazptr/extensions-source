package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import keiyoushi.utils.readIntLittleEndian

// The web reader's `ultra_sign` query parameter (wasm a5b84fd26eefaff32016).
object UltraSign {

    fun sign(body: String, ts: Long): String {
        val h1 = hash("a810devicepcnov27platformweb" + ts.toString(16))
        val h2 = hash(body + (ts ushr 7).toString(2))
        val h3 = hash(encodeBody(body.encodeToByteArray(), if (ts % 2 == 0L) BASE64_EVEN else BASE64_ODD))
        fun tsByte(shift: Int) = (ts ushr shift).toByte()
        fun char(c: Char) = c.code.toByte()
        val shared = byteArrayOf(
            1, tsByte(23), 0, 0, 0, 2, char(h2[11]), 0, 0, 5, 0, 0, tsByte(24), 7, tsByte(15), 7, 2,
            char(h3[19]), char(h1[23]), tsByte(8), 1, 1, 0, 6, 5, 0, 4, char(h2[7]), tsByte(31),
        )
        val sign = (hash(shared + byteArrayOf(char(h1[7]), char(h2[29]))).take(24) + hash(shared + char(h3[31])).take(24)).toCharArray()
        val tsHex = ts.toString(16).padStart(11, '0')
        TS_POSITIONS.forEachIndexed { i, position -> sign[position] = tsHex[i] }
        return String(sign)
    }

    private fun hash(input: String) = hash(input.encodeToByteArray())

    // Merkle-Damgard over 64-byte blocks with an ARX round per little-endian word.
    private fun hash(input: ByteArray): String {
        var f = 0xDEADBEEF.toInt()
        var h = 0x12345678
        var k = 0xABCDEF01.toInt()
        var j = 0xFEEDCAFE.toInt()
        val padded = input.copyOf(((input.size + 9) / 64 + 1) * 64)
        padded[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        for (b in 0 until 8) padded[padded.size - 8 + b] = (bits ushr (8 * b)).toByte()
        for (offset in padded.indices step 4) {
            val word = padded.readIntLittleEndian(offset)
            val h1 = ((f and h) or (j and h.inv())) + k + word
            val i = word + h + (h1 xor f xor j)
            val g1 = f + word + ((h1 and i) or (j and i.inv()))
            j = (word + j + (i xor h1 xor g1)).rotateLeft(11)
            f = g1.rotateLeft(3)
            k = h1.rotateLeft(5)
            h = i.rotateLeft(7)
        }
        return buildString {
            for (word in intArrayOf(k, h, f, j)) {
                for (shift in 28 downTo 0 step 4) append(HASH_ALPHABET[(word ushr shift) and 15])
            }
        }
    }

    // Body XOR keystream, base64 over zero-padded groups with one '=' per padding byte.
    private fun encodeBody(body: ByteArray, alphabet: String): String {
        val pad = (3 - body.size % 3) % 3
        val stream = keystream(body.size)
        val bytes = ByteArray(body.size + pad) { if (it < body.size) (body[it].toInt() xor stream[it].toInt()).toByte() else 0 }
        return buildString {
            for (i in bytes.indices step 3) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = bytes[i + 1].toInt() and 0xFF
                val b2 = bytes[i + 2].toInt() and 0xFF
                append(alphabet[b0 ushr 2])
                append(alphabet[((b0 and 3) shl 4) or (b1 ushr 4)])
                append(alphabet[((b1 and 15) shl 2) or (b2 ushr 6)])
                append(alphabet[b2 and 63])
            }
            repeat(pad) { append('=') }
        }
    }

    // RC4 with `S[i] ^= i` after each key schedule swap and the output index offset by one.
    private fun keystream(length: Int): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + RC4_KEY[i % 3] + s[i]) and 255
            s[i] = s[j].also { s[j] = s[i] } xor i
        }
        var i = 0
        j = 0
        return ByteArray(length) {
            i = (i + 1) and 255
            j = (j + s[i]) and 255
            s[i] = s[j].also { s[j] = s[i] }
            s[(s[i] + s[j] + 1) and 255].toByte()
        }
    }

    private val RC4_KEY = intArrayOf(1, 3, 7)
    private const val BASE64_EVEN = "UalVqQk8T7+5oJxC6MzIniWDrc3XERGN/wdLyp90mf2tHuvsgeSjZbBF1KAOPhY4"
    private const val BASE64_ODD = "zI3XZb8T7+5opF1KJxUaBqQk90mf6MlVCEceSjAOPhY42RGN/wdLytHuvsgniWDr"

    // Hash nibbles as letters of the sign alphabet.
    private val HASH_ALPHABET = "0123456789abcdef".map { "br5NH9CxpvJzmEt3OqoF7jgWMIZ2UnASfc8K6XaLYylT1eVBGh4sdQkDwRP0ui"[it.code % 62] }

    // Where each hex digit of ts goes, most significant first.
    private val TS_POSITIONS = intArrayOf(41, 37, 23, 29, 13, 19, 5, 47, 7, 11, 17)
}
