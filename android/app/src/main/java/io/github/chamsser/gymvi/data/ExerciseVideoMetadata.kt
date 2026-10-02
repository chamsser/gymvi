package io.github.chamsser.gymvi.data

import org.json.JSONObject

/** Missing, hidden, malformed or negative statistics stay absent, never zero. */
internal fun JSONObject.exerciseStatistic(name: String): Long? = when (val value = opt(name)) {
    is Int -> value.toLong().takeIf { it >= 0 }
    is Long -> value.takeIf { it >= 0 }
    else -> null
}

internal fun JSONObject.exerciseChannelName(): String? = (opt("channel_name") as? String)
    ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 && it.none(Char::isISOControl) }

internal fun JSONObject.exerciseDuration(): Int? = exerciseStatistic("duration_seconds")
    ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
