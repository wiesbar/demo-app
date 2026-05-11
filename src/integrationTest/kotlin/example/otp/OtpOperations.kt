package example.otp

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

internal fun RestTestClient.generateOtp(userId: String) {
    post()
        .uri("/one-time-password/generate")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"userId":"$userId"}""")
        .exchange()
        .expectStatus()
        .isNoContent
}

internal fun RestTestClient.verifyOtp(
    userId: String,
    otp: String,
): RestTestClient.ResponseSpec =
    post()
        .uri("/one-time-password/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"userId":"$userId","otp":"$otp"}""")
        .exchange()
