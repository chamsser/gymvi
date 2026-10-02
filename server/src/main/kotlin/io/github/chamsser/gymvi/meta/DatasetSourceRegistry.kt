package io.github.chamsser.gymvi.meta

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Where an ACTIVE dataset came from. `provider` and `license` are facts checked against that exact
 * published snapshot; anything nobody checked stays null instead of being guessed.
 */
data class DatasetSourceResponse(
    val datasetKind: String,
    val datasetVersion: String,
    val asOf: Instant,
    val provider: String?,
    val license: DatasetLicenseResponse?,
)

data class DatasetLicenseResponse(
    val name: String?,
    val url: String?,
    val attribution: String?,
)

data class DatasetSourceEntry(
    val datasetKind: String,
    val datasetVersion: String,
    val sourceDatasetId: String,
    val rawSha256: String,
    val provider: String?,
    val license: DatasetLicenseResponse,
    val checkedAt: OffsetDateTime,
)

/**
 * Checked facts keyed by the four values that identify one published snapshot. A new publish
 * matches no entry, so its provider and license read as unknown until someone checks it.
 */
@Component
class DatasetSourceRegistry(private val entries: List<DatasetSourceEntry>) {
    @Autowired
    constructor() : this(loadResource(RESOURCE))

    fun describe(rows: List<ActiveDatasetRow>): List<DatasetSourceResponse> =
        rows.filter { it.datasetKind in KINDS }
            .sortedBy { KINDS.indexOf(it.datasetKind) }
            .map { row ->
                val entry = entries.firstOrNull { it.matches(row) }
                DatasetSourceResponse(
                    datasetKind = row.datasetKind,
                    datasetVersion = row.datasetVersion,
                    asOf = row.asOf,
                    provider = entry?.provider,
                    license = entry?.let { licenseFor(it, row) },
                )
            }

