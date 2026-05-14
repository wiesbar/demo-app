package example.config

import example.otp.CaffeineRateLimiter
import example.otp.DefaultOTPGenerator
import example.otp.DefaultPasswordRepository
import example.otp.JooqPasswordRepository
import example.otp.JooqRateLimiter
import example.otp.NoOpSmsService
import example.otp.OTPGenerator
import example.otp.OTPService
import example.otp.OtpHasher
import example.otp.OtpSweeper
import example.otp.PasswordRepository
import example.otp.PostgresClock
import example.otp.RateLimiter
import example.otp.SMSService
import example.otp.Sha256HmacOtpHasher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

private val logger = KotlinLogging.logger {}

private const val PROD_PROFILE = "prod"
private const val PERSISTENT_OTP = "persistent-otp"

@OptIn(ExperimentalTime::class)
@Configuration
@Profile("one-time-password")
@EnableScheduling
@EnableConfigurationProperties(OtpProperties::class)
internal class OtpConfig {
    @Bean
    @Profile("!$PERSISTENT_OTP")
    internal fun clock(): Clock = Clock.System

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun postgresClock(dsl: DSLContext): Clock = PostgresClock(dsl)

    @Bean
    internal fun otpGenerator(props: OtpProperties): OTPGenerator = DefaultOTPGenerator(length = props.length)

    @Bean
    @Profile("!$PERSISTENT_OTP")
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
    @Profile("!$PERSISTENT_OTP")
    internal fun rateLimiter(
        props: OtpProperties,
        clock: Clock,
    ): RateLimiter = CaffeineRateLimiter(props.rateLimit, clock)

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun otpHasher(props: OtpProperties): OtpHasher = Sha256HmacOtpHasher(props.hashPepper)

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun jooqPasswordRepository(
        dsl: DSLContext,
        props: OtpProperties,
        clock: Clock,
        hasher: OtpHasher,
    ): PasswordRepository =
        JooqPasswordRepository(
            dsl = dsl,
            maxAttempts = props.maxAttempts,
            otpExpireTime = props.expireTime.toKotlinDuration(),
            clock = clock,
            hasher = hasher,
        )

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun jooqRateLimiter(
        dsl: DSLContext,
        props: OtpProperties,
        clock: Clock,
    ): RateLimiter = JooqRateLimiter(dsl, props.rateLimit, clock)

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun otpSweeper(
        dsl: DSLContext,
        clock: Clock,
    ): OtpSweeper = OtpSweeper(dsl, clock)

    @Bean
    @Profile(PERSISTENT_OTP)
    internal fun otpSweepSchedule(sweeper: OtpSweeper): OtpSweepSchedule = OtpSweepSchedule(sweeper)

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

internal class OtpSweepSchedule(
    private val sweeper: OtpSweeper,
) {
    @Scheduled(fixedDelayString = "PT1M")
    fun sweep() {
        sweeper.sweep()
    }
}
