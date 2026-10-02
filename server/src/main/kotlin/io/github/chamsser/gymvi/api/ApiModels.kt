package io.github.chamsser.gymvi.api

import jakarta.servlet.http.HttpServletRequest
import java.time.Instant

data class ApiMeta(
    val requestId: String,
    val datasetVersion: String?,
    val asOf: Instant?,
)

data class ApiWarning(
    val code: String,
    val message: String,
)

data class ApiSuccess<T>(
    val data: T,
    val meta: ApiMeta,
    val warnings: List<ApiWarning> = emptyList(),
)

data class ApiError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

data class ApiErrorResponse(
    val error: ApiError,
    val meta: ApiMeta,
    val warnings: List<ApiWarning> = emptyList(),
)

fun HttpServletRequest.requestId(): String =
    getAttribute(REQUEST_ID_ATTRIBUTE) as? String ?: "missing-request-id"

const val REQUEST_ID_ATTRIBUTE = "gymvi.request_id"
