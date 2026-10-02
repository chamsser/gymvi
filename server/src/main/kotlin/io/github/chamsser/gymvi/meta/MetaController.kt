package io.github.chamsser.gymvi.meta

import io.github.chamsser.gymvi.ai.AiModelProvider
import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.ApiWarning
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class VersionResponse(
    val appVersion: String,
    val apiVersion: String,
    val schemaVersion: String,
    val datasetStatus: String,
    val dependencies: Map<String, String>,
    val aiModel: AiModelStatus,
    val datasets: List<DatasetSourceResponse>,
)

/**
 * Server setup only: it does not promise remaining budget, a model reply for the next turn or the
 * model that answered one, and it never carries the key, endpoint or ledger path.
 */
data class AiModelStatus(
    val provider: String?,
    val configuredModel: String?,
    val status: String,
)

@RestController
@RequestMapping("/api/v1/meta")
internal class MetaController(
    private val activeDatasets: ActiveDatasetCatalog,
    private val sources: DatasetSourceRegistry,
    modelProvider: AiModelProvider,
    @param:Value("\${gymvi.app-version}") private val appVersion: String,
    @param:Value("\${gymvi.api-version}") private val apiVersion: String,
    @param:Value("\${gymvi.schema-version}") private val schemaVersion: String,
) {
    // Read from the provider bean itself, so it changes only where the real OpenAI adapter is built.
    private val aiModel = modelProvider.configuredModel.let { configured ->
        AiModelStatus(
            provider = configured?.provider,
            configuredModel = configured?.model,
            status = if (configured == null) "NOT_CONFIGURED" else "CONFIGURED",
        )
    }

    @GetMapping("/version")
    fun version(request: HttpServletRequest): ApiSuccess<VersionResponse> {
        // One read builds the whole response, so meta and datasets never mix two publishes.
        val active = activeDatasets.activeDatasets()
        val dataset = active.singleOrNull { it.datasetKind == "FACILITY" }
        val connected = dataset != null
        return ApiSuccess(
            data = VersionResponse(
                appVersion = appVersion,
                apiVersion = apiVersion,
                schemaVersion = schemaVersion,
                datasetStatus = if (connected) "ACTIVE" else "NOT_CONNECTED",
                dependencies = mapOf("facility_dataset" to if (connected) "UP" else "NOT_CONNECTED"),
                aiModel = aiModel,
                datasets = if (connected) sources.describe(active) else emptyList(),
            ),
            meta = ApiMeta(request.requestId(), dataset?.datasetVersion, dataset?.asOf),
            warnings = if (connected) {
                emptyList()
            } else {
                listOf(ApiWarning("DATASET_NOT_CONNECTED", "시설 데이터셋이 연결되지 않았습니다."))
            },
        )
    }
}
