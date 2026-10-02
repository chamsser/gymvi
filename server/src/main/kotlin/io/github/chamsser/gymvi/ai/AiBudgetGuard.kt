package io.github.chamsser.gymvi.ai

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

internal data class AiUsage(
    val inputTokens: Long,
    val outputTokens: Long,
)

internal enum class AiBudgetLevel {
    NORMAL,
    WARNING_70,
    RESTRICTED_90,
    BLOCKED_100,
}

internal data class AiBudgetSnapshot(
    val dailyUsedNanoDollars: Long,
    val totalUsedNanoDollars: Long,
    val level: AiBudgetLevel,
)

internal class AiBudgetGuard(
    private val dailyLimitNanoDollars: Long,
    private val totalLimitNanoDollars: Long,
    private val clock: Clock,
    private val inputNanoDollarsPerToken: Long = 100L,
    private val outputNanoDollarsPerToken: Long = 500L,
    private val ledger: AiBudgetLedger? = null,
) {
    private val zone = ZoneId.of("Asia/Seoul")
    private var date = LocalDate.now(clock.withZone(zone))
    private var dailySpent = 0L
    private var totalSpent = 0L
    private var reserved = 0L
    private var ledgerFailure: Exception? = null

    init {
        require(dailyLimitNanoDollars > 0)
        require(totalLimitNanoDollars >= dailyLimitNanoDollars)
        require(inputNanoDollarsPerToken > 0)
        require(outputNanoDollarsPerToken > 0)
        try {
            val loaded = ledger?.load()
            if (loaded != null) {
                require(!loaded.date.isAfter(date)) { "AI budget ledger date cannot be in the future." }
                totalSpent = Math.addExact(loaded.totalSpentNanoDollars, loaded.reservedNanoDollars)
                dailySpent = if (loaded.date == date) {
                    Math.addExact(loaded.dailySpentNanoDollars, loaded.reservedNanoDollars)
                } else {
                    0L
                }
                reserved = 0L
            }
            if (ledger != null) persist()
        } catch (exception: Exception) {
            markLedgerFailed(exception)
        }
    }

    @Synchronized
    fun reserve(maxInputTokens: Long, maxOutputTokens: Long): AiBudgetReservation? {
        ensureLedgerAvailable()
        resetDailyIfNeeded()
        val amount = tokenCostNanoDollars(maxInputTokens, maxOutputTokens)
        if (dailySpent + reserved + amount > dailyLimitNanoDollars ||
            totalSpent + reserved + amount > totalLimitNanoDollars
        ) return null
        reserved += amount
        try {
            persist()
        } catch (exception: Exception) {
            reserved -= amount
            throw markLedgerFailed(exception)
        }
        return AiBudgetReservation(this, amount)
    }

    @Synchronized
    fun snapshot(): AiBudgetSnapshot {
        ensureLedgerAvailable()
        resetDailyIfNeeded()
        val dailyRatio = dailySpent.toDouble() / dailyLimitNanoDollars
        val totalRatio = totalSpent.toDouble() / totalLimitNanoDollars
        val ratio = maxOf(dailyRatio, totalRatio)
        val level = when {
            ratio >= 1.0 -> AiBudgetLevel.BLOCKED_100
            ratio >= 0.9 -> AiBudgetLevel.RESTRICTED_90
            ratio >= 0.7 -> AiBudgetLevel.WARNING_70
            else -> AiBudgetLevel.NORMAL
        }
        return AiBudgetSnapshot(dailySpent, totalSpent, level)
    }

    @Synchronized
    private fun complete(reservedAmount: Long, usage: AiUsage?) {
        resetDailyIfNeeded()
        reserved = (reserved - reservedAmount).coerceAtLeast(0L)
        val actual = usage?.let { tokenCostNanoDollars(it.inputTokens, it.outputTokens) } ?: reservedAmount
        dailySpent += actual
        totalSpent += actual
        try {
            persist()
        } catch (exception: Exception) {
            throw markLedgerFailed(exception)
        }
    }

    private fun resetDailyIfNeeded() {
        ensureLedgerAvailable()
        val today = LocalDate.now(clock.withZone(zone))
        if (today != date) {
            date = today
            dailySpent = 0L
            try {
                persist()
            } catch (exception: Exception) {
                throw markLedgerFailed(exception)
            }
        }
    }

    private fun ensureLedgerAvailable() {
        ledgerFailure?.let { throw AiProviderException("AI_BUDGET_LEDGER_FAILED", it) }
    }

    private fun markLedgerFailed(exception: Exception): AiProviderException {
        ledgerFailure = exception
        dailySpent = dailyLimitNanoDollars
        totalSpent = totalLimitNanoDollars
        reserved = 0L
        runCatching { persist() }
        return AiProviderException("AI_BUDGET_LEDGER_FAILED", exception)
    }

    private fun persist() {
        ledger?.save(
            AiBudgetRecord(
                date = date,
                dailySpentNanoDollars = dailySpent,
                totalSpentNanoDollars = totalSpent,
                reservedNanoDollars = reserved,
            ),
        )
    }

    internal class AiBudgetReservation internal constructor(
        private val guard: AiBudgetGuard,
        private val amount: Long,
    ) {
        private var completed = false

        fun complete(usage: AiUsage?) {
            synchronized(this) {
                if (completed) return
                completed = true
            }
            guard.complete(amount, usage)
        }
    }

    companion object {
        fun fromDollarLimits(
            dailyLimit: String,
            totalLimit: String,
            clock: Clock,
            inputUsdPerMillionTokens: String = "0.10",
            outputUsdPerMillionTokens: String = "0.50",
            ledger: AiBudgetLedger? = null,
        ): AiBudgetGuard =
            AiBudgetGuard(
                dailyLimitNanoDollars = dollarsToNanoDollars(dailyLimit),
                totalLimitNanoDollars = dollarsToNanoDollars(totalLimit),
                clock = clock,
                inputNanoDollarsPerToken = priceToNanoDollarsPerToken(inputUsdPerMillionTokens),
                outputNanoDollarsPerToken = priceToNanoDollarsPerToken(outputUsdPerMillionTokens),
                ledger = ledger,
            )

        private fun priceToNanoDollarsPerToken(value: String): Long =
            BigDecimal(value)
                .multiply(BigDecimal(1_000L))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact()

        private fun dollarsToNanoDollars(value: String): Long =
            BigDecimal(value)
                .multiply(BigDecimal(1_000_000_000L))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
    }

    private fun tokenCostNanoDollars(inputTokens: Long, outputTokens: Long): Long {
        require(inputTokens >= 0 && outputTokens >= 0)
        return Math.addExact(
            Math.multiplyExact(inputTokens, inputNanoDollarsPerToken),
            Math.multiplyExact(outputTokens, outputNanoDollarsPerToken),
        )
    }
}
