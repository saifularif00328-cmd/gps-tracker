package com.saifularif.gpstracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
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
import com.saifularif.gpstracker.data.FriendEvent
import com.saifularif.gpstracker.data.GeofenceTracker
import com.saifularif.gpstracker.data.Place
import com.saifularif.gpstracker.data.Profile
import com.saifularif.gpstracker.data.Repo
import com.saifularif.gpstracker.data.TripDetector
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
    private val friendJobs = HashMap<String, Job>()
    private val notified = HashSet<String>()
    @Volatile private var eventsSeenTs = 0L

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
            Repo.friends(uid).collect { list ->
                val accepted = list.filter { it.status == "accepted" }.map { it.uid }.toSet()
                friendJobs.keys.filter { it !in accepted }.forEach { friendJobs.remove(it)?.cancel() }
                accepted.filter { it !in friendJobs }.forEach { fid ->
                    friendJobs[fid] = scope.launch { Repo.events(fid).collect(::onFriendEvents) }
                }
            }
        }
    }

    private fun applySharing(sharing: Boolean, wasSharing: Boolean) {
        if (sharing) {
            startInForeground("Lokasi sedang dibagikan", "Teman yang Anda setujui dapat melihat posisi Anda")
            requestUpdates(moving = false, force = true)
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
        var newest = eventsSeenTs
        events.sortedBy { it.ts }.forEach { e ->
            if (e.ts <= eventsSeenTs || !notified.add(e.id)) return@forEach
            val text = when (e.type) {
                "arrive" -> "${e.fromName} tiba di ${e.placeName}"
                "leave" -> "${e.fromName} meninggalkan ${e.placeName}"
                "battery_low" -> "Baterai ${e.fromName} hampir habis"
                else -> return@forEach
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                e.id.hashCode(),
                NotificationCompat.Builder(this, CHANNEL_FRIENDS)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle("GPS-Tracker")
                    .setContentText(text)
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
