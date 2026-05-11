package example.otp

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

internal fun interface OTPGenerator {
    fun generate(): String
}

internal class DefaultOTPGenerator(
    private val length: Int = 6,
    private val allowedChars: List<Char> = ('A'..'Z').toList(),
    private val random: Random = SecureRandom().asKotlinRandom(),
) : OTPGenerator {
    init {
        require(length > 0) { "length must be positive, was $length" }
        require(allowedChars.isNotEmpty()) { "allowedChars must not be empty" }
    }

    override fun generate(): String = (1..length).map { allowedChars.random(random) }.joinToString("")
}