    companion object {
        private const val RESOURCE = "curated/dataset-sources.json"
        private val KINDS = listOf("FACILITY", "PROGRAM")
        private val ROOT_KEYS = setOf("schema_version", "description", "entries")
        private val ENTRY_KEYS = setOf(
            "dataset_kind",
            "dataset_version",
            "source_dataset_id",
            "raw_sha256",
            "provider",
            "license",
            "checked_at",
        )
        private val LICENSE_KEYS = setOf("name", "url", "attribution")
        private val IDENTIFIER = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val SECRET_WORD = Regex("(?i)(service_?key|api_?key|s_key|access_?token|secret|password)")
        private val SECRET_QUERY_NAME =
            Regex("(?i)^(service_?key|api_?key|s_?key|key|(access_)?token|secret|password)$")
        private val HIDDEN_CHARACTER_TYPES = listOf(
            Character.CONTROL,
            Character.FORMAT,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
            Character.SURROGATE,
            Character.PRIVATE_USE,
            Character.UNASSIGNED,
        ).map(Byte::toInt).toSet()

        fun loadResource(resource: String): List<DatasetSourceEntry> {
            val stream = DatasetSourceRegistry::class.java.classLoader.getResourceAsStream(resource)
                ?: error("Dataset source registry is missing: $resource")
            return stream.use { parse(ObjectMapper().readTree(it)) }
        }

        fun parse(root: JsonNode): List<DatasetSourceEntry> {
            require(root.keys() == ROOT_KEYS) { "Unexpected dataset source registry keys." }
            require(root.path("schema_version").stringOrNull() == "1.0.0") { "Unsupported dataset source schema." }
            require(!root.path("description").stringOrNull().isNullOrBlank()) {
                "Dataset source registry needs a description."
            }
            val entriesNode = root.path("entries")
            require(entriesNode.isArray) { "Dataset source entries must be a list." }
            val entries = entriesNode.values().map(::parseEntry)
            require(entries.distinctBy { it.datasetKind to it.datasetVersion }.size == entries.size) {
                "Duplicate dataset source entry."
            }
            return entries
        }

        private fun parseEntry(entry: JsonNode): DatasetSourceEntry {
            require(entry.keys() == ENTRY_KEYS) { "Unexpected dataset source entry keys." }
            val license = entry.path("license")
            require(license.keys() == LICENSE_KEYS) { "Unexpected dataset license keys." }
            return DatasetSourceEntry(
                datasetKind = entry.requiredText("dataset_kind").also {
                    require(it in KINDS) { "Unknown dataset kind: $it" }
                },
                datasetVersion = entry.identifier("dataset_version"),
                sourceDatasetId = entry.identifier("source_dataset_id"),
                rawSha256 = entry.requiredText("raw_sha256").also {
                    require(SHA256.matches(it)) { "Dataset source raw_sha256 must be 64 lowercase hex digits." }
                },
                provider = entry.optionalDisplayText("provider"),
                license = DatasetLicenseResponse(
                    name = license.optionalDisplayText("name"),
                    url = license.optionalText("url")?.also {
                        require(isPublicUrl(it)) { "Dataset license URL must be a plain public HTTPS address." }
                    },
                    attribution = license.optionalDisplayText("attribution"),
                ),
                checkedAt = OffsetDateTime.parse(entry.requiredText("checked_at")),
            )
        }

        // Registry values win. A stored URL or attribution is reused only after the same checks as
        // the registry, and the license name never comes from the database.
        private fun licenseFor(entry: DatasetSourceEntry, row: ActiveDatasetRow): DatasetLicenseResponse? {
            val license = DatasetLicenseResponse(
                name = entry.license.name,
                url = entry.license.url ?: row.licenseUrl?.trim()?.takeIf(::isPublicUrl),
                attribution = entry.license.attribution ?: row.licenseAttribution?.trim()?.takeIf(::isDisplayText),
            )
            return license.takeUnless { it.name == null && it.url == null && it.attribution == null }
        }

        private fun DatasetSourceEntry.matches(row: ActiveDatasetRow): Boolean =
            datasetKind == row.datasetKind &&
                datasetVersion == row.datasetVersion &&
                sourceDatasetId == row.sourceDatasetId &&
                rawSha256 == row.rawSha256

        private fun isDisplayText(value: String): Boolean =
            value == value.trim() &&
                value.codePointCount(0, value.length) in 1..300 &&
                hasNoHiddenCharacters(value) &&
                '\\' !in value &&
                !SECRET_WORD.containsMatchIn(value)

        private fun isPublicUrl(value: String): Boolean {
            if (value.length !in 1..500 || !hasNoHiddenCharacters(value)) return false
            if (value.any { it.isWhitespace() || it == '\\' }) return false
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return false
            return uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).all { pair ->
                val name = runCatching { URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8) }.getOrNull()
                name != null && !SECRET_QUERY_NAME.matches(name)
            }
        }

        private fun hasNoHiddenCharacters(value: String): Boolean =
            value.codePoints().allMatch { Character.getType(it) !in HIDDEN_CHARACTER_TYPES }

        private fun JsonNode.keys(): Set<String> =
            if (isObject) propertyNames().asSequence().toSet() else emptySet()

        private fun JsonNode.stringOrNull(): String? = takeIf { it.isString }?.stringValue()

        private fun JsonNode.requiredText(name: String): String =
            path(name).stringOrNull() ?: throw IllegalArgumentException("Dataset source field must be text: $name")

        private fun JsonNode.identifier(name: String): String = requiredText(name).also {
            require(IDENTIFIER.matches(it)) { "Dataset source $name is not a plain identifier." }
        }

        private fun JsonNode.optionalText(name: String): String? {
            val node = path(name)
            if (node.isNull) return null
            return node.stringOrNull()
                ?: throw IllegalArgumentException("Dataset source field must be text or null: $name")
        }

        private fun JsonNode.optionalDisplayText(name: String): String? = optionalText(name)?.also {
            require(isDisplayText(it)) { "Dataset source $name is not plain display text." }
        }
    }
}
