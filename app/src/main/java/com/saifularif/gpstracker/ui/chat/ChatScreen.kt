package com.saifularif.gpstracker.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.Message
import com.saifularif.gpstracker.ui.Avatar
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.GreenLight
import com.saifularif.gpstracker.ui.theme.Muted
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun ChatScreen(
    friend: Friend,
    myUid: String,
    messages: List<Message>, // terbaru lebih dulu
    onSendText: (String) -> Unit,
    onSendVoice: (audio: String, durMs: Long) -> Unit,
    onPing: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer(context) }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); player.stop() } }

    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        onMessage(if (ok) "Izin mikrofon aktif. Tahan tombol mic untuk merekam." else "Izin mikrofon ditolak")
    }

    LaunchedEffect(recording) {
        while (recording) {
            recMs = recorder.elapsedMs()
            delay(200)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
            Avatar(friend.name, friend.state(), 36.dp)
            Text(friend.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            IconButton(onClick = onPing) { Icon(Icons.Default.MyLocation, "Ping lokasi", tint = Green) }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { m ->
                val mine = m.from == myUid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Green else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                        when (m.type) {
                            "voice" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                val playing = player.playingId == m.id
                                IconButton(
                                    onClick = { m.audio?.let { player.toggle(m.id, it) } },
                                    modifier = Modifier.size(36.dp),
                                ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar", tint = fg) }
                                Text("Pesan suara ${mmss(m.durMs)}", color = fg)
                            }
                            "ping" -> Text(
                                if (mine) "Anda meminta lokasi diperbarui" else "${friend.name} meminta lokasi Anda diperbarui",
                                color = fg, fontStyle = FontStyle.Italic,
                            )
                            else -> Text(m.text, color = fg)
                        }
                        Text(
                            timeFmt.format(Date(m.ts)),
                            color = fg.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (recording) {
            Text(
                "Merekam ${mmss(recMs)} / 0:30 - lepas untuk mengirim",
                color = Color(0xFFDC2626),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                text, { text = it },
                Modifier.weight(1f),
                placeholder = { Text("Tulis pesan") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            if (text.isNotBlank()) {
                IconButton(onClick = { onSendText(text.trim()); text = "" }) {
                    Icon(Icons.Default.Send, "Kirim", tint = Green)
                }
            } else {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (recording) Color(0xFFDC2626) else GreenLight)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                player.stop()
                                if (!recorder.start()) {
                                    onMessage("Gagal memulai rekaman")
                                    return@detectTapGestures
                                }
                                recording = true
                                tryAwaitRelease()
                                recording = false
                                val result = recorder.stop()
                                if (result != null) onSendVoice(result.first, result.second)
                                else onMessage("Rekaman terlalu singkat. Tahan tombol mic lebih lama.")
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Mic, "Tahan untuk merekam", tint = if (recording) Color.White else Green)
                }
            }
        }
    }
}
