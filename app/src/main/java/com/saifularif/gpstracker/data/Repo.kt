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
        val taken = db.collection("users").whereEqualTo("username", u).limit(1).get().await()
        require(taken.isEmpty) { "Username sudah dipakai" }
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val id = res.user!!.uid
        db.collection("users").document(id).set(
            mapOf(
                "name" to name.trim(),
                "username" to u,
                "code" to newCode(),
                "shareEnabled" to true,
                "pausedUntil" to 0L,
            )
        ).await()
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
}
