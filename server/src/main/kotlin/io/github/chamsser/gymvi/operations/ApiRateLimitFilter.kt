package io.github.chamsser.gymvi.operations

import io.github.chamsser.gymvi.api.ApiError
import io.github.chamsser.gymvi.api.ApiErrorResponse
import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ApiRateLimitFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${gymvi.rate-limit.requests-per-minute:120}") requestsPerMinute: Int,
    @Value("\${gymvi.rate-limit.burst-capacity:20}") burstCapacity: Int,
    @Value("\${gymvi.rate-limit.max-tracked-clients:4096}") maxTrackedClients: Int,
) : OncePerRequestFilter() {
    private val limiter = ClientRateLimiter(
        requestsPerMinute = requestsPerMinute,
        burstCapacity = burstCapacity,
        maxTrackedClients = maxTrackedClients,
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith(API_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (limiter.tryAcquire(request.remoteAddr.orEmpty())) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader("Retry-After", "1")
        objectMapper.writeValue(
            response.outputStream,
            ApiErrorResponse(
                error = ApiError(
                    code = "RATE_LIMITED",
                    message = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                    retryable = true,
                ),
                meta = ApiMeta(request.requestId(), null, null),
            ),
        )
    }

    private companion object {
        const val API_PREFIX = "/api/v1/"
    }
}

internal class ClientRateLimiter(
    requestsPerMinute: Int,
    private val burstCapacity: Int,
    private val maxTrackedClients: Int,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val refillPerNanosecond: Double
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val overflowBucket: TokenBucket
    private val acquisitions = AtomicLong()

    init {
        require(requestsPerMinute > 0)
        require(burstCapacity > 0)
        require(maxTrackedClients > 0)
        refillPerNanosecond = requestsPerMinute.toDouble() / NANOS_PER_MINUTE
        overflowBucket = TokenBucket(burstCapacity.toDouble(), nanoTime())
    }

    fun tryAcquire(clientId: String): Boolean {
        val now = nanoTime()
        if (acquisitions.incrementAndGet() % CLEANUP_INTERVAL == 0L) {
            evictInactiveBuckets(now)
        }
        val normalizedClientId = clientId.ifBlank { UNKNOWN_CLIENT }
        val bucket = buckets[normalizedClientId] ?: createBucket(normalizedClientId, now)
        return synchronized(bucket) {
            val elapsed = (now - bucket.lastRefillNanos).coerceAtLeast(0L)
            bucket.tokens = min(
                burstCapacity.toDouble(),
                bucket.tokens + elapsed * refillPerNanosecond,
            )
            bucket.lastRefillNanos = now
            bucket.lastSeenNanos = now
            if (bucket.tokens < 1.0) {
                false
            } else {
                bucket.tokens -= 1.0
                true
            }
        }
    }

    internal fun trackedClientCount(): Int = buckets.size

    private fun createBucket(clientId: String, now: Long): TokenBucket =
        synchronized(buckets) {
            buckets[clientId]
                ?: if (buckets.size >= maxTrackedClients) {
                    overflowBucket
                } else {
                    TokenBucket(burstCapacity.toDouble(), now).also {
                        buckets[clientId] = it
                    }
                }
        }

    private fun evictInactiveBuckets(now: Long) {
        buckets.entries.removeIf { (_, bucket) ->
            now - bucket.lastSeenNanos > INACTIVE_BUCKET_NANOS
        }
    }

    private data class TokenBucket(
        var tokens: Double,
        var lastRefillNanos: Long,
        @Volatile var lastSeenNanos: Long = lastRefillNanos,
    )

    private companion object {
        const val NANOS_PER_MINUTE = 60_000_000_000.0
        const val INACTIVE_BUCKET_NANOS = 600_000_000_000L
        const val CLEANUP_INTERVAL = 256L
        const val UNKNOWN_CLIENT = "unknown"
    }
}
