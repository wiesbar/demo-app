package example.otp

import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer

internal object OtpPostgresContainer {
    private const val IMAGE = "postgres:16-alpine"

    val instance: PostgreSQLContainer<*> =
        PostgreSQLContainer(IMAGE).also { container ->
            container.start()
            Runtime.getRuntime().addShutdownHook(Thread(container::stop))
        }

    val dsl: DSLContext by lazy {
        Flyway
            .configure()
            .dataSource(instance.jdbcUrl, instance.username, instance.password)
            .load()
            .migrate()
        DSL.using(instance.jdbcUrl, instance.username, instance.password)
    }
}
