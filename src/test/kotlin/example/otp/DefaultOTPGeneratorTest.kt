package example.otp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import kotlin.random.Random

class DefaultOTPGeneratorTest :
    FunSpec({
        test("generates OTP of default length 6 from A-Z") {
            val otp = DefaultOTPGenerator(random = Random.Default).generate()

            otp shouldHaveLength 6
            otp shouldMatch Regex("[A-Z]{6}")
        }

        test("generates OTP of configured length") {
            val otp = DefaultOTPGenerator(length = 10, random = Random.Default).generate()

            otp shouldHaveLength 10
        }

        test("uses only configured allowed chars") {
            val generator = DefaultOTPGenerator(length = 50, allowedChars = listOf('0', '1'), random = Random.Default)

            generator.generate() shouldMatch Regex("[01]{50}")
        }

        test("single allowed char produces deterministic output") {
            val otp = DefaultOTPGenerator(length = 4, allowedChars = listOf('X'), random = Random.Default).generate()

            otp shouldBe "XXXX"
        }

        test("rejects zero length") {
            shouldThrow<IllegalArgumentException> { DefaultOTPGenerator(length = 0, random = Random.Default) }
                .message shouldBe "length must be positive, was 0"
        }

        test("rejects negative length") {
            shouldThrow<IllegalArgumentException> { DefaultOTPGenerator(length = -1, random = Random.Default) }
                .message shouldBe "length must be positive, was -1"
        }

        test("rejects empty allowedChars") {
            shouldThrow<IllegalArgumentException> {
                DefaultOTPGenerator(allowedChars = emptyList(), random = Random.Default)
            }.message shouldBe "allowedChars must not be empty"
        }
    })
