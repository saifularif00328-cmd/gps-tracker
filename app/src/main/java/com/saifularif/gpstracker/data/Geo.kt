package com.saifularif.gpstracker.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Logika murni (tanpa Android) agar mudah diuji. */

data class GeoPt(val lat: Double, val lng: Double)

fun distanceM(a: GeoPt, b: GeoPt) = distanceM(a.lat, a.lng, b.lat, b.lng)

fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val p1 = lat1 * PI / 180
    val p2 = lat2 * PI / 180
    val dp = (lat2 - lat1) * PI / 180
    val dl = (lng2 - lng1) * PI / 180
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

fun pathLengthM(points: List<GeoPt>): Double {
    var sum = 0.0
    for (i in 1 until points.size) sum += distanceM(points[i - 1], points[i])
    return sum
}

/** Jarak titik p ke garis a-b (meter), pendekatan bidang datar lokal. */
private fun crossTrackM(p: GeoPt, a: GeoPt, b: GeoPt): Double {
    val k = 111_320.0
    val cosLat = cos(a.lat * PI / 180)
    fun x(g: GeoPt) = (g.lng - a.lng) * k * cosLat
    fun y(g: GeoPt) = (g.lat - a.lat) * k
    val bx = x(b); val by = y(b); val px = x(p); val py = y(p)
    val len2 = bx * bx + by * by
    if (len2 == 0.0) return sqrt(px * px + py * py)
    val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return sqrt(dx * dx + dy * dy)
}

/** Douglas-Peucker; toleransi dinaikkan sampai jumlah titik <= [maxPoints]. */
fun simplify(points: List<GeoPt>, maxPoints: Int, startToleranceM: Double = 5.0): List<GeoPt> {
    if (points.size <= 2 || points.size <= maxPoints) return points
    var tol = startToleranceM
    var out = douglasPeucker(points, tol)
    var guard = 0
    while (out.size > maxPoints && guard++ < 20) {
        tol *= 2
        out = douglasPeucker(points, tol)
    }
    if (out.size > maxPoints) {
        val step = out.size.toDouble() / maxPoints
        out = (0 until maxPoints).map { out[(it * step).toInt()] } + out.last()
    }
    return out
}

private fun douglasPeucker(points: List<GeoPt>, tolM: Double): List<GeoPt> {
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)
    while (stack.isNotEmpty()) {
        val (s, e) = stack.removeLast()
        var maxD = 0.0
        var idx = -1
        for (i in s + 1 until e) {
            val d = crossTrackM(points[i], points[s], points[e])
            if (d > maxD) { maxD = d; idx = i }
        }
        if (idx != -1 && maxD > tolM) {
            keep[idx] = true
            stack.addLast(s to idx)
            stack.addLast(idx to e)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/**
 * Mendeteksi perjalanan: mulai saat bergerak, selesai setelah diam >= [stillMs].
 * Perjalanan lebih pendek dari [minDistanceM] dibuang (mis. goyangan GPS).
 */
class TripDetector(
    private val stillMs: Long = 5 * 60_000L,
    private val minDistanceM: Double = 100.0,
    private val maxPoints: Int = 300,
) {
    private val points = ArrayList<GeoPt>()
    private var startAt = 0L
    private var lastMovingAt = 0L
    private var stillStart = 0L
    private var idCounter = 0

    val active get() = points.isNotEmpty()

    fun onSample(t: Long, lat: Double, lng: Double, moving: Boolean): Trip? {
        if (moving) {
            if (!active) startAt = t
            points.add(GeoPt(lat, lng))
            lastMovingAt = t
            stillStart = 0L
            return null
        }
        if (!active) return null
        if (stillStart == 0L) stillStart = t
        return if (t - stillStart >= stillMs) finish() else null
    }

    /** Dipanggil saat layanan berhenti agar perjalanan yang berjalan tidak hilang. */
    fun flush(): Trip? = if (active) finish() else null

    private fun finish(): Trip? {
        val pts = ArrayList(points)
        val start = startAt
        val end = lastMovingAt
        points.clear()
        startAt = 0L; lastMovingAt = 0L; stillStart = 0L
        val dist = pathLengthM(pts)
        if (pts.size < 2 || dist < minDistanceM) return null
        return Trip("trip-$start-${idCounter++}", start, end, dist, simplify(pts, maxPoints))
    }
}

/** Mendeteksi masuk/keluar tempat dengan histeresis agar tidak berkedip di tepi radius. */
class GeofenceTracker(private val leaveFactor: Double = 1.2) {
    private val inside = HashMap<String, Boolean>()

    data class Transition(val place: Place, val entered: Boolean)

    /** Observasi pertama sebuah tempat hanya mencatat status awal (tidak memicu peristiwa). */
    fun update(places: List<Place>, lat: Double, lng: Double): List<Transition> {
        val out = ArrayList<Transition>()
        inside.keys.retainAll(places.map { it.id }.toSet())
        for (p in places) {
            val d = distanceM(lat, lng, p.lat, p.lng)
            val was = inside[p.id]
            val now = when {
                d <= p.radius -> true
                d > p.radius * leaveFactor -> false
                else -> was ?: false
            }
            inside[p.id] = now
            if (was != null && was != now) out.add(Transition(p, now))
        }
        return out
    }
}
