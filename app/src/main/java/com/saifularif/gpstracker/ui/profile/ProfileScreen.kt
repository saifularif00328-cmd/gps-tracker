package com.saifularif.gpstracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saifularif.gpstracker.data.Profile
import com.saifularif.gpstracker.data.formatDuration
import com.saifularif.gpstracker.ui.Avatar
import com.saifularif.gpstracker.ui.theme.Muted

private const val HOUR = 3_600_000L

@Composable
fun ProfileScreen(
    me: Profile?,
    onSetSharing: (enabled: Boolean, pauseMs: Long) -> Unit,
    onOpenGuide: () -> Unit,
    onSignOut: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val paused = me != null && me.shareEnabled && me.pausedUntil > now
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(me?.name ?: "?", null, 64.dp)
            Column {
                Text(me?.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@${me?.username ?: ""}", color = Muted)
            }
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bagikan lokasi saya", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                me == null -> ""
                                !me.shareEnabled -> "Mati. Tidak ada yang bisa melihat Anda."
                                paused -> "Dijeda, lanjut dalam ${formatDuration(me.pausedUntil - now)}."
                                else -> "Aktif untuk teman yang disetujui."
                            },
                            color = Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = me?.shareEnabled == true, onCheckedChange = { onSetSharing(it, 0L) })
                }
                if (me?.shareEnabled == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetSharing(true, HOUR) }) { Text("Jeda 1 jam") }
                        OutlinedButton(onClick = { onSetSharing(true, 8 * HOUR) }) { Text("Jeda 8 jam") }
                        if (paused) OutlinedButton(onClick = { onSetSharing(true, 0L) }) { Text("Lanjut") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onOpenGuide, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Petunjuk Pemakaian")
        }
        Button(onClick = onSignOut, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Keluar") }
    }
}
