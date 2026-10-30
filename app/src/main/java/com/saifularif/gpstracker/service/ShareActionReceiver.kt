package com.saifularif.gpstracker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.saifularif.gpstracker.data.Repo
import com.saifularif.gpstracker.data.chatIdOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Tombol pada notifikasi "X meminta Anda menyalakan berbagi lokasi". */
class ShareActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = Repo.uid ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        context.getSystemService(NotificationManager::class.java).cancel(notifId)
        val friend = intent.getStringExtra(EXTRA_FRIEND)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ENABLE -> Repo.setSharing(uid, true, 0L)
                    ACTION_DECLINE -> if (friend != null) {
                        Repo.sendMessage(chatIdOf(uid, friend), uid, "text", "Belum bisa membagikan lokasi sekarang.")
                    }
                }
            } catch (_: Exception) {
                // Gagal jaringan/izin: pengguna masih bisa mengubahnya manual di aplikasi.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ENABLE = "com.saifularif.gpstracker.SHARE_ENABLE"
        const val ACTION_DECLINE = "com.saifularif.gpstracker.SHARE_DECLINE"
        const val EXTRA_NOTIF = "notif"
        const val EXTRA_FRIEND = "friend"
    }
}
