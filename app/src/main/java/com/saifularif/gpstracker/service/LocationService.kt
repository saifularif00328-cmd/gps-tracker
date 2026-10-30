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
import com.saifularif.gpstracker.MainActivity
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service yang mengirim lokasi ke Firestore secara adaptif:
 * bergerak -> tiap ~5 detik (min jarak 10 m); diam -> tiap ~60 detik.
 * Tujuannya menghemat baterai dan kuota gratis Firestore.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var movingMode: Boolean? = null
    private var lastSent: Location? = null
    private var lastSentAt = 0L
    private var stillSince = 0L

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
        startInForeground()
        requestUpdates(moving = false)
        return START_STICKY
    }

    private fun startInForeground() {
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
            .setContentTitle("Lokasi sedang dibagikan")
            .setContentText("Teman yang Anda setujui dapat melihat posisi Anda")
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

    @Suppress("MissingPermission")
    private fun requestUpdates(moving: Boolean) {
        if (movingMode == moving) return
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
        val now = System.currentTimeMillis()
        val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
        val moving = speed >= Friend.MOVING_SPEED_MPS
        if (moving) stillSince = 0L else if (stillSince == 0L) stillSince = now
        requestUpdates(moving)

        val prev = lastSent
        val movedFar = prev == null || loc.distanceTo(prev) > 10f
        val due = now - lastSentAt >= 60_000L
        if (!movedFar && !due) return

        lastSent = loc
        lastSentAt = now
        val uid = Repo.uid ?: return stopSelf()
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        scope.launch {
            runCatching {
                val p = Repo.getProfile(uid)
                if (p != null && !p.isSharing()) {
                    Repo.clearLocation(uid)
                } else {
                    Repo.publishLocation(uid, loc.latitude, loc.longitude, speed, loc.bearing.toDouble(), battery, stillSince)
                }
            }
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "share"
        private const val NOTIF_ID = 1
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
