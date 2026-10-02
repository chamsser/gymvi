package io.github.chamsser.gymvi.geocoding

import io.github.chamsser.gymvi.operations.ClientRateLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GeocodingRequestGuard(
    @Value("\${gymvi.geocoding.requests-per-minute:10}") requestsPerMinute: Int,
    @Value("\${gymvi.geocoding.burst-capacity:3}") burstCapacity: Int,
    @Value("\${gymvi.rate-limit.max-tracked-clients:4096}") maxTrackedClients: Int,
) {
    private val limiter = ClientRateLimiter(requestsPerMinute, burstCapacity, maxTrackedClients)

    fun requirePermit(clientAddress: String) {
        if (!limiter.tryAcquire(clientAddress)) throw GeocodingRateLimitedException()
    }
}
