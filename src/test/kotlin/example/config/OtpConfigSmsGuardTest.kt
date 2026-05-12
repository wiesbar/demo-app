package example.config

import example.otp.NoOpSmsService
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment

class OtpConfigSmsGuardTest :
    FunSpec({
        test("smsService returns a NoOpSmsService when the prod profile is not active") {
            val config = OtpConfig()
            val env: Environment = MockEnvironment().withActiveProfiles("one-time-password")

            val sms = config.smsService(env)

            sms.shouldBeInstanceOf<NoOpSmsService>()
        }

        test("smsService throws IllegalStateException when prod profile is active") {
            val config = OtpConfig()
            val env = MockEnvironment().withActiveProfiles("prod", "one-time-password")

            val ex = shouldThrow<IllegalStateException> { config.smsService(env) }

            ex shouldHaveMessage "NoOpSmsService is not allowed when the 'prod' profile is active; " +
                "wire a real SMSService bean."
        }

        test("smsService does not throw when only non-prod profiles are active") {
            val config = OtpConfig()
            val env = MockEnvironment().withActiveProfiles("staging", "one-time-password")

            shouldNotThrow<IllegalStateException> { config.smsService(env) }
        }
    })

private fun MockEnvironment.withActiveProfiles(vararg profiles: String): MockEnvironment {
    profiles.forEach { addActiveProfile(it) }
    return this
}
