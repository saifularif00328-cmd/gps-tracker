package com.saifularif.gpstracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.annotation.SuppressLint
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saifularif.gpstracker.GpsTrackerApp
import com.saifularif.gpstracker.MainActivity
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Message
import com.saifularif.gpstracker.data.chatIdOf
import com.saifularif.gpstracker.data.FriendEvent
import com.saifularif.gpstracker.data.GeofenceTracker
import com.saifularif.gpstracker.data.Place
import com.saifularif.gpstracker.data.Profile
import com.saifularif.gpstracker.data.Repo
import com.saifularif.gpstracker.data.TripDetector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Layanan latar depan:
 *  - berbagi AKTIF  : kirim lokasi adaptif (bergerak ~5 dtk / diam ~60 dtk), rekam perjalanan,
 *                     deteksi tiba/pergi tempat, peringatan baterai rendah;
 *  - berbagi MATI   : tidak meminta/mengirim lokasi sama sekali;
 *  - selalu         : mendengarkan peristiwa teman dan menampilkan notifikasi lokal.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val prefs by lazy { (application as GpsTrackerApp).prefs }

    private var movingMode: Boolean? = null
    private var lastSent: Location? = null
    private var lastSentAt = 0L
    private var stillSince = 0L

    @Volatile private var me: Profile? = null
    @Volatile private var places: List<Place> = emptyList()
    private val trips = TripDetector()
    private val geofence = GeofenceTracker()
    private var batteryLowSent = false

    private var resumeJob: Job? = null
    private val ready = CompletableDeferred<Unit>()
    private val friendJobs = HashMap<String, Job>()
    private val notified = HashSet<String>()
    @Volatile private var eventsSeenTs = 0L
    @Volatile private var msgSeenTs = 0L
    private val friendNames = HashMap<String, String>()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::handle)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground("Menyiapkan...")
        val uid = Repo.uid ?: run { stopSelf(); return START_NOT_STICKY }
        if (watching) return START_STICKY
        watching = true
        startWatchers(uid)
        return START_STICKY
    }

    private var watching = false

    private fun startWatchers(uid: String) {
        scope.launch {
            eventsSeenTs = prefs.eventsSeenFlow.first().takeIf { it > 0 }
                ?: System.currentTimeMillis().also { prefs.setEventsSeen(it) }
            msgSeenTs = prefs.msgSeenFlow.first().takeIf { it > 0 }
                ?: System.currentTimeMillis().also { prefs.setMsgSeen(it) }
            ready.complete(Unit)
            runCatching { Repo.pruneOld(uid) }
        }
        scope.launch {
            Repo.profile(uid).collect { p ->
                val wasSharing = me?.isSharing() == true
                me = p
                applySharing(p?.isSharing() == true, wasSharing)
                // Jeda berakhir tanpa perubahan data: jadwalkan evaluasi ulang.
                resumeJob?.cancel()
                val left = (p?.pausedUntil ?: 0L) - System.currentTimeMillis()
                if (p != null && p.shareEnabled && left > 0) {
                    resumeJob = scope.launch {
                        delay(left + 500)
                        me?.let { applySharing(it.isSharing(), false) }
                    }
                }
            }
        }
        scope.launch { Repo.places(uid).collect { places = it } }
        scope.launch {
            ready.await() // penanda "sudah dilihat" harus terbaca dulu agar kabar lama tidak dinotifikasi ulang
            Repo.friends(uid).collect { list ->
                val acceptedList = list.filter { it.status == "accepted" }
                friendNames.clear()
                acceptedList.forEach { friendNames[it.uid] = it.name }
                val accepted = acceptedList.map { it.uid }.toSet()
                friendJobs.keys.filter { it !in accepted }.forEach { friendJobs.remove(it)?.cancel() }
                accepted.filter { it !in friendJobs }.forEach { fid ->
                    friendJobs[fid] = scope.launch {
                        launch { Repo.events(fid).collect(::onFriendEvents) }
                        launch { Repo.messages(chatIdOf(uid, fid)).collect { onMessages(fid, it) } }
                    }
                }
            }
        }
    }

    private fun applySharing(sharing: Boolean, wasSharing: Boolean) {
        if (sharing) {
            startInForeground("Lokasi sedang dibagikan", "Teman yang Anda setujui dapat melihat posisi Anda")
            requestUpdates(moving = false, force = true)
            if (!wasSharing) respondToPing() // kirim posisi segar begitu berbagi dinyalakan
        } else {
            client.removeLocationUpdates(callback)
            movingMode = null
            flushTrip()
            startInForeground("Berbagi lokasi dimatikan", "Anda tetap menerima kabar teman")
        }
    }

    // ---- Notifikasi layanan ----

    private fun startInForeground(title: String, text: String = "") {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Berbagi lokasi", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Hentikan", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---- Peristiwa teman -> notifikasi lokal ----

    private fun onFriendEvents(events: List<FriendEvent>) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FRIENDS, "Kabar teman", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "SOS darurat", NotificationManager.IMPORTANCE_HIGH)
        )
        var newest = eventsSeenTs
        events.sortedBy { it.ts }.forEach { e ->
            if (e.ts <= eventsSeenTs || !notified.add(e.id)) return@forEach
            val sos = e.type == "sos"
            val text = when (e.type) {
                "arrive" -> "${e.fromName} tiba di ${e.placeName}"
                "leave" -> "${e.fromName} meninggalkan ${e.placeName}"
                "battery_low" -> "Baterai ${e.fromName} hampir habis"
                "sos" -> "${e.fromName} meminta bantuan! Ketuk untuk membuka lokasinya."
                else -> return@forEach
            }
            val open = if (sos && e.lat != null && e.lng != null) {
                val geo = Uri.parse("geo:${e.lat},${e.lng}?q=${e.lat},${e.lng}(SOS ${e.fromName})")
                PendingIntent.getActivity(this, e.id.hashCode(), Intent(Intent.ACTION_VIEW, geo), PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            }
            nm.notify(
                e.id.hashCode(),
                NotificationCompat.Builder(this, if (sos) CHANNEL_SOS else CHANNEL_FRIENDS)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle(if (sos) "SOS DARURAT" else "GPS-Tracker")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(if (sos) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                    .apply { if (sos) setCategory(NotificationCompat.CATEGORY_ALARM) }
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            )
            if (e.ts > newest) newest = e.ts
        }
        if (newest > eventsSeenTs) {
            eventsSeenTs = newest
            scope.launch { prefs.setEventsSeen(newest) }
        }
    }

    // ---- Pesan teman -> notifikasi lokal; ping -> kirim lokasi segar ----

    private fun onMessages(friendUid: String, messages: List<Message>) {
        val myUid = Repo.uid ?: return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Pesan", NotificationManager.IMPORTANCE_HIGH)
        )
        val name = friendNames[friendUid] ?: "Teman"
        var newest = msgSeenTs
        messages.sortedBy { it.ts }.forEach { m ->
            if (m.from == myUid || m.ts <= msgSeenTs || !notified.add("m-" + m.id)) return@forEach
            if (m.type == "ping") {
                handlePing(friendUid, name, m.id)
                if (m.ts > newest) newest = m.ts
                return@forEach
            }
            val text = when (m.type) {
                "text" -> "$name: ${m.text}"
                "voice" -> "$name mengirim pesan suara"
                else -> return@forEach
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                m.id.hashCode(),
                NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("GPS-Tracker")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            )
            if (m.ts > newest) newest = m.ts
        }
        if (newest > msgSeenTs) {
            msgSeenTs = newest
            scope.launch { prefs.setMsgSeen(newest) }
        }
    }

    /**
     * Ping: bila berbagi aktif langsung kirim lokasi segar; bila mati/dijeda tampilkan notifikasi
     * dengan pilihan Nyalakan / Tidak (lokasi tidak pernah bocor tanpa persetujuan).
     */
    private fun handlePing(friendUid: String, name: String, messageId: String) {
        if (me?.isSharing() == true) {
            respondToPing()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Pesan", NotificationManager.IMPORTANCE_HIGH)
        )
        val notifId = messageId.hashCode()
        fun action(code: Int, name: String) = PendingIntent.getBroadcast(
            this, notifId + code,
            Intent(this, ShareActionReceiver::class.java)
                .setAction(name)
                .putExtra(ShareActionReceiver.EXTRA_NOTIF, notifId)
                .putExtra(ShareActionReceiver.EXTRA_FRIEND, friendUid),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            notifId,
            NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("$name meminta lokasi Anda")
                .setContentText("Berbagi lokasi Anda sedang mati. Nyalakan agar $name bisa melihat posisi Anda?")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Berbagi lokasi Anda sedang mati. Nyalakan agar $name bisa melihat posisi Anda?"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Nyalakan", action(1, ShareActionReceiver.ACTION_ENABLE))
                .addAction(0, "Tidak", action(2, ShareActionReceiver.ACTION_DECLINE))
                .build()
        )
    }

    /** Ping hanya dijawab bila berbagi aktif, sehingga tidak membocorkan lokasi saat berbagi mati. */
    @SuppressLint("MissingPermission")
    private fun respondToPing() {
        val profile = me
        if (profile == null || !profile.isSharing()) return
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener
                val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                scope.launch {
                    runCatching {
                        Repo.publishLocation(
                            profile.uid, loc.latitude, loc.longitude,
                            if (loc.hasSpeed()) loc.speed.toDouble() else 0.0,
                            loc.bearing.toDouble(), battery, stillSince,
                        )
                    }
                }
            }
    }

    // ---- Lokasi ----

    @Suppress("MissingPermission")
    private fun requestUpdates(moving: Boolean, force: Boolean = false) {
        if (!force && movingMode == moving) return
        movingMode = moving
        val interval = if (moving) 5_000L else 30_000L
        val req = LocationRequest.Builder(
            if (moving) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval
        ).setMinUpdateDistanceMeters(if (moving) 10f else 0f).build()
        client.removeLocationUpdates(callback)
        try {
            client.requestLocationUpdates(req, callback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun handle(loc: Location) {
        val profile = me
        if (profile == null || !profile.isSharing()) return
        val uid = profile.uid
        val now = System.currentTimeMillis()
        val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
        val moving = speed >= Friend.MOVING_SPEED_MPS
        if (moving) stillSince = 0L else if (stillSince == 0L) stillSince = now
        requestUpdates(moving)

        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Perjalanan, tempat, baterai: dievaluasi di setiap sampel.
        trips.onSample(now, loc.latitude, loc.longitude, moving)?.let { trip ->
            scope.launch { runCatching { Repo.publishTrip(uid, trip) } }
        }
        geofence.update(places, loc.latitude, loc.longitude).forEach { t ->
            scope.launch {
                runCatching {
                    Repo.publishEvent(uid, profile.name, if (t.entered) "arrive" else "leave", t.place.name)
                }
            }
        }
        if (battery in 0..LOW_BATTERY && !batteryLowSent) {
            batteryLowSent = true
            scope.launch { runCatching { Repo.publishEvent(uid, profile.name, "battery_low", "") } }
        } else if (battery > LOW_BATTERY + 15) {
            batteryLowSent = false
        }

        // Kirim posisi hanya bila pindah > 10 m atau sudah 60 dtk (hemat kuota gratis Firestore).
        val prev = lastSent
        val movedFar = prev == null || loc.distanceTo(prev) > 10f
        val due = now - lastSentAt >= 60_000L
        if (!movedFar && !due) return
        lastSent = loc
        lastSentAt = now
        scope.launch {
            runCatching {
                Repo.publishLocation(uid, loc.latitude, loc.longitude, speed, loc.bearing.toDouble(), battery, stillSince)
            }
        }
    }

    private fun flushTrip() {
        val uid = me?.uid ?: return
        // Scope terpisah: scope layanan dibatalkan di onDestroy sebelum unggahan selesai.
        trips.flush()?.let { trip ->
            CoroutineScope(Dispatchers.IO).launch { runCatching { Repo.publishTrip(uid, trip) } }
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        flushTrip()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "share"
        private const val CHANNEL_FRIENDS = "friends"
        private const val CHANNEL_MESSAGES = "messages"
        private const val CHANNEL_SOS = "sos"
        private const val NOTIF_ID = 1
        private const val LOW_BATTERY = 15
        const val ACTION_STOP = "stop"

        fun start(context: Context) {
            val i = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
