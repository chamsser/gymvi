package io.github.chamsser.gymvi.api

import io.github.chamsser.gymvi.catalog.GymviApiException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(GymviApiException::class)
    fun handleKnown(exception: GymviApiException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val status = when (exception.code) {
            "DATASET_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE
            "DATA_FACILITY_NOT_FOUND", "DATA_EVIDENCE_NOT_FOUND", "DATA_USAGE_OPTION_NOT_FOUND" ->
                HttpStatus.NOT_FOUND
            "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS
            "PROVIDER_BUDGET_EXHAUSTED", "PROVIDER_ROUTE_UNAVAILABLE",
            "PROVIDER_GEOCODING_BUDGET_EXHAUSTED", "PROVIDER_GEOCODING_UNAVAILABLE",
            "PROVIDER_MEDIA_BUDGET_EXHAUSTED", "PROVIDER_MEDIA_UNAVAILABLE" ->
                HttpStatus.SERVICE_UNAVAILABLE
            "PROVIDER_ROUTE_FAILED", "PROVIDER_GEOCODING_FAILED", "PROVIDER_MEDIA_FAILED" ->
                HttpStatus.BAD_GATEWAY
            else -> HttpStatus.BAD_REQUEST
        }
        return error(status, exception.code, exception.message, exception.retryable, request)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class)
    fun handleInput(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.info("request_validation_failed type={}", exception.javaClass.simpleName)
        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_PARAMETER",
            "요청 매개변수 형식이 올바르지 않습니다.",
            false,
            request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.error(
            "request_failed type={} request_id={}",
            exception.javaClass.simpleName,
            request.requestId(),
        )
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "요청을 처리하지 못했습니다.", true, request)
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        retryable: Boolean,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(
            ApiErrorResponse(
                error = ApiError(code, message, retryable),
                meta = ApiMeta(request.requestId(), null, null),
            ),
        )
}
