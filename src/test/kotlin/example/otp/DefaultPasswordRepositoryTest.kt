package example.otp

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class DefaultPasswordRepositoryTest :
    PasswordRepositoryContract(
        newRepo = { clock, maxAttempts, expireTime ->
            DefaultPasswordRepository(maxAttempts = maxAttempts, otpExpireTime = expireTime, clock = clock)
        },
    )
