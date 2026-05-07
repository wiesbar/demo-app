package example.catalog

import example.web.ProductWithIdDto
import example.web.SearchResponseDto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

internal fun RestTestClient.ResponseSpec.expectSearchHits(expected: List<ProductWithIdDto>) {
    expectStatus().isOk
    expectBody<SearchResponseDto>().value { body ->
        withClue("missing response body") {
            body.shouldNotBeNull()
        }.hits.run {
            assertSoftly {
                map { it.product } shouldBe expected
                forEach { it.score shouldBeGreaterThan 0.0 }
            }
        }
    }
}

internal fun RestTestClient.ResponseSpec.expectFirstSearchHit(expected: ProductWithIdDto) {
    expectStatus().isOk
    expectBody<SearchResponseDto>().value { body ->
        withClue("missing response body") {
            body.shouldNotBeNull()
        }.hits.run {
            assertSoftly {
                shouldNotBeEmpty()
                first().product shouldBe expected
                first().score shouldBeGreaterThan 0.0
            }
        }
    }
}
