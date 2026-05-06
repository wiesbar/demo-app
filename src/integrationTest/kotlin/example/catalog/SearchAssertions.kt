package example.catalog

import example.web.ProductWithIdDto
import example.web.SearchResponseDto
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

internal fun RestTestClient.ResponseSpec.expectSearchHits(expected: List<ProductWithIdDto>) {
    expectStatus().isOk
    expectBody<SearchResponseDto>().value { body ->
        val actual = checkNotNull(body) { "missing response body" }
        assertSoftly {
            with(actual) {
                total shouldBe expected.size
                hits.map { it.product } shouldBe expected
                hits.forEach { it.score shouldBeGreaterThan 0.0 }
            }
        }
    }
}

internal fun RestTestClient.ResponseSpec.expectFirstSearchHit(expected: ProductWithIdDto) {
    expectStatus().isOk
    expectBody<SearchResponseDto>().value { body ->
        val actual = checkNotNull(body) { "missing response body" }
        assertSoftly {
            with(actual.hits) {
                shouldNotBeEmpty()
                first().product shouldBe expected
                first().score shouldBeGreaterThan 0.0
            }
        }
    }
}
