package example.otp

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal interface OtpHasher {
    val algorithm: String

    fun hash(otp: String): ByteArray

    fun matches(
        otp: String,
        hash: ByteArray,
        algorithm: String,
    ): Boolean
}

private const val HMAC_SHA_256 = "HmacSHA256"

internal class Sha256HmacOtpHasher(
    pepper: String,
) : OtpHasher {
    override val algorithm: String = "HMAC-SHA-256"

    private val key = SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), HMAC_SHA_256)

    override fun hash(otp: String): ByteArray =
        Mac.getInstance(HMAC_SHA_256).run {
            init(key)
            doFinal(otp.toByteArray(Charsets.UTF_8))
        }

    override fun matches(
        otp: String,
        hash: ByteArray,
        algorithm: String,
    ): Boolean = algorithm == this.algorithm && MessageDigest.isEqual(hash(otp), hash)
}
