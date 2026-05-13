package example.config

import example.otp.CaffeineRateLimiter
import example.otp.DefaultOTPGenerator
import example.otp.DefaultPasswordRepository
import example.otp.NoOpSmsService
import example.otp.OTPGenerator
import example.otp.OTPService
import example.otp.PasswordRepository
import example.otp.RateLimiter
import example.otp.SMSService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

private val logger = KotlinLogging.logger {}

private const val PROD_PROFILE = "prod"

@OptIn(ExperimentalTime::class)
@Configuration
@Profile("one-time-password")
@EnableConfigurationProperties(OtpProperties::class)
internal class OtpConfig {
    @Bean
    internal fun clock(): Clock = Clock.System

    @Bean
    internal fun otpGenerator(props: OtpProperties): OTPGenerator = DefaultOTPGenerator(length = props.length)

    @Bean
    internal fun passwordRepository(
        props: OtpProperties,
        clock: Clock,
    ): PasswordRepository =
        DefaultPasswordRepository(
            maxAttempts = props.maxAttempts,
            otpExpireTime = props.expireTime.toKotlinDuration(),
            clock = clock,
        )

    @Bean
    internal fun rateLimiter(
        props: OtpProperties,
        clock: Clock,
    ): RateLimiter = CaffeineRateLimiter(props.rateLimit, clock)

    @Bean
    internal fun smsService(environment: Environment): SMSService {
        check(PROD_PROFILE !in environment.activeProfiles) {
            "NoOpSmsService is not allowed when the 'prod' profile is active; wire a real SMSService bean."
        }
        logger.warn { "No real SMSService is wired; using NoOpSmsService which does not deliver OTPs." }
        return NoOpSmsService()
    }

    @Bean
    internal fun otpService(
        sms: SMSService,
        generator: OTPGenerator,
        repository: PasswordRepository,
    ): OTPService = OTPService(sms, generator, repository)
}
