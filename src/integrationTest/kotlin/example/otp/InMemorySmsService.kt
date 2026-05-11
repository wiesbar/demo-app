package example.otp

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap

internal class InMemorySmsService : SMSService {
    private val sent = ConcurrentHashMap<String, String>()

    override fun sendOTP(
        userId: String,
        password: String,
    ) {
        sent[userId] = password
    }

    fun lastOtpFor(userId: String): String? = sent[userId]

    fun reset() {
        sent.clear()
    }

    @TestConfiguration
    internal class TestConfig {
        @Bean
        @Primary
        internal fun smsService(): InMemorySmsService = InMemorySmsService()
    }
}
