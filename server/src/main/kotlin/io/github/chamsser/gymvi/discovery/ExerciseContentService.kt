package io.github.chamsser.gymvi.discovery

import io.github.chamsser.gymvi.catalog.ApiValidationException
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

enum class ExerciseSport {
    SWIMMING, RUNNING, STRETCHING, FITNESS, YOGA_PILATES, DANCE, TENNIS, BADMINTON,
    TABLE_TENNIS, GOLF, SQUASH, SKATING, TEAM_BALL, MARTIAL_ARTS, CLIMBING,
}

data class ExerciseContentItem(
    val contentId: String,
    val title: String,
    val summary: String,
    val category: String,
    val sportTags: List<String>,
    val audience: String,
    val thumbnailUrl: String,
    val contentUrl: String,
    val sourceName: String,
    val sourceUrl: String,
    val checkedAt: String?,
    val usageTerms: String?,
) {
    fun forFeed() = ExerciseContentResponse(
        contentId, title, summary, category, audience, thumbnailUrl, contentUrl, sourceName, sourceUrl,
    )
}

data class ExerciseContentsData(val contents: List<ExerciseContentItem>, val sport: String?)
data class ExerciseContentsResult(val data: ExerciseContentsData, val datasetVersion: String)

internal data class LoadedExerciseContents(val items: List<ExerciseContentItem>, val datasetVersion: String)

@Service
class ExerciseContentService {
    private val loaded = loadResource()
    private val byId = loaded.items.associateBy(ExerciseContentItem::contentId)

    fun find(sport: String?, limit: Int = 10): ExerciseContentsResult {
        if (sport != null && ExerciseSport.entries.none { it.name == sport }) {
            throw ApiValidationException("VALIDATION_SPORT", "지원하지 않는 운동 종목입니다.")
        }
        if (limit !in 1..20) {
            throw ApiValidationException("VALIDATION_LIMIT", "결과 제한은 1부터 20까지입니다.")
        }
        val matches = loaded.items.asSequence().filter { sport == null || sport in it.sportTags }.take(limit).toList()
        return ExerciseContentsResult(ExerciseContentsData(matches, sport), loaded.datasetVersion)
    }

    fun forFeed(contentId: String): ExerciseContentResponse =
        requireNotNull(byId[contentId]) { "Curated exercise content is missing: $contentId" }.forFeed()

    companion object {
        private const val RESOURCE = "curated/exercise-contents.json"
        private val CATEGORIES = setOf("POOL", "GYM", "FIELD", "BALL", "OTHER")

        private fun loadResource(): LoadedExerciseContents {
            val stream = ExerciseContentService::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("Curated exercise contents resource is missing: $RESOURCE")
            return stream.use { parse(it.readBytes()) }
        }

        internal fun parse(bytes: ByteArray): LoadedExerciseContents {
            val root = try {
                ObjectMapper().readTree(bytes)
            } catch (error: Exception) {
                throw IllegalArgumentException("Curated exercise contents JSON is invalid", error)
            }
            require(root.isObject && root.path("schema_version").toString() == "1") {
                "Curated exercise contents schema_version must be 1"
            }
            val itemNodes = root.path("items")
            require(itemNodes.isArray) { "Curated exercise contents items must be an array" }
            val ids = mutableSetOf<String>()
            val items = itemNodes.values().mapIndexed { index, node ->
                require(node.isObject) { "Curated exercise contents item $index must be an object" }
                val id = node.text("content_id")
                require(ids.add(id)) { "Duplicate curated exercise content id: $id" }
                val category = node.text("category")
                require(category in CATEGORIES) { "Unknown curated exercise content category for $id: $category" }
                val tags = node.path("sport_tags")
                require(tags.isArray && tags.values().isNotEmpty()) {
                    "Curated exercise content sport_tags must be a non-empty array for $id"
                }
                val sportTags = tags.values().map { tag ->
                    require(tag.isString && ExerciseSport.entries.any { it.name == tag.stringValue() }) {
                        "Unknown curated exercise content sport tag for $id: $tag"
                    }
                    tag.stringValue()
                }
                ExerciseContentItem(
                    contentId = id,
                    title = node.text("title"),
                    summary = node.text("summary"),
                    category = category,
                    sportTags = sportTags,
                    audience = node.text("audience"),
                    thumbnailUrl = node.httpsUrl("thumbnail_url", setOf("img.youtube.com")),
                    contentUrl = node.httpsUrl("content_url", setOf("www.youtube.com", "youtube.com", "youtu.be")),
                    sourceName = node.text("source_name"),
                    sourceUrl = node.httpsUrl("source_url", setOf("nfa.kspo.or.kr")),
                    checkedAt = node.optionalText("checked_at")?.also { value ->
                        try {
                            LocalDate.parse(value)
                        } catch (error: DateTimeParseException) {
                            throw IllegalArgumentException("Invalid curated exercise content checked_at for $id", error)
                        }
                    },
                    usageTerms = node.optionalText("usage_terms"),
                )
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }.take(12)
            return LoadedExerciseContents(items, "curated-exercise-contents-$hash")
        }

        private fun JsonNode.text(name: String): String {
            val value = path(name)
            require(value.isString && value.stringValue().isNotBlank()) {
                "Curated exercise content field is blank or missing: $name"
            }
            return value.stringValue().trim()
        }

        private fun JsonNode.optionalText(name: String): String? {
            val value = path(name)
            require(value.isNull || value.isString) { "Curated exercise content field must be text or null: $name" }
            return if (value.isNull) null else value.stringValue().trim().also {
                require(it.isNotEmpty()) { "Curated exercise content field is blank: $name" }
            }
        }

        private fun JsonNode.httpsUrl(name: String, hosts: Set<String>): String = text(name).also { value ->
            val uri = try {
                URI(value)
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid curated exercise content URL: $name", error)
            }
            require(uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.lowercase(Locale.ROOT) in hosts && uri.rawUserInfo == null) {
                "Curated exercise content URL must use an allowed HTTPS host: $name"
            }
        }
    }
}
