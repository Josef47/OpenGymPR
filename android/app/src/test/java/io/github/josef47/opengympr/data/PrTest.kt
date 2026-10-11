package io.github.josef47.opengympr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class PrTest {
    private var nextId = 0L
    private fun set(w: Double, r: Int, t: Long = nextId * 1000) = LiftSet(++nextId, 1, w, r, t)

    @Test fun firstSetIsNotAPr() = assertNull(prOf(set(100.0, 5), emptyList()))

    @Test fun heavierIsWeightPr() = assertEquals(Pr.Weight, prOf(set(102.5, 1), listOf(set(100.0, 5))))

    @Test fun sameWeightMoreRepsIsE1rmPr() =
        // 100x6 beats 100x5 on e1RM, which outranks the rep PR
        assertEquals(Pr.E1rm, prOf(set(100.0, 6), listOf(set(100.0, 5))))

    @Test fun repPrWhenE1rmNotBeaten() {
        // e1RM best comes from 120x3 (132); 100x8 (126.7) is no e1RM PR, but 8 reps at >=100 is new
        val prior = listOf(set(120.0, 3), set(100.0, 6))
        assertEquals(Pr.Reps(100.0), prOf(set(100.0, 8), prior))
    }

    @Test fun highRepSetsIgnoredForE1rm() {
        val prior = listOf(set(60.0, 5))
        // Sets above 12 reps never count for e1RM; 40x20 still beats every same-or-heavier set on reps -> rep PR
        assertEquals(Pr.Reps(40.0), prOf(set(40.0, 20), prior))
    }

    @Test fun equalSetIsNoPr() = assertNull(prOf(set(100.0, 5), listOf(set(100.0, 5))))

    @Test fun repMaxesTakesHeaviestPerRepCount() {
        val rm = repMaxes(listOf(set(100.0, 5), set(110.0, 2), set(90.0, 8)))
        assertEquals(110.0, rm[1]!!.weightKg, 0.0)
        assertEquals(110.0, rm[2]!!.weightKg, 0.0)
        assertEquals(100.0, rm[5]!!.weightKg, 0.0)
        assertEquals(90.0, rm[8]!!.weightKg, 0.0)
        assertNull(rm[9])
    }

    @Test fun dailyBestOnePointPerDay() {
        val day = 86_400_000L
        val pts = dailyBest(listOf(set(100.0, 5, 0), set(105.0, 3, 1000), set(100.0, 5, day)), ZoneOffset.UTC)
        assertEquals(2, pts.size)
        assertEquals(e1rm(105.0, 3).coerceAtLeast(e1rm(100.0, 5)), pts[0].value, 1e-9)
    }

    @Test fun flagsRecomputeChronologically() {
        val a = set(100.0, 5); val b = set(105.0, 5); val c = set(100.0, 5)
        val flags = flagPrs(listOf(a, b, c))
        assertEquals(mapOf(b.id to Pr.Weight), flags)
    }
}
