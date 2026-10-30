package com.saifularif.gpstracker

import android.app.Application
import com.saifularif.gpstracker.data.Prefs
import org.osmdroid.config.Configuration

class GpsTrackerApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir
        }
    }
}
