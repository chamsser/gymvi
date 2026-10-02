package io.github.chamsser.gymvi.mobility

import io.github.chamsser.gymvi.operations.ClientRateLimiter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RouteRequestGuard(
    @Value("\${gymvi.route.requests-per-minute:6}") requestsPerMinute: Int,
    @Value("\${gymvi.route.burst-capacity:3}") burstCapacity: Int,
    @Value("\${gymvi.rate-limit.max-tracked-clients:4096}") maxTrackedClients: Int,
) {
    private val limiter = ClientRateLimiter(requestsPerMinute, burstCapacity, maxTrackedClients)

    fun requirePermit(clientAddress: String) {
        if (!limiter.tryAcquire(clientAddress)) throw RouteRateLimitedException()
    }
}
