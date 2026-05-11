package example.otp

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Suppress("MagicNumber")
class OTPServiceTest :
    FunSpec({
        context("generate") {
            withData(
                nameFn = { it.label },
                listOf(
                    GenerateCase("sends OTP to user", listOf("some-user"), listOf("some-user" to "A")),
                    GenerateCase(
                        "handles multiple users independently",
                        listOf("user-a", "user-b"),
                        listOf("user-a" to "A", "user-b" to "B"),
                    ),
                    GenerateCase(
                        "sends a new OTP each time for same user",
                        listOf("some-user", "some-user", "some-user"),
                        listOf("some-user" to "A", "some-user" to "B", "some-user" to "C"),
                    ),
                ),
            ) { case ->
                val fixture = Fixture()
                case.userIds.forEach(fixture.service::generate)

                fixture.sms.assertSent(*case.expected.toTypedArray())
            }
        }

        context("verify") {
            withData(
                nameFn = { it.label },
                listOf(
                    VerifyCase("returns false for wrong OTP", "some-user", "some-user", "something", false),
                    VerifyCase("returns true for generated OTP", "some-user", "some-user", "A", true),
                    VerifyCase("returns false when no OTP generated", null, "some-user", "A", false),
                    VerifyCase("returns false for different user", "user-a", "user-b", "A", false),
                ),
            ) { case ->
                val fixture = Fixture()
                case.generateFor?.let(fixture.service::generate)

                fixture.service.verify(case.verifyUserId, case.otp) shouldBe case.expected
            }
        }

        test("regenerating OTP invalidates the previous one") {
            val fixture = Fixture()
            fixture.service.generate("user")
            fixture.service.generate("user")

            fixture.service.verify("user", "A") shouldBe false
            fixture.service.verify("user", "B") shouldBe true
        }

        test("regenerating after expiry produces a usable OTP") {
            val fixture = Fixture()
            fixture.service.generate("user")
            fixture.clock.advance(5.minutes)

            fixture.service.generate("user")

            fixture.service.verify("user", "B") shouldBe true
        }
    })

@OptIn(ExperimentalTime::class)
private class Fixture {
    val sms = SMSServiceMock()
    val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
    private var nextOtp = 'A'
    val service = OTPService(sms, { (nextOtp++).toString() }, DefaultPasswordRepository(clock = clock))
}

private data class GenerateCase(
    val label: String,
    val userIds: List<String>,
    val expected: List<Pair<String, String>>,
)

private data class VerifyCase(
    val label: String,
    val generateFor: String?,
    val verifyUserId: String,
    val otp: String,
    val expected: Boolean,
)
