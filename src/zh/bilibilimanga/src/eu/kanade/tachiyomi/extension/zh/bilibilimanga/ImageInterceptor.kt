package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import android.util.Base64
import keiyoushi.utils.newAesCipher
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec

// Decrypts the images ImageToken hands out with a `cpx` parameter.
class ImageInterceptor(private val privateKey: PrivateKey) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = response.request.url
        val cpx = url.queryParameter("cpx") ?: return response
        if (!response.isSuccessful) return response
        val ts = url.queryParameter("ts")?.toLong(16) ?: throw IOException("Image url without ts")
        val encrypted = response.body.bytes()
        if (encrypted.size < 140) throw IOException("Image too short to decrypt")
        val image = try {
            decrypt(encrypted, Base64.decode(cpx, Base64.DEFAULT), ts)
        } catch (e: GeneralSecurityException) {
            throw IOException("Image decryption failed", e)
        }
        return response.newBuilder().body(image.toResponseBody()).build()
    }

    private fun decrypt(encrypted: ByteArray, cpx: ByteArray, ts: Long): ByteArray {
        val mode = MODES[encrypted[0].toInt()] ?: throw IOException("Unknown image mode ${encrypted[0]}")
        val length = encrypted.size - 70
        val markers = markerPositions(length, ts - length - 69)
        // Marker bytes 4..68 are the server's P-256 public key; the rest of the data is the image.
        val serverKey = ByteArray(65) { encrypted[1 + markers[4 + it]] }
        val image = ByteArray(length)
        var from = 0
        var to = 0
        for (marker in markers) {
            encrypted.copyInto(image, to, 1 + from, 1 + marker)
            to += marker - from
            from = marker + 1
        }
        encrypted.copyInto(image, to, 1 + from)

        val iv = IvParameterSpec(cpx.copyOfRange(mode.ivOffset, mode.ivOffset + 16))
        val encryptedLength = minOf(mode.length, image.size)
        // CBC carries PKCS#7 padding, so its plaintext comes out shorter than the encrypted part.
        val head = newAesCipher(mode.transformation, sharedSecret(serverKey), iv).doFinal(image, 0, encryptedLength)
        return head + image.copyOfRange(encryptedLength, image.size)
    }

    // 69 distinct positions from an LCG, ascending.
    private fun markerPositions(range: Int, seed: Long): IntArray {
        var state = seed and 0xFFFFFFFF
        val positions = LinkedHashSet<Int>()
        while (positions.size < 69) {
            state = (state * 1664525 + 1013904223) and 0xFFFFFFFF
            positions += (state.toDouble() / 4294967296.0 * range).toInt()
        }
        return positions.toIntArray().apply { sort() }
    }

    private fun sharedSecret(serverKey: ByteArray): ByteArray {
        val curve = AlgorithmParameters.getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, serverKey.copyOfRange(1, 33)), BigInteger(1, serverKey.copyOfRange(33, 65)))
        val publicKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve))
        return KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }
    }

    private class Mode(val transformation: String, val ivOffset: Int, val length: Int)

    companion object {
        // First byte of the encrypted image: cipher, IV offset in cpx and how many bytes are encrypted.
        private val MODES = mapOf(
            3 to Mode("AES/CTR/NoPadding", 25, 25600),
            6 to Mode("AES/CBC/PKCS5Padding", 33, 21520),
            7 to Mode("AES/CTR/NoPadding", 25, 25600),
            8 to Mode("AES/CTR/NoPadding", 31, 25600),
        )
    }
}
