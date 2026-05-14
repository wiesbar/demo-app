package example.otp

import org.springframework.test.context.DynamicPropertyRegistry

internal fun registerOtpDatasource(registry: DynamicPropertyRegistry) {
    registry.add("spring.datasource.url") { OtpPostgresContainer.instance.jdbcUrl }
    registry.add("spring.datasource.username") { OtpPostgresContainer.instance.username }
    registry.add("spring.datasource.password") { OtpPostgresContainer.instance.password }
}
