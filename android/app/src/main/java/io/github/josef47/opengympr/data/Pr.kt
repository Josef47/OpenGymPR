package io.github.josef47.opengympr.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One logged set. Weight is always stored in kg; lb is display only. */
data class LiftSet(val id: Long, val exerciseId: Long, val weightKg: Double, val reps: Int, val time: Long)

data class Exercise(val id: Long, val name: String, val custom: Boolean)

/** e1RM is only meaningful for lower rep ranges. */
const val E1RM_MAX_REPS = 12

/** Epley estimated one-rep max. */
fun e1rm(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

sealed class Pr(val label: String) {
    data object Weight : Pr("Weight PR")
    data object E1rm : Pr("e1RM PR")
    data class Reps(val atKg: Double) : Pr("Rep PR")
}

/**
 * Which record does [set] break compared with everything logged before it ([prior])?
 * At most one, in order of importance: heaviest weight, best e1RM, most reps at same-or-heavier weight.
 * The very first set of an exercise is not a PR (nothing to beat).
 */
fun prOf(set: LiftSet, prior: List<LiftSet>): Pr? {
    if (prior.isEmpty()) return null
    if (set.weightKg > prior.maxOf { it.weightKg }) return Pr.Weight
    if (set.reps <= E1RM_MAX_REPS) {
        val best = prior.filter { it.reps <= E1RM_MAX_REPS }.maxOfOrNull { e1rm(it.weightKg, it.reps) } ?: 0.0
        if (e1rm(set.weightKg, set.reps) > best + 1e-9) return Pr.E1rm
    }
    val sameOrHeavier = prior.filter { it.weightKg >= set.weightKg - 1e-9 }
    if (sameOrHeavier.isNotEmpty() && set.reps > sameOrHeavier.maxOf { it.reps }) return Pr.Reps(set.weightKg)
    return null
}

/** PR flag per set, computed chronologically. Not stored, so deleting a set re-evaluates everything. */
fun flagPrs(setsOldestFirst: List<LiftSet>): Map<Long, Pr> {
    val out = HashMap<Long, Pr>()
    setsOldestFirst.forEachIndexed { i, s -> prOf(s, setsOldestFirst.subList(0, i))?.let { out[s.id] = it } }
    return out
}

fun bestE1rm(sets: List<LiftSet>): Double =
    sets.filter { it.reps <= E1RM_MAX_REPS }.maxOfOrNull { e1rm(it.weightKg, it.reps) } ?: 0.0

/** Heaviest set that reached at least N reps, for N = 1..12. */
fun repMaxes(sets: List<LiftSet>): Map<Int, LiftSet> {
    val out = sortedMapOf<Int, LiftSet>()
    for (s in sets) for (r in 1..minOf(s.reps, E1RM_MAX_REPS)) {
        val cur = out[r]
        if (cur == null || s.weightKg > cur.weightKg) out[r] = s
    }
    return out
}

fun localDay(time: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

data class DayPoint(val time: Long, val value: Double)

/** Best e1RM per training day, oldest first — the chart line. */
fun dailyBest(sets: List<LiftSet>, zone: ZoneId = ZoneId.systemDefault()): List<DayPoint> =
    sets.filter { it.reps <= E1RM_MAX_REPS }
        .groupBy { localDay(it.time, zone) }
        .map { (_, day) -> day.maxBy { e1rm(it.weightKg, it.reps) }.let { DayPoint(it.time, e1rm(it.weightKg, it.reps)) } }
        .sortedBy { it.time }
