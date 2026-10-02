package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.ApiError
import io.github.chamsser.gymvi.api.ApiErrorResponse
import io.github.chamsser.gymvi.api.requestId
import io.github.chamsser.gymvi.catalog.GymviApiException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.databind.ObjectMapper
import java.io.IOException

@RestController
internal class AiController(private val service: AiTurnService, private val mapper: ObjectMapper) {
    @PostMapping("/api/v1/ai/turns")
    fun turn(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
    ): ApiSuccess<AiTurnData> {
        val result = service.turn(AiRequestParser.turn(body))
        return ApiSuccess(
            data = result.data,
            meta = ApiMeta(request.requestId(), result.datasetVersion, result.asOf),
            warnings = result.warnings,
        )
    }

    @PostMapping("/api/v1/ai/turns/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @RequestBody body: Map<String, Any?>,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<StreamingResponseBody> {
        // Validate before starting the stream, preserving normal 400 JSON responses.
        val input = AiRequestParser.turn(body)
        val requestId = request.requestId()
        val stream = StreamingResponseBody { output ->
            fun send(event: String, data: Any) {
                try {
                    output.write("event: $event\ndata: ${mapper.writeValueAsString(data)}\n\n".toByteArray(Charsets.UTF_8))
                    // Spring's response stream suppresses flush by default. Commit this
                    // endpoint's servlet buffer without changing the global HTTP setting.
                    response.flushBuffer()
                } catch (exception: IOException) {
                    throw AiClientDisconnectedException(exception)
                }
            }
            try {
                output.write(": connected\n\n".toByteArray(Charsets.UTF_8))
                response.flushBuffer()
                val result = service.turn(input) { delta -> send("reply_delta", mapOf("delta" to delta)) }
                send("complete", ApiSuccess(result.data, ApiMeta(requestId, result.datasetVersion, result.asOf), result.warnings))
            } catch (_: AiClientDisconnectedException) {
                // The client has left. Do not retry the paid turn or write another event.
            } catch (_: IOException) {
                // Includes a disconnect before the initial comment was flushed.
            } catch (exception: Exception) {
                val error = if (exception is GymviApiException) {
                    ApiError(exception.code, exception.message, exception.retryable)
                } else {
                    ApiError("INTERNAL", "답변을 이어받지 못했어요. 다시 시도해 주세요.", true)
                }
                send("error", ApiErrorResponse(error, ApiMeta(requestId, null, null)))
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/event-stream;charset=UTF-8"))
            .header("Cache-Control", "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .body(stream)
    }
}
