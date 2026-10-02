package io.github.chamsser.gymvi.evidence

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/evidence")
class EvidenceController(private val service: EvidenceService) {
    @GetMapping("/{evidence_id}")
    fun find(
        @PathVariable("evidence_id") evidenceId: String,
        request: HttpServletRequest,
    ): ApiSuccess<EvidenceResponse> {
        val evidence = service.find(evidenceId)
        return ApiSuccess(
            data = evidence,
            meta = ApiMeta(
                request.requestId(),
                evidence.dataset.datasetVersion,
                evidence.dataset.asOf,
            ),
        )
    }
}
