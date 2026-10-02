package io.github.chamsser.gymvi.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal fun exerciseVideoCount(value: Long?): String? {
    if (value == null || value < 0) return null
    val (divisor, suffix) = when {
        value >= 100_000_000L -> 100_000_000L to "억"
        value >= 10_000L -> 10_000L to "만"
        value >= 1_000L -> 1_000L to "천"
        else -> return value.toString()
    }
    return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 1, RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString() + suffix
}

internal fun exerciseVideoDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    return if (seconds >= 3_600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
    } else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
