package com.saifularif.gpstracker.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.FriendState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun stateColor(s: FriendState) = when (s) {
    FriendState.MOVING -> AColor.parseColor("#16A34A")
    FriendState.STILL -> AColor.parseColor("#F59E0B")
    FriendState.OFFLINE -> AColor.parseColor("#94A3B8")
}

/**
 * Peta OpenStreetMap (gratis, tanpa API key).
 * [centerOn] -> kamera mengikuti teman itu; [trail] -> jejak rute yang digambar.
 */
@Composable
fun OsmMap(
    friends: List<Friend>,
    modifier: Modifier = Modifier,
    centerOn: String? = null,
    trail: List<GeoPoint> = emptyList(),
    onMarkerTap: (Friend) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(-6.2, 106.8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                overlays.add(MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                    enableMyLocation()
                    runOnFirstFix { post { controller.animateTo(myLocation) } }
                })
            }
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onDetach()
        }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        view.overlays.removeAll { it is Marker || it is Polyline }
        if (trail.size > 1) {
            view.overlays.add(Polyline().apply {
                setPoints(trail)
                outlinePaint.color = AColor.parseColor("#16A34A")
                outlinePaint.strokeWidth = 10f
            })
        }
        val now = System.currentTimeMillis()
        friends.filter { it.hasLocation }.forEach { f ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(f.lat!!, f.lng!!)
                title = f.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(stateColor(f.state(now)))
                    setStroke(6, AColor.WHITE)
                    setSize(56, 56)
                }
                setOnMarkerClickListener { _, _ -> onMarkerTap(f); true }
            })
        }
        centerOn?.let { id ->
            friends.firstOrNull { it.uid == id && it.hasLocation }?.let {
                view.controller.animateTo(GeoPoint(it.lat!!, it.lng!!))
            }
        }
        view.invalidate()
    })
}
