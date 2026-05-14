package example.otp

import example.otp.jooq.Tables.OTP_ENTRIES
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class JooqPasswordRepositoryTest :
    PasswordRepositoryContract(
        reset = { OtpPostgresContainer.dsl.truncate(OTP_ENTRIES).execute() },
        newRepo = { clock, maxAttempts, expireTime ->
            JooqPasswordRepository(
                dsl = OtpPostgresContainer.dsl,
                maxAttempts = maxAttempts,
                otpExpireTime = expireTime,
                clock = clock,
                hasher = Sha256HmacOtpHasher("integration-test-pepper"),
            )
        },
    )
