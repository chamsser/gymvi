package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.usage.JoinedUsageOptionRecord
import io.github.chamsser.gymvi.usage.OperatorTimeResponse
import io.github.chamsser.gymvi.usage.UsageOptionRecord
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ProgramFactDeriver {
    private val categoryTerms = linkedMapOf(
        Category.SWIMMING to listOf("수영", "아쿠아", "수중", "자유형", "배영", "평영", "접영"),
        Category.FITNESS to listOf("헬스", "피트니스", "웨이트", "체력단련", "스피닝", "크로스핏", "GX"),
        Category.YOGA_PILATES to listOf("요가", "필라테스"),
        Category.DANCE to listOf("댄스", "줌바", "발레", "에어로빅", "라인댄스"),
        Category.TENNIS to listOf("테니스"),
        Category.BADMINTON to listOf("배드민턴"),
        Category.TABLE_TENNIS to listOf("탁구"),
        Category.GOLF to listOf("골프"),
        Category.SQUASH to listOf("스쿼시"),
        Category.SKATING to listOf("빙상", "스케이트", "쇼트트랙", "피겨"),
        Category.TEAM_BALL to listOf("농구", "배구", "축구", "풋살", "족구", "핸드볼"),
        Category.MARTIAL_ARTS to listOf("태권도", "유도", "검도", "복싱", "주짓수", "합기도"),
        Category.CLIMBING to listOf("클라이밍"),
    )
    private val levelTerms = linkedMapOf(
        Level.BEGINNER to listOf("초급", "기초", "입문", "초보", "왕초보"),
        Level.INTERMEDIATE to listOf("중급"),
        Level.ADVANCED to listOf("상급", "고급", "연수"),
    )
    private val targetTerms = linkedMapOf(
        TargetGroup.CHILD to listOf("어린이", "초등", "유아", "유치", "키즈", "아동"),
        TargetGroup.YOUTH to listOf("청소년", "중학생", "고등학생", "중학", "고등", "중고"),
        TargetGroup.ADULT to listOf("성인", "일반"),
        TargetGroup.SENIOR to listOf("경로", "노인", "실버", "어르신", "시니어"),
    )
    private val allTerms = listOf("누구나", "전체", "제한없음", "남녀노소", "가족")
    private val timePattern = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*[~-]\\s*(\\d{1,2}):(\\d{2})\\s*$")

    fun derive(
        record: JoinedUsageOptionRecord,
        date: LocalDate,
        origin: Origin?,
        operatorTime: OperatorTimeResponse? = null,
    ): ProgramFacts = ProgramFacts(
        category(record.option),
        level(record.option.programName),
        targetGroups(record.option.targetName),
        startTime(record.option.sourceTimeValue, operatorTime),
        period(record.option.beginDate, record.option.endDate, date),
        distance(origin, record.latitude, record.longitude),
    )

    fun category(option: UsageOptionRecord): DerivedFact<Category> {
        var ambiguous = false
        for ((field, raw) in listOf("program_type_name" to option.programTypeName, "program_name" to option.programName)) {
            val text = raw.orEmpty()
            val matches = categoryTerms.mapValues { (_, terms) ->
                terms.filter { text.contains(it, ignoreCase = true) }
            }.filterValues(List<String>::isNotEmpty)
            if (matches.size == 1) {
                val (category, terms) = matches.entries.single()
                return known(category, field, raw, terms)
            }
            ambiguous = ambiguous || matches.size > 1
        }
        return unknown(if (ambiguous) "CATEGORY_AMBIGUOUS" else "CATEGORY_TERM_NOT_FOUND")
    }

    fun level(name: String): DerivedFact<Level> {
        val matches = linkedSetOf<Level>()
        val terms = mutableListOf<String>()
        var remainder = name
        for ((composite, groups) in listOf(
            "초중급" to listOf(Level.BEGINNER, Level.INTERMEDIATE),
            "중상급" to listOf(Level.INTERMEDIATE, Level.ADVANCED),
        )) {
            if (remainder.contains(composite)) {
                matches.addAll(groups)
                terms += composite
                remainder = remainder.replace(composite, "")
            }
        }
        for ((group, patterns) in levelTerms) {
            patterns.filter { remainder.contains(it) }.forEach {
                matches += group
                terms += it
            }
        }
        return if (matches.isEmpty()) unknown("LEVEL_TERM_NOT_FOUND")
        else knownValues(Level.entries.filter(matches::contains), "program_name", name, terms.distinct())
    }

    fun targetGroups(raw: String?): DerivedFact<TargetGroup> {
        if (raw.isNullOrBlank()) return unknown("TARGET_BLANK")
        val all = allTerms.filter(raw::contains)
        if (all.isNotEmpty()) return knownValues(TargetGroup.entries, "target_name", raw, all)
        data class Hit(val group: TargetGroup, val term: String, val start: Int, val end: Int)
        val hits = targetTerms.flatMap { (group, terms) ->
            terms.flatMap { term -> Regex(Regex.escape(term)).findAll(raw).map { Hit(group, term, it.range.first, it.range.last + 1) }.toList() }
        }.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.term.length })
        val nonOverlapping = mutableListOf<Hit>()
        hits.forEach { hit -> if (nonOverlapping.none { hit.start < it.end && hit.end > it.start }) nonOverlapping += hit }
        if (nonOverlapping.isEmpty()) return unknown("TARGET_UNPARSED")
        val groups = linkedSetOf<TargetGroup>()
        nonOverlapping.forEachIndexed { index, hit ->
            groups += hit.group
            if (Regex("^\\s*이상").containsMatchIn(raw.substring(hit.end))) {
                groups += TargetGroup.entries.drop(hit.group.ordinal)
            }
            val next = nonOverlapping.getOrNull(index + 1)
            if (next != null && raw.substring(hit.end, next.start).trim() == "~") {
                val range = listOf(hit.group.ordinal, next.group.ordinal)
                groups += TargetGroup.entries.subList(range.min(), range.max() + 1)
            }
        }
        return knownValues(TargetGroup.entries.filter(groups::contains), "target_name", raw, nonOverlapping.map(Hit::term).distinct())
    }

    fun startTime(raw: String?, operatorTime: OperatorTimeResponse? = null): DerivedFact<LocalTime> {
        if (raw.isNullOrBlank()) return operatorTime?.let(::operatorStartTime) ?: unknown("SOURCE_TIME_BLANK")
        val match = timePattern.matchEntire(raw)
            ?: return operatorTime?.let(::operatorStartTime)
                ?: unknown("SOURCE_TIME_UNPARSEABLE", "source_time_value", raw)
        val (sh, sm, eh, em) = match.destructured.toList().map(String::toInt)
        if (sh !in 0..23 || eh !in 0..24 || sm !in 0..59 || em !in 0..59 ||
            (eh == 24 && em != 0) || sh * 60 + sm >= eh * 60 + em
        ) return operatorTime?.let(::operatorStartTime)
            ?: unknown("SOURCE_TIME_UNPARSEABLE", "source_time_value", raw)
        return known(LocalTime.of(sh, sm), "source_time_value", raw, listOf(match.value.trim()))
    }

    private fun operatorStartTime(operatorTime: OperatorTimeResponse): DerivedFact<LocalTime> {
        val value = "${operatorTime.startTime}~${operatorTime.endTime}"
        return known(LocalTime.parse(operatorTime.startTime), "operator_time", value, listOf(value))
    }

    fun period(begin: LocalDate?, end: LocalDate?, date: LocalDate): DerivedFact<Period> {
        val value = when {
            end != null && end < date -> Period.ENDED
            begin != null && begin > date -> Period.UPCOMING
            begin != null && end != null && begin <= date && end >= date -> Period.CURRENT
            else -> null
        }
        return if (value == null) unknown("PERIOD_UNKNOWN") else known(
            value,
            "begin_date,end_date",
            "${begin ?: ""},${end ?: ""}",
            listOfNotNull(begin?.toString(), end?.toString()),
        )
    }

    fun distance(origin: Origin?, latitude: Double, longitude: Double): DerivedFact<Int> {
        if (origin == null) return unknown("ORIGIN_NOT_PROVIDED")
        val latDelta = Math.toRadians(latitude - origin.latitude)
        val lonDelta = Math.toRadians(longitude - origin.longitude)
        val a = sin(latDelta / 2).let { it * it } +
            cos(Math.toRadians(origin.latitude)) * cos(Math.toRadians(latitude)) *
            sin(lonDelta / 2).let { it * it }
        val meters = (2 * 6_371_000 * asin(sqrt(a.coerceIn(0.0, 1.0)))).roundToInt()
        return known(meters, "origin,facility.location", "provided", emptyList())
    }

    private fun <T> known(value: T, field: String, source: String?, terms: List<String>) =
        DerivedFact(state = "KNOWN", value = value, basis = FactBasis(field, source, terms))

    private fun <T> knownValues(values: List<T>, field: String, source: String?, terms: List<String>) =
        DerivedFact<T>(state = "KNOWN", values = values, basis = FactBasis(field, source, terms))

    private fun <T> unknown(reason: String, field: String? = null, source: String? = null) =
        DerivedFact<T>(state = "UNKNOWN", reasonCode = reason, basis = field?.let { FactBasis(it, source, emptyList()) })
}
