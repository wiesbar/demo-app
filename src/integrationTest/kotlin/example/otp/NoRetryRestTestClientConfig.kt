package example.otp

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.http.server.LocalTestWebServer
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.test.web.servlet.client.RestTestClient

@TestConfiguration
internal class NoRetryRestTestClientConfig {
    @Bean
    internal fun restTestClient(applicationContext: ApplicationContext): RestTestClient {
        val server = LocalTestWebServer.obtain(applicationContext)
        val client = HttpClients.custom().disableAutomaticRetries().build()
        val factory = HttpComponentsClientHttpRequestFactory(client)
        return RestTestClient
            .bindToServer(factory)
            .uriBuilderFactory(server.uriBuilderFactory())
            .build()
    }
}
