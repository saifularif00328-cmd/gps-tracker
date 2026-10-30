package com.saifularif.gpstracker.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Trip
import com.saifularif.gpstracker.data.formatDuration
import com.saifularif.gpstracker.ui.map.OsmMap
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.Muted
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    friends: List<Friend>,
    trips: List<Trip>,
    selectedUid: String?,
    onSelect: (String?) -> Unit,
) {
    var openTrip by remember { mutableStateOf<Trip?>(null) }
    BackHandler(enabled = openTrip != null) { openTrip = null }

    val current = openTrip
    if (current != null) {
        Playback(current, onClose = { openTrip = null })
        return
    }

    val dayFmt = remember { SimpleDateFormat("EEEE, d MMM", Locale("id", "ID")) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val accepted = friends.filter { it.status == "accepted" }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Riwayat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text("Perjalanan disimpan 7 hari.", color = Muted, style = MaterialTheme.typography.bodySmall)
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "me") {
                FilterChip(selected = selectedUid == null, onClick = { onSelect(null) }, label = { Text("Saya") })
            }
            items(accepted, key = { it.uid }) { f ->
                FilterChip(selected = selectedUid == f.uid, onClick = { onSelect(f.uid) }, label = { Text(f.name) })
            }
        }
        if (trips.isEmpty()) {
            Text(
                if (selectedUid == null) "Belum ada perjalanan. Tercatat otomatis saat Anda bergerak lalu berhenti 5 menit (berbagi lokasi harus aktif)."
                else "Belum ada perjalanan dari teman ini.",
                color = Muted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        val days = trips.groupBy { dayFmt.format(Date(it.startAt)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { (day, list) ->
                item(key = "day-$day") {
                    Text(day, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(list, key = { it.id }) { t ->
                    Card(
                        Modifier.fillMaxWidth().clickable { openTrip = t },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("${timeFmt.format(Date(t.startAt))} - ${timeFmt.format(Date(t.endAt))}", fontWeight = FontWeight.SemiBold)
                                Text("Durasi ${formatDuration(t.endAt - t.startAt)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("%.1f km".format(t.distanceM / 1000), color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Playback(trip: Trip, onClose: () -> Unit) {
    val pts = remember(trip.id) { trip.points.map { GeoPoint(it.lat, it.lng) } }
    var index by remember(trip.id) { mutableFloatStateOf(0f) }
    var playing by remember(trip.id) { mutableStateOf(false) }

    LaunchedEffect(playing, pts) {
        while (playing) {
            delay(150)
            val next = index + 1
            if (next >= pts.lastIndex) {
                index = pts.lastIndex.coerceAtLeast(0).toFloat()
                playing = false
            } else {
                index = next
            }
        }
    }

    val i = index.toInt().coerceIn(0, (pts.size - 1).coerceAtLeast(0))
    val here = pts.getOrNull(i)
    val marker = here?.let {
        Friend(
            uid = "playback", name = "Perjalanan", status = "accepted",
            lat = it.latitude, lng = it.longitude, speedMps = 5.0, updatedAt = System.currentTimeMillis(),
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            OsmMap(
                friends = listOfNotNull(marker),
                modifier = Modifier.fillMaxSize(),
                centerOn = marker?.uid,
                trail = pts.take(i + 1),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.1f km • %s".format(trip.distanceM / 1000, formatDuration(trip.endAt - trip.startAt)),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClose) { Text("Tutup") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (index >= pts.lastIndex) index = 0f
                    playing = !playing
                }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar", tint = Green) }
                Slider(
                    value = index,
                    onValueChange = { index = it; playing = false },
                    valueRange = 0f..(pts.size - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
