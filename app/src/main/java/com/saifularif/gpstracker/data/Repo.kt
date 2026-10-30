package com.saifularif.gpstracker.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object Repo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    fun authState(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(name: String, username: String, email: String, password: String) {
        val u = username.trim().lowercase()
        // Akun dibuat lebih dulu: aturan Firestore mewajibkan login untuk membaca "users".
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = res.user!!
        try {
            val taken = db.collection("users").whereEqualTo("username", u).limit(1).get().await()
            require(taken.isEmpty) { "Username sudah dipakai" }
            db.collection("users").document(user.uid).set(
                mapOf(
                    "name" to name.trim(),
                    "username" to u,
                    "code" to newCode(),
                    "shareEnabled" to true,
                    "pausedUntil" to 0L,
                )
            ).await()
        } catch (e: Exception) {
            runCatching { user.delete().await() }
            throw e
        }
    }

    fun signOut() = auth.signOut()

    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun profile(uid: String): Flow<Profile?> = callbackFlow {
        val reg = db.collection("users").document(uid).addSnapshotListener { s, _ ->
            trySend(s?.toProfile())
        }
        awaitClose { reg.remove() }
    }

    private fun DocumentSnapshot.toProfile(): Profile? {
        if (!exists()) return null
        return Profile(
            uid = id,
            name = getString("name") ?: "",
            username = getString("username") ?: "",
            code = getString("code") ?: "",
            shareEnabled = getBoolean("shareEnabled") ?: true,
            pausedUntil = getLong("pausedUntil") ?: 0L,
        )
    }

    suspend fun getProfile(uid: String): Profile? =
        db.collection("users").document(uid).get().await().toProfile()

    suspend fun setSharing(uid: String, enabled: Boolean, pausedUntil: Long = 0L) {
        db.collection("users").document(uid)
            .update(mapOf("shareEnabled" to enabled, "pausedUntil" to pausedUntil)).await()
        if (!enabled || pausedUntil > System.currentTimeMillis()) clearLocation(uid)
    }

    suspend fun clearLocation(uid: String) {
        db.collection("locations").document(uid).delete().await()
    }

    // ---- Teman ----

    fun friends(uid: String): Flow<List<Friend>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("friends")
            .addSnapshotListener { s, _ ->
                val list = s?.documents?.map {
                    Friend(
                        uid = it.id,
                        name = it.getString("name") ?: "Teman",
                        status = it.getString("status") ?: "pending_out",
                        favorite = it.getBoolean("favorite") ?: false,
                        group = it.getString("group") ?: "",
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun location(friendUid: String): Flow<Map<String, Any?>?> = callbackFlow {
        val reg = db.collection("locations").document(friendUid).addSnapshotListener { s, _ ->
            trySend(if (s != null && s.exists()) s.data else null)
        }
        awaitClose { reg.remove() }
    }

    /** Kirim undangan memakai kode 6 karakter atau username. */
    suspend fun invite(me: Profile, codeOrUsername: String): String {
        val q = codeOrUsername.trim()
        require(q.isNotEmpty()) { "Isi kode atau username" }
        var res = db.collection("users").whereEqualTo("code", q.uppercase()).limit(1).get().await()
        if (res.isEmpty) res = db.collection("users").whereEqualTo("username", q.lowercase().removePrefix("@")).limit(1).get().await()
        val target = res.documents.firstOrNull() ?: error("Pengguna tidak ditemukan")
        require(target.id != me.uid) { "Itu kode Anda sendiri" }
        val name = target.getString("name") ?: "Teman"
        db.collection("users").document(me.uid).collection("friends").document(target.id)
            .set(mapOf("name" to name, "status" to "pending_out", "since" to FieldValue.serverTimestamp())).await()
        db.collection("users").document(target.id).collection("friends").document(me.uid)
            .set(mapOf("name" to me.name, "status" to "pending_in", "since" to FieldValue.serverTimestamp())).await()
        return name
    }

    suspend fun accept(me: Profile, friend: Friend) {
        // Urutan penting: aturan Firestore memeriksa entri milik kita masih "pending_in".
        db.collection("users").document(friend.uid).collection("friends").document(me.uid)
            .update("status", "accepted").await()
        db.collection("users").document(me.uid).collection("friends").document(friend.uid)
            .update("status", "accepted").await()
    }

    suspend fun remove(meUid: String, friendUid: String) {
        db.collection("users").document(meUid).collection("friends").document(friendUid).delete().await()
        runCatching {
            db.collection("users").document(friendUid).collection("friends").document(meUid).delete().await()
        }
    }

    // ---- Lokasi ----

    suspend fun publishLocation(
        uid: String,
        lat: Double,
        lng: Double,
        speedMps: Double,
        bearing: Double,
        battery: Int,
        stillSince: Long,
    ) {
        db.collection("locations").document(uid).set(
            mapOf(
                "lat" to lat,
                "lng" to lng,
                "speed" to speedMps,
                "bearing" to bearing,
                "battery" to battery,
                "updatedAt" to System.currentTimeMillis(),
                "stillSince" to stillSince,
            )
        ).await()
    }

    // ---- Favorit & grup (label pribadi di daftar teman sendiri) ----

    suspend fun setFavorite(meUid: String, friendUid: String, favorite: Boolean) {
        db.collection("users").document(meUid).collection("friends").document(friendUid)
            .update("favorite", favorite).await()
    }

    suspend fun setGroup(meUid: String, friendUid: String, group: String) {
        db.collection("users").document(meUid).collection("friends").document(friendUid)
            .update("group", group.trim()).await()
    }

    // ---- Tempat ----

    fun places(uid: String): Flow<List<Place>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("places")
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map {
                    Place(
                        id = it.id,
                        name = it.getString("name") ?: "",
                        lat = it.getDouble("lat") ?: 0.0,
                        lng = it.getDouble("lng") ?: 0.0,
                        radius = it.getDouble("radius") ?: 100.0,
                    )
                }?.sortedBy { it.name.lowercase() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun addPlace(uid: String, name: String, lat: Double, lng: Double, radius: Double) {
        require(name.isNotBlank()) { "Nama tempat wajib diisi" }
        db.collection("users").document(uid).collection("places").add(
            mapOf("name" to name.trim(), "lat" to lat, "lng" to lng, "radius" to radius)
        ).await()
    }

    suspend fun deletePlace(uid: String, placeId: String) {
        db.collection("users").document(uid).collection("places").document(placeId).delete().await()
    }

    // ---- Riwayat perjalanan (satu dokumen per perjalanan) ----

    suspend fun publishTrip(uid: String, trip: Trip) {
        db.collection("users").document(uid).collection("trips").document(trip.id).set(
            mapOf(
                "startAt" to trip.startAt,
                "endAt" to trip.endAt,
                "distanceM" to trip.distanceM,
                "pts" to trip.points.flatMap { listOf(it.lat, it.lng) },
            )
        ).await()
    }

    fun trips(uid: String): Flow<List<Trip>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("trips")
            .orderBy("startAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map { d ->
                    val flat = (d.get("pts") as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
                    Trip(
                        id = d.id,
                        startAt = d.getLong("startAt") ?: 0L,
                        endAt = d.getLong("endAt") ?: 0L,
                        distanceM = d.getDouble("distanceM") ?: 0.0,
                        points = flat.chunked(2).filter { it.size == 2 }.map { GeoPt(it[0], it[1]) },
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ---- Peristiwa (tiba/pergi/baterai rendah) ----

    suspend fun publishEvent(
        uid: String,
        fromName: String,
        type: String,
        placeName: String,
        lat: Double? = null,
        lng: Double? = null,
    ) {
        val data = hashMapOf<String, Any>(
            "type" to type,
            "placeName" to placeName,
            "fromName" to fromName,
            "ts" to System.currentTimeMillis(),
        )
        if (lat != null && lng != null) { data["lat"] = lat; data["lng"] = lng }
        db.collection("users").document(uid).collection("events").add(data).await()
    }

    fun events(friendUid: String): Flow<List<FriendEvent>> = callbackFlow {
        val reg = db.collection("users").document(friendUid).collection("events")
            .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map { d ->
                    FriendEvent(
                        id = d.id,
                        fromUid = friendUid,
                        fromName = d.getString("fromName") ?: "Teman",
                        type = d.getString("type") ?: "",
                        placeName = d.getString("placeName") ?: "",
                        ts = d.getLong("ts") ?: 0L,
                        lat = d.getDouble("lat"),
                        lng = d.getDouble("lng"),
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /** Pembersihan oleh klien (tanpa Cloud Functions): perjalanan > 7 hari, peristiwa > 2 hari. */
    suspend fun pruneOld(uid: String) {
        val now = System.currentTimeMillis()
        val trips = db.collection("users").document(uid).collection("trips")
            .whereLessThan("startAt", now - 7 * 24 * 3_600_000L).get().await()
        trips.documents.forEach { it.reference.delete() }
        val events = db.collection("users").document(uid).collection("events")
            .whereLessThan("ts", now - 2 * 24 * 3_600_000L).get().await()
        events.documents.forEach { it.reference.delete() }
    }

    // ---- Chat, pesan suara, ping ----

    fun messages(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map { d ->
                    Message(
                        id = d.id,
                        from = d.getString("from") ?: "",
                        type = d.getString("type") ?: "text",
                        text = d.getString("text") ?: "",
                        audio = d.getString("audio"),
                        durMs = d.getLong("durMs") ?: 0L,
                        ts = d.getLong("ts") ?: 0L,
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(
        chatId: String,
        from: String,
        type: String,
        text: String = "",
        audio: String? = null,
        durMs: Long = 0L,
    ) {
        val data = hashMapOf<String, Any>(
            "from" to from,
            "type" to type,
            "text" to text,
            "durMs" to durMs,
            "ts" to System.currentTimeMillis(),
        )
        if (audio != null) data["audio"] = audio
        db.collection("chats").document(chatId).collection("messages").add(data).await()
    }

    /** Hapus pesan lama (> 7 hari) milik sendiri; pesan teman dilewati karena aturan menolaknya. */
    suspend fun pruneChat(chatId: String, meUid: String) {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 3_600_000L
        val old = db.collection("chats").document(chatId).collection("messages")
            .whereLessThan("ts", cutoff).get().await()
        old.documents.filter { it.getString("from") == meUid }.forEach { it.reference.delete() }
    }

    // ---- Widget: status teman favorit (sekali ambil) ----

    suspend fun favoriteStatuses(limit: Int = 3): List<Pair<String, String>> {
        val me = uid ?: return emptyList()
        val friends = db.collection("users").document(me).collection("friends")
            .whereEqualTo("status", "accepted").whereEqualTo("favorite", true)
            .limit(limit.toLong()).get().await()
        return friends.documents.map { f ->
            val name = f.getString("name") ?: "Teman"
            val loc = runCatching { db.collection("locations").document(f.id).get().await() }.getOrNull()
            val friend = Friend(
                uid = f.id, name = name, status = "accepted",
                lat = loc?.getDouble("lat"), lng = loc?.getDouble("lng"),
                speedMps = loc?.getDouble("speed") ?: 0.0,
                updatedAt = loc?.getLong("updatedAt") ?: 0L,
                stillSince = loc?.getLong("stillSince") ?: 0L,
            )
            name to friend.statusText()
        }
    }

    suspend fun sharingStatus(): String? {
        val me = uid ?: return null
        val p = getProfile(me) ?: return null
        return when {
            !p.shareEnabled -> "Berbagi lokasi: mati"
            p.pausedUntil > System.currentTimeMillis() -> "Berbagi lokasi: dijeda"
            else -> "Berbagi lokasi: aktif"
        }
    }
}
