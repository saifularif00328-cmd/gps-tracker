package com.saifularif.gpstracker.data

enum class FriendState { MOVING, STILL, OFFLINE }

data class Profile(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val code: String = "",
    val shareEnabled: Boolean = true,
    val pausedUntil: Long = 0L,
) {
    fun isSharing(now: Long = System.currentTimeMillis()) = shareEnabled && now >= pausedUntil
}

data class Friend(
    val uid: String,
    val name: String,
    val status: String, // accepted | pending_in | pending_out
    val lat: Double? = null,
    val lng: Double? = null,
    val speedMps: Double = 0.0,
    val bearing: Double = 0.0,
    val battery: Int = -1,
    val updatedAt: Long = 0L,
    val stillSince: Long = 0L,
    val favorite: Boolean = false,
    val group: String = "",
) {
    val hasLocation get() = lat != null && lng != null

    fun state(now: Long = System.currentTimeMillis()): FriendState = when {
        !hasLocation || now - updatedAt > OFFLINE_AFTER_MS -> FriendState.OFFLINE
        speedMps >= MOVING_SPEED_MPS -> FriendState.MOVING
        else -> FriendState.STILL
    }

    val speedKmh get() = speedMps * 3.6

    companion object {
        const val OFFLINE_AFTER_MS = 5 * 60_000L
        const val MOVING_SPEED_MPS = 1.0
    }
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "baru saja"
        m < 60 -> "$m menit"
        m < 24 * 60 -> "${m / 60} jam ${m % 60} menit"
        else -> "${m / (24 * 60)} hari"
    }
}

fun Friend.statusText(now: Long = System.currentTimeMillis()): String = when (state(now)) {
    FriendState.MOVING -> "Bergerak • %.0f km/j".format(speedKmh)
    FriendState.STILL -> "Diam • ${formatDuration(now - (if (stillSince > 0) stillSince else updatedAt))}"
    FriendState.OFFLINE ->
        if (updatedAt > 0) "Offline • terakhir ${formatDuration(now - updatedAt)} lalu" else "Belum membagikan lokasi"
}

data class Place(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radius: Double = 100.0,
)

data class Trip(
    val id: String,
    val startAt: Long,
    val endAt: Long,
    val distanceM: Double,
    val points: List<GeoPt>,
)

data class FriendEvent(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val type: String, // arrive | leave | battery_low
    val placeName: String,
    val ts: Long,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class Message(
    val id: String,
    val from: String,
    val type: String, // text | voice | ping
    val text: String,
    val audio: String?, // base64 (hanya untuk voice)
    val durMs: Long,
    val ts: Long,
)

/** ID chat simetris: dua uid diurutkan lalu digabung dengan "_". */
fun chatIdOf(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

/** Teks bantuan untuk teman yang tampil offline. */
fun Friend.offlineHint(now: Long = System.currentTimeMillis()): String? =
    if (state(now) != FriendState.OFFLINE) null
    else if (!hasLocation) "Belum pernah membagikan lokasi. Mintalah ia menyalakan berbagi lokasi."
    else "Mungkin berbagi dimatikan, HP tanpa internet, atau aplikasi ditutup sistem. Ketuk \"Minta aktifkan\" agar ia bisa menyalakannya."

fun Profile.sharingText(now: Long = System.currentTimeMillis()): String = when {
    !shareEnabled -> "Mati. Tidak ada yang bisa melihat Anda."
    pausedUntil > now -> "Dijeda, lanjut dalam ${formatDuration(pausedUntil - now)}."
    else -> "Aktif untuk teman yang disetujui."
}
