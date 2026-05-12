package example.otp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
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

        context("SMS dispatch failure") {
            test("attempts SMS dispatch before storing the OTP") {
                val fixture = OrderRecordingFixture()

                fixture.service.generate("some-user")

                fixture.events shouldContainExactly listOf("send:some-user:A", "store:some-user:A")
            }

            test("does not store the OTP when SMS dispatch fails") {
                val smsError = RuntimeException("upstream provider error")
                val fixture = Fixture(failSmsWith = smsError)

                val actual = shouldThrow<RuntimeException> { fixture.service.generate("some-user") }

                actual shouldBeSameInstanceAs smsError
                fixture.service.verify("some-user", "A") shouldBe false
            }
        }
    })

@OptIn(ExperimentalTime::class)
private class Fixture(
    failSmsWith: Throwable? = null,
) {
    val sms =
        SMSServiceMock(
            onSend = { _, _ -> failSmsWith?.let { throw it } },
        )
    val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
    private var nextOtp = 'A'
    val service =
        OTPService(
            sms,
            { (nextOtp++).toString() },
            DefaultPasswordRepository(maxAttempts = 3, otpExpireTime = 5.minutes, clock = clock),
        )
}

@OptIn(ExperimentalTime::class)
private class OrderRecordingFixture {
    val events = mutableListOf<String>()
    private val passwords =
        object : PasswordRepository {
            override fun store(
                userId: String,
                otp: String,
            ) {
                events += "store:$userId:$otp"
            }

            override fun consumeAttempt(
                userId: String,
                providedOtp: String,
            ): Boolean = false
        }
    private val sms =
        SMSServiceMock(
            onSend = { userId, otp -> events += "send:$userId:$otp" },
        )
    val service = OTPService(sms, { "A" }, passwords)
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
