package io.github.chamsser.gymvi.geocoding

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class GeocodingConfiguration {
    @Bean
    fun geocodingProvider(
        objectMapper: ObjectMapper,
        @Value("\${gymvi.geocoding.naver.client-id:}") clientId: String,
        @Value("\${gymvi.geocoding.naver.client-secret:}") clientSecret: String,
        @Value("\${gymvi.geocoding.naver.endpoint:https://maps.apigw.ntruss.com/map-geocode/v2/geocode}")
        endpoint: String,
    ): GeocodingProvider = if (clientId.isBlank() || clientSecret.isBlank()) {
        GeocodingProvider { _, _ -> throw GeocodingProviderUnavailableException() }
    } else {
        NaverGeocodingProvider(
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
