package com.saifularif.gpstracker.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.FriendState
import com.saifularif.gpstracker.ui.FriendRowHeader
import com.saifularif.gpstracker.ui.theme.Green


@Composable
fun MapScreen(
    friends: List<Friend>,
    onFollow: (Friend) -> Unit,
    onOpenGuide: () -> Unit,
    onSos: () -> Unit,
    sharing: Boolean,
    onToggleSharing: () -> Unit,
) {
    var filter by remember { mutableStateOf("all") }
    var selectedUid by remember { mutableStateOf<String?>(null) }
    var centerUid by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()

    val accepted = friends.filter { it.status == "accepted" }
    val groups = accepted.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = accepted.filter {
        when {
            filter == "moving" -> it.state(now) == FriendState.MOVING
            filter == "still" -> it.state(now) == FriendState.STILL
            filter == "fav" -> it.favorite
            filter.startsWith("g:") -> it.group == filter.removePrefix("g:")
            else -> true
        }
    }
    val selected = shown.firstOrNull { it.uid == selectedUid }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            friends = shown,
            modifier = Modifier.fillMaxSize(),
            centerOn = centerUid,
            onMarkerTap = { selectedUid = it.uid; centerUid = it.uid },
        )

        Column(Modifier.padding(12.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GPS-Tracker", style = MaterialTheme.typography.titleMedium, color = Green)
                        FilterChip(
                            selected = sharing,
                            onClick = onToggleSharing,
                            label = { Text(if (sharing) "Berbagi: Aktif" else "Berbagi: Mati") },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSos) { Icon(Icons.Default.Warning, "SOS", tint = Color(0xFFDC2626)) }
                        IconButton(onClick = onOpenGuide) { Icon(Icons.Default.Info, "Petunjuk", tint = Green) }
                    }
                }
            }
            val chips = listOf("all" to "Semua", "moving" to "Bergerak", "still" to "Diam", "fav" to "Favorit") +
                groups.map { "g:$it" to it }
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (!sharing) {
            Card(
                Modifier.align(Alignment.TopCenter).padding(top = 120.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Berbagi mati: teman tidak bisa melihat Anda.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onToggleSharing) { Text("Nyalakan") }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            if (selected != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        FriendRowHeader(selected, now)
                        if (selected.battery >= 0) {
                            Text("Baterai ${selected.battery}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onFollow(selected) }, shape = RoundedCornerShape(14.dp)) { Text("Ikuti") }
                            TextButton(onClick = { selectedUid = null }) { Text("Tutup") }
                        }
                    }
                }
            } else if (accepted.isEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(
                        "Belum ada teman. Buka tab Teman lalu bagikan kode Anda atau masukkan kode teman.",
                        Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.uid }) { f ->
                        Card(
                            Modifier.clickable { onFollow(f) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(4.dp),
                        ) { Box(Modifier.padding(12.dp)) { FriendRowHeader(f, now) } }
                    }
                }
            }
        }
    }
}
