package com.saifularif.gpstracker.ui.follow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.FriendState
import com.saifularif.gpstracker.data.statusText
import com.saifularif.gpstracker.ui.FriendRowHeader
import com.saifularif.gpstracker.ui.color
import com.saifularif.gpstracker.ui.map.OsmMap
import com.saifularif.gpstracker.ui.theme.Muted
import org.osmdroid.util.GeoPoint

@Composable
fun FollowScreen(
    friends: List<Friend>,
    followUid: String?,
    onPick: (String?) -> Unit,
) {
    val accepted = friends.filter { it.status == "accepted" }
    val target = accepted.firstOrNull { it.uid == followUid }

    if (target == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ikuti teman", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Pilih teman yang ingin diikuti.", color = Muted)
            if (accepted.isEmpty()) Text("Belum ada teman terhubung.", color = Muted)
            accepted.forEach { f ->
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = f.hasLocation) { onPick(f.uid) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) { Box(Modifier.padding(12.dp)) { FriendRowHeader(f) } }
            }
        }
        return
    }

    val trail = remember(target.uid) { mutableStateListOf<GeoPoint>() }
    LaunchedEffect(target.lat, target.lng) {
        val lat = target.lat ?: return@LaunchedEffect
        val lng = target.lng ?: return@LaunchedEffect
        val p = GeoPoint(lat, lng)
        if (trail.isEmpty() || trail.last().distanceToAsDouble(p) > 5.0) trail.add(p)
    }

    Box(Modifier.fillMaxSize()) {
        OsmMap(friends = listOf(target), modifier = Modifier.fillMaxSize(), centerOn = target.uid, trail = trail.toList())
        Card(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(6.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FriendRowHeader(target)
                Stepper(target.state())
                Text(
                    "Jejak: ${trail.size} titik" + if (target.battery >= 0) " • Baterai ${target.battery}%" else "",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { onPick(null) }, shape = RoundedCornerShape(14.dp)) { Text("Berhenti ikuti") }
            }
        }
    }
}

@Composable
private fun Stepper(current: FriendState) {
    val steps = listOf(FriendState.MOVING to "Bergerak", FriendState.STILL to "Berhenti", FriendState.OFFLINE to "Offline")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEach { (s, label) ->
            val active = s == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (active) s.color() else MaterialTheme.colorScheme.outline))
                Text(
                    " $label",
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Color.Unspecified else Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
