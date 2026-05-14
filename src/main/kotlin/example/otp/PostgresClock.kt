package example.otp

import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalTime::class)
internal class PostgresClock(
    private val dsl: DSLContext,
) : Clock {
    override fun now(): Instant {
        val timestamp =
            dsl
                .select(DSL.field("statement_timestamp()", OffsetDateTime::class.java))
                .fetchOne(0, OffsetDateTime::class.java)
        return checkNotNull(timestamp) { "statement_timestamp() returned no row" }
            .toInstant()
            .toKotlinInstant()
    }
}
