package com.saifularif.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    // ~11.1 m per 0.0001 derajat lintang
    private fun north(i: Int) = GeoPt(-6.2 + i * 0.0001, 106.8)

    @Test fun distance_isRoughlyCorrect() {
        val d = distanceM(-6.2, 106.8, -6.2 + 0.01, 106.8)
        assertTrue(d in 1100.0..1120.0)
    }

    @Test fun simplify_keepsEndpointsAndLimitsSize() {
        val line = (0..1000).map { north(it) }
        val out = simplify(line, maxPoints = 50)
        assertEquals(line.first(), out.first())
        assertEquals(line.last(), out.last())
        assertTrue(out.size <= 51)
    }

    @Test fun simplify_straightLineCollapses() {
        val line = (0..100).map { north(it) }
        assertEquals(2, simplify(line, maxPoints = 10).size)
    }

    @Test fun simplify_keepsCorner() {
        val l = (0..50).map { north(it) } + (1..50).map { GeoPt(north(50).lat, 106.8 + it * 0.0001) }
        val out = simplify(l, maxPoints = 10)
        assertTrue(out.contains(north(50)))
    }

    @Test fun trip_endsAfterStillPeriod() {
        val d = TripDetector(stillMs = 300_000, minDistanceM = 100.0)
        var t = 0L
        for (i in 0..30) { assertNull(d.onSample(t, north(i).lat, north(i).lng, true)); t += 5_000 }
        assertNull(d.onSample(t, north(30).lat, north(30).lng, false)) // mulai diam
        assertNull(d.onSample(t + 299_000, north(30).lat, north(30).lng, false))
        val trip = d.onSample(t + 300_000, north(30).lat, north(30).lng, false)
        assertNotNull(trip)
        assertTrue(trip!!.distanceM > 300)
        assertTrue(!d.active)
    }

    @Test fun trip_movingAgainCancelsStillTimer() {
        val d = TripDetector(stillMs = 300_000, minDistanceM = 100.0)
        for (i in 0..20) d.onSample(i * 5_000L, north(i).lat, north(i).lng, true)
        d.onSample(200_000, north(20).lat, north(20).lng, false)
        d.onSample(250_000, north(21).lat, north(21).lng, true) // bergerak lagi
        assertNull(d.onSample(400_000, north(21).lat, north(21).lng, false)) // timer mulai ulang
        assertTrue(d.active)
    }

    @Test fun trip_tooShortIsDropped() {
        val d = TripDetector(stillMs = 60_000, minDistanceM = 100.0)
        d.onSample(0, north(0).lat, north(0).lng, true)
        d.onSample(5_000, north(1).lat, north(1).lng, true)
        d.onSample(10_000, north(1).lat, north(1).lng, false)
        assertNull(d.onSample(80_000, north(1).lat, north(1).lng, false))
        assertTrue(!d.active)
    }

    @Test fun flush_returnsRunningTrip() {
        val d = TripDetector()
        for (i in 0..30) d.onSample(i * 5_000L, north(i).lat, north(i).lng, true)
        assertNotNull(d.flush())
    }

    private val home = Place("home", "Rumah", -6.2, 106.8, 100.0)

    @Test fun geofence_firstObservationIsSilent() {
        val g = GeofenceTracker()
        assertTrue(g.update(listOf(home), -6.2, 106.8).isEmpty())
    }

    @Test fun geofence_arriveAndLeaveWithHysteresis() {
        val g = GeofenceTracker()
        g.update(listOf(home), -6.3, 106.8) // jauh
        val arrive = g.update(listOf(home), -6.2, 106.8)
        assertEquals(1, arrive.size); assertTrue(arrive[0].entered)
        // 110 m: di luar radius tetapi di dalam zona histeresis (120 m) -> tetap di dalam
        assertTrue(g.update(listOf(home), -6.2 + 0.00099, 106.8).isEmpty())
        val leave = g.update(listOf(home), -6.2 + 0.0015, 106.8) // ~167 m
        assertEquals(1, leave.size); assertTrue(!leave[0].entered)
    }
}
