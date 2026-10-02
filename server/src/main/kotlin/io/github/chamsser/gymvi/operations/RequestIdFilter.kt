package io.github.chamsser.gymvi.operations

import io.github.chamsser.gymvi.api.REQUEST_ID_ATTRIBUTE
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import kotlin.time.TimeSource

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RequestIdFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = UUID.randomUUID().toString()
        val started = TimeSource.Monotonic.markNow()
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        MDC.put(MDC_REQUEST_ID, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            log.debug(
                "request_completed method={} path={} status={} duration_ms={}",
                request.method,
                request.requestURI,
                response.status,
                started.elapsedNow().inWholeMilliseconds,
            )
            MDC.remove(MDC_REQUEST_ID)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        private const val MDC_REQUEST_ID = "request_id"
    }
}
