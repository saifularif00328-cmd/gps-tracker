package com.saifularif.gpstracker.ui.friends

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Profile
import com.saifularif.gpstracker.ui.FriendRowHeader
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.GreenLight
import com.saifularif.gpstracker.ui.theme.Orange
import com.saifularif.gpstracker.ui.theme.Muted

@Composable
fun FriendsScreen(
    me: Profile?,
    friends: List<Friend>,
    onInvite: (String) -> Unit,
    onAccept: (Friend) -> Unit,
    onRemove: (Friend) -> Unit,
    onFollow: (Friend) -> Unit,
    onFavorite: (Friend, Boolean) -> Unit,
    onGroup: (Friend, String) -> Unit,
    onChat: (Friend) -> Unit,
    onPing: (Friend) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var groupFor by remember { mutableStateOf<Friend?>(null) }
    val incoming = friends.filter { it.status == "pending_in" }
    val outgoing = friends.filter { it.status == "pending_out" }
    val accepted = friends.filter { it.status == "accepted" }
        .sortedWith(compareByDescending<Friend> { it.favorite }.thenBy { it.name.lowercase() })

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Teman", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = GreenLight)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Kode Anda", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(me?.code ?: "...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = Green)
                        Text("@${me?.username ?: ""}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Tambahkan saya di GPS-Tracker. Kode: ${me?.code} (username: @${me?.username})")
                        }
                        context.startActivity(Intent.createChooser(send, "Bagikan kode"))
                    }) { Icon(Icons.Default.Share, "Bagikan", tint = Green) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it },
                    Modifier.weight(1f),
                    label = { Text("Kode atau username teman") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Button(onClick = { onInvite(query); query = "" }, shape = RoundedCornerShape(14.dp)) { Text("Undang") }
            }
        }
        if (incoming.isNotEmpty()) {
            item { Text("Permintaan masuk", fontWeight = FontWeight.SemiBold) }
            items(incoming, key = { "in" + it.uid }) { f ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.name, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAccept(f) }) { Text("Terima") }
                            androidx.compose.material3.OutlinedButton(onClick = { onRemove(f) }) { Text("Tolak") }
                        }
                    }
                }
            }
        }
        item { Text("Teman terhubung (${accepted.size})", fontWeight = FontWeight.SemiBold) }
        if (accepted.isEmpty()) item { Text("Belum ada teman terhubung.", color = Muted) }
        items(accepted, key = { "ok" + it.uid }) { f ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { FriendRowHeader(f) }
                        IconButton(onClick = { onChat(f) }) { Icon(Icons.Default.Chat, "Chat", tint = Green) }
                        IconButton(onClick = { onPing(f) }) { Icon(Icons.Default.MyLocation, "Ping lokasi", tint = Green) }
                        IconButton(onClick = { onFavorite(f, !f.favorite) }) {
                            Icon(
                                if (f.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                "Favorit",
                                tint = if (f.favorite) Orange else Muted,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.AssistChip(
                            onClick = { groupFor = f },
                            label = { Text(if (f.group.isBlank()) "Atur grup" else "Grup: ${f.group}") },
                        )
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onFollow(f) }, enabled = f.hasLocation, shape = RoundedCornerShape(12.dp)) { Text("Ikuti") }
                        IconButton(onClick = { onRemove(f) }) { Icon(Icons.Default.Delete, "Hapus", tint = Muted) }
                    }
                }
            }
        }
        if (outgoing.isNotEmpty()) {
            item { Text("Menunggu persetujuan", fontWeight = FontWeight.SemiBold) }
            items(outgoing, key = { "out" + it.uid }) { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name)
                    androidx.compose.material3.TextButton(onClick = { onRemove(f) }) { Text("Batalkan") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    groupFor?.let { f ->
        var text by remember(f.uid) { mutableStateOf(f.group) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { groupFor = null },
            title = { Text("Grup untuk ${f.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Nama grup") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Keluarga", "Kantor", "Teman").forEach { g ->
                            androidx.compose.material3.AssistChip(onClick = { text = g }, label = { Text(g) })
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onGroup(f, text); groupFor = null }) { Text("Simpan") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onGroup(f, ""); groupFor = null }) { Text("Hapus grup") }
            },
        )
    }
}
