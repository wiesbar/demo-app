package example.otp

import example.web.IntegrationSpec
import io.kotest.assertions.assertSoftly
import io.kotest.datatest.withData
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "one-time-password.length=4",
        "one-time-password.max-attempts=2",
    ],
)
@AutoConfigureRestTestClient
@ActiveProfiles("one-time-password")
class OtpRequestValidationTest(
    @Autowired private val restClient: RestTestClient,
) : IntegrationSpec({
        extension(SpringExtension)

        context("should reject invalid one-time-password requests") {
            withData(
                nameFn = { it.label },
                listOf(
                    Case(
                        "generate: blank userId returns 400",
                        "/one-time-password/generate",
                        """{"userId":" "}""",
                        "userId must not be blank",
                    ),
                    Case(
                        "generate: empty userId returns 400",
                        "/one-time-password/generate",
                        """{"userId":""}""",
                        "userId must not be blank",
                    ),
                    Case(
                        "verify: blank userId returns 400",
                        "/one-time-password/verify",
                        """{"userId":" ","otp":"ABCDEF"}""",
                        "userId must not be blank",
                    ),
                    Case(
                        "verify: blank otp returns 400",
                        "/one-time-password/verify",
                        """{"userId":"user-x","otp":" "}""",
                        "otp must not be blank",
                    ),
                ),
            ) { case ->
                restClient
                    .post()
                    .uri(case.uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(case.body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<Map<String, String>>()
                    .value { responseBody ->
                        assertSoftly {
                            with(responseBody.shouldNotBeNull()) {
                                this["status"] shouldBe "400"
                                this["message"] shouldBe case.expectedMessage
                            }
                        }
                    }
            }
        }

        context("should reject malformed bodies") {
            withData(
                nameFn = { it.label },
                listOf(
                    MalformedCase("generate: missing userId field returns 400", "/one-time-password/generate", "{}"),
                    MalformedCase("verify: malformed JSON returns 400", "/one-time-password/verify", "not-json"),
                ),
            ) { (_, uri, body) ->
                restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }
        }
    })

private data class Case(
    val label: String,
    val uri: String,
    val body: String,
    val expectedMessage: String,
)

private data class MalformedCase(
    val label: String,
    val uri: String,
    val body: String,
)
