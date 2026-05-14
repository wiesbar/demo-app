package example.otp

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

internal class Sha256HmacOtpHasherTest :
    FunSpec({
        val pepper = "test-pepper-value"

        test("algorithm discriminator is HMAC-SHA-256") {
            Sha256HmacOtpHasher(pepper).algorithm shouldBe "HMAC-SHA-256"
        }

        test("hash is deterministic for the same otp and pepper") {
            val hasher = Sha256HmacOtpHasher(pepper)

            hasher.hash("ABC123") shouldBe hasher.hash("ABC123")
        }

        test("hash produces a 32-byte digest") {
            Sha256HmacOtpHasher(pepper).hash("ABC123") shouldHaveSize 32
        }

        test("different otps produce different hashes") {
            val hasher = Sha256HmacOtpHasher(pepper)

            hasher.hash("ABC123") shouldNotBe hasher.hash("XYZ789")
        }

        test("different peppers produce different hashes for the same otp") {
            val one = Sha256HmacOtpHasher("pepper-one")
            val two = Sha256HmacOtpHasher("pepper-two")

            one.hash("ABC123") shouldNotBe two.hash("ABC123")
        }

        context("matches returns expected result for otp against its own hash") {
            withData(
                nameFn = { (otp, expected) -> "matches returns '$expected' for otp=$otp" },
                listOf(
                    "ABC123" to true,
                    "WRONG123" to false,
                ),
            ) { (otp, expected) ->
                val hasher = Sha256HmacOtpHasher(pepper)
                val hash = hasher.hash("ABC123")

                hasher.matches(otp, hash, "HMAC-SHA-256") shouldBe expected
            }
        }

        test("matches returns false when the algorithm discriminator does not match") {
            val hasher = Sha256HmacOtpHasher(pepper)
            val hash = hasher.hash("ABC123")

            hasher.matches("ABC123", hash, "HMAC-SHA-512") shouldBe false
        }
    })
