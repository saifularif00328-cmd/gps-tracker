package com.saifularif.gpstracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Message
import com.saifularif.gpstracker.data.chatIdOf
import com.saifularif.gpstracker.data.Place
import com.saifularif.gpstracker.data.Trip
import com.saifularif.gpstracker.data.Profile
import com.saifularif.gpstracker.data.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = (app as GpsTrackerApp).prefs

    /** null = belum diketahui/belum login. */
    val uid: StateFlow<String?> = Repo.authState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Repo.uid)

    val profile: StateFlow<Profile?> = uid.flatMapLatest { id ->
        if (id == null) flowOf(null) else Repo.profile(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Teman + lokasi terbarunya (hanya yang accepted punya lokasi). */
    val friends: StateFlow<List<Friend>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else Repo.friends(id).flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList()) else {
                val flows: List<Flow<Friend>> = list.map { f ->
                    if (f.status != "accepted") flowOf(f) else Repo.location(f.uid).map { d ->
                        if (d == null) f else f.copy(
                            lat = (d["lat"] as? Number)?.toDouble(),
                            lng = (d["lng"] as? Number)?.toDouble(),
                            speedMps = (d["speed"] as? Number)?.toDouble() ?: 0.0,
                            bearing = (d["bearing"] as? Number)?.toDouble() ?: 0.0,
                            battery = (d["battery"] as? Number)?.toInt() ?: -1,
                            updatedAt = (d["updatedAt"] as? Number)?.toLong() ?: 0L,
                            stillSince = (d["stillSince"] as? Number)?.toLong() ?: 0L,
                        )
                    }
                }
                combine(flows) { it.toList() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val places: StateFlow<List<Place>> = uid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else Repo.places(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Pemilik riwayat yang sedang dilihat (null = saya sendiri). */
    val historyUid = MutableStateFlow<String?>(null)
    val trips: StateFlow<List<Trip>> = combine(uid, historyUid) { me, other -> other ?: me }
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else Repo.trips(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Teman yang sedang dichat (null = daftar teman). */
    val chatWith = MutableStateFlow<String?>(null)
    val messages: StateFlow<List<Message>> = combine(uid, chatWith) { me, other ->
        if (me != null && other != null) chatIdOf(me, other) else null
    }.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else Repo.messages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Permintaan membuka dialog SOS (mis. dari widget). */
    val sosPrompt = MutableStateFlow(false)

    val followUid = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    fun follow(uid: String?) { followUid.value = uid }
    fun toast(msg: String?) { message.value = msg }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { message.value = e.message ?: "Terjadi kesalahan" }
    }

    fun signIn(email: String, pass: String, onDone: () -> Unit = {}) = launchSafe {
        Repo.signIn(email, pass); onDone()
    }

    fun signUp(name: String, username: String, email: String, pass: String) = launchSafe {
        require(name.isNotBlank() && username.isNotBlank()) { "Nama dan username wajib diisi" }
        require(pass.length >= 6) { "Kata sandi minimal 6 karakter" }
        Repo.signUp(name, username, email, pass)
    }

    fun signOut() { Repo.signOut() }

    fun invite(codeOrUsername: String) = launchSafe {
        val me = profile.value ?: error("Profil belum siap")
        val name = Repo.invite(me, codeOrUsername)
        message.value = "Undangan dikirim ke $name. Tunggu persetujuan."
    }

    fun accept(f: Friend) = launchSafe {
        Repo.accept(profile.value ?: error("Profil belum siap"), f)
    }

    fun removeFriend(f: Friend) = launchSafe {
        Repo.remove(uid.value ?: return@launchSafe, f.uid)
        if (followUid.value == f.uid) followUid.value = null
    }

    fun setSharing(enabled: Boolean, pauseMs: Long = 0L) = launchSafe {
        val id = uid.value ?: return@launchSafe
        Repo.setSharing(id, enabled, if (pauseMs > 0) System.currentTimeMillis() + pauseMs else 0L)
    }

    fun setFavorite(f: Friend, fav: Boolean) = launchSafe {
        Repo.setFavorite(uid.value ?: return@launchSafe, f.uid, fav)
    }

    fun setGroup(f: Friend, group: String) = launchSafe {
        Repo.setGroup(uid.value ?: return@launchSafe, f.uid, group)
    }

    fun addPlace(name: String, lat: Double, lng: Double, radius: Double) = launchSafe {
        Repo.addPlace(uid.value ?: return@launchSafe, name, lat, lng, radius)
        message.value = "Tempat \"${name.trim()}\" disimpan"
    }

    fun deletePlace(p: Place) = launchSafe {
        Repo.deletePlace(uid.value ?: return@launchSafe, p.id)
    }

    fun viewHistoryOf(uid: String?) { historyUid.value = uid }

    fun openChat(friendUid: String?) {
        chatWith.value = friendUid
        val me = uid.value ?: return
        if (friendUid != null) launchSafe { Repo.pruneChat(chatIdOf(me, friendUid), me) }
    }

    private fun send(type: String, text: String = "", audio: String? = null, durMs: Long = 0L) = launchSafe {
        val me = uid.value ?: return@launchSafe
        val other = chatWith.value ?: return@launchSafe
        Repo.sendMessage(chatIdOf(me, other), me, type, text, audio, durMs)
    }

    fun sendText(text: String) { if (text.isNotBlank()) send("text", text.take(1000)) }
    fun sendVoice(audio: String, durMs: Long) { send("voice", audio = audio, durMs = durMs) }
    fun sendPing() {
        send("ping")
        message.value = "Ping terkirim. Lokasi teman diperbarui bila ia sedang berbagi."
    }

    fun pingFriend(f: Friend) = launchSafe {
        val me = uid.value ?: return@launchSafe
        Repo.sendMessage(chatIdOf(me, f.uid), me, "ping")
        message.value = "Ping terkirim ke ${f.name}"
    }

    fun sendSos(lat: Double, lng: Double) = launchSafe {
        val p = profile.value ?: error("Profil belum siap")
        Repo.publishEvent(p.uid, p.name, "sos", "", lat, lng)
        message.value = "SOS terkirim ke teman Anda"
    }
}
