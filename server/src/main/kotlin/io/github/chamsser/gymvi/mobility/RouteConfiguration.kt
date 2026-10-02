package io.github.chamsser.gymvi.mobility

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class RouteConfiguration {
    @Bean
    fun routeProvider(
        objectMapper: ObjectMapper,
        @Value("\${gymvi.route.naver.client-id:}") clientId: String,
        @Value("\${gymvi.route.naver.client-secret:}") clientSecret: String,
        @Value("\${gymvi.route.naver.endpoint:https://maps.apigw.ntruss.com/map-direction/v1/driving}")
        endpoint: String,
    ): RouteProvider = if (clientId.isBlank() || clientSecret.isBlank()) {
        RouteProvider { _, _ -> throw RouteProviderUnavailableException() }
    } else {
        NaverDirectionsProvider(
            clientId = clientId,
            clientSecret = clientSecret,
            endpoint = endpoint,
            objectMapper = objectMapper,
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
        )
    }
}
