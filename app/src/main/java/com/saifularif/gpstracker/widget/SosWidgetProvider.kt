package com.saifularif.gpstracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.saifularif.gpstracker.MainActivity
import com.saifularif.gpstracker.R
import com.saifularif.gpstracker.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget layar utama: status berbagi, hingga 3 teman favorit, tombol SOS (membuka dialog konfirmasi
 * di aplikasi agar tidak terkirim tak sengaja), buka aplikasi, dan segarkan.
 */
class SosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SosWidgetProvider::class.java))
            refresh(context, manager, ids)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Tampilkan segera tombol-tombolnya, lalu isi data begitu Firestore menjawab.
        ids.forEach { manager.updateAppWidget(it, views(context, "Memuat...", emptyList())) }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val share = runCatching { Repo.sharingStatus() }.getOrNull() ?: "Masuk dulu di aplikasi"
                val lines = runCatching { Repo.favoriteStatuses() }.getOrDefault(emptyList())
                ids.forEach { manager.updateAppWidget(it, views(context, share, lines)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, share: String, lines: List<Pair<String, String>>): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget)
        v.setTextViewText(R.id.w_share, share)
        val ids = intArrayOf(R.id.w_l1, R.id.w_l2, R.id.w_l3)
        ids.forEachIndexed { i, id ->
            val line = lines.getOrNull(i)
            v.setTextViewText(
                id,
                when {
                    line != null -> "${line.first}: ${line.second}"
                    i == 0 && lines.isEmpty() -> "Belum ada teman favorit (beri bintang di tab Teman)"
                    else -> ""
                }
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        v.setOnClickPendingIntent(
            R.id.w_sos,
            PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SOS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
            )
        )
        v.setOnClickPendingIntent(
            R.id.w_open,
            PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), flags)
        )
        v.setOnClickPendingIntent(
            R.id.w_refresh,
            PendingIntent.getBroadcast(
                context, 3,
                Intent(context, SosWidgetProvider::class.java).setAction(ACTION_REFRESH),
                flags
            )
        )
        return v
    }

    companion object {
        const val ACTION_REFRESH = "com.saifularif.gpstracker.WIDGET_REFRESH"
    }
}
