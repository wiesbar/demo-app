package example.config

import example.otp.DefaultOTPGenerator
import example.otp.DefaultPasswordRepository
import example.otp.LoggingSmsService
import example.otp.OTPGenerator
import example.otp.OTPService
import example.otp.PasswordRepository
import example.otp.SMSService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

@ConfigurationProperties(prefix = "one-time-password")
internal data class OtpProperties(
    val length: Int,
    val maxAttempts: Int,
    val expireTime: Duration,
)

@OptIn(ExperimentalTime::class)
@Configuration
@Profile("one-time-password")
@EnableConfigurationProperties(OtpProperties::class)
internal class OtpConfig {
    @Bean
    internal fun otpGenerator(props: OtpProperties): OTPGenerator = DefaultOTPGenerator(length = props.length)

    @Bean
    internal fun passwordRepository(props: OtpProperties): PasswordRepository =
        DefaultPasswordRepository(
            maxAttempts = props.maxAttempts,
            otpExpireTime = props.expireTime.toKotlinDuration(),
        )

    @Bean
    internal fun smsService(): SMSService = LoggingSmsService()

    @Bean
    internal fun otpService(
        sms: SMSService,
        generator: OTPGenerator,
        repository: PasswordRepository,
    ): OTPService = OTPService(sms, generator, repository)
}
