package io.github.chamsser.gymvi.ai

import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDate

internal data class AiBudgetRecord(
    val date: LocalDate,
    val dailySpentNanoDollars: Long,
    val totalSpentNanoDollars: Long,
    val reservedNanoDollars: Long,
)

internal interface AiBudgetLedger {
    fun load(): AiBudgetRecord?
    fun save(record: AiBudgetRecord)
}

internal class FileAiBudgetLedger(
    private val path: Path,
    private val objectMapper: ObjectMapper,
) : AiBudgetLedger {
    init {
        require(path.isAbsolute) { "AI budget ledger path must be absolute." }
        require(path.parent != null) { "AI budget ledger path must have a parent directory." }
    }

    override fun load(): AiBudgetRecord? {
        if (!Files.exists(path)) return null
        val size = Files.size(path)
        require(size in 1..MAX_LEDGER_BYTES) { "AI budget ledger size is invalid." }
        val root = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8))
        require(root.isObject) { "AI budget ledger must be a JSON object." }
        require(root.propertyNames().asSequence().toSet() == FIELDS) {
            "AI budget ledger fields are invalid."
        }
        return AiBudgetRecord(
            date = LocalDate.parse(root.get("date").asText()),
            dailySpentNanoDollars = root.nonNegativeLong("daily_spent_nano_dollars"),
            totalSpentNanoDollars = root.nonNegativeLong("total_spent_nano_dollars"),
            reservedNanoDollars = root.nonNegativeLong("reserved_nano_dollars"),
        )
    }

    override fun save(record: AiBudgetRecord) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".gymvi-ai-budget-", ".tmp")
        try {
            val json = objectMapper.writeValueAsString(
                mapOf(
                    "date" to record.date.toString(),
                    "daily_spent_nano_dollars" to record.dailySpentNanoDollars,
                    "total_spent_nano_dollars" to record.totalSpentNanoDollars,
                    "reserved_nano_dollars" to record.reservedNanoDollars,
                ),
            )
            Files.writeString(
                temporary,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun tools.jackson.databind.JsonNode.nonNegativeLong(name: String): Long {
        val value = get(name)?.takeIf(tools.jackson.databind.JsonNode::isIntegralNumber)?.asLong()
            ?: error("AI budget ledger value is invalid: $name")
        require(value >= 0) { "AI budget ledger value must be non-negative: $name" }
        return value
    }

    private companion object {
        const val MAX_LEDGER_BYTES = 4_096L
        val FIELDS = setOf(
            "date",
            "daily_spent_nano_dollars",
            "total_spent_nano_dollars",
            "reserved_nano_dollars",
        )
    }
}
