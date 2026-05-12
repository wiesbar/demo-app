package example.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.ApplicationRunner

class IndexInitializerTypeTest :
    FunSpec({
        test("IndexInitializer should not implement Spring's ApplicationRunner") {
            ApplicationRunner::class.java.isAssignableFrom(IndexInitializer::class.java) shouldBe false
        }
    })
