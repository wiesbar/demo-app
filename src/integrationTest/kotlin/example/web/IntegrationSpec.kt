package example.web

import example.catalog.registerElasticsearchUri
import io.kotest.core.spec.style.FunSpec
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

abstract class IntegrationSpec(
    body: FunSpec.() -> Unit = {},
) : FunSpec(body) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun elasticsearchProperties(registry: DynamicPropertyRegistry) {
            registerElasticsearchUri(registry)
        }
    }
}
