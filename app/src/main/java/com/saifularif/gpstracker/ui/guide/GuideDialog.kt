package com.saifularif.gpstracker.ui.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.Muted
import kotlinx.coroutines.launch

private data class GuidePage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    GuidePage(Icons.Default.Group, "1. Undang teman", "Buka tab Teman, bagikan kode 6 karakter Anda atau masukkan kode/username teman. Lokasi baru terlihat setelah teman menyetujui."),
    GuidePage(Icons.Default.Place, "2. Lihat di peta", "Hijau = bergerak (lengkap dengan km/j). Oranye = diam (ada lama diam). Abu-abu = offline atau HP mati."),
    GuidePage(Icons.Default.Navigation, "3. Ikuti teman", "Ketuk kartu teman (di Peta bagian bawah atau di tab Teman) untuk langsung mengikuti. Kamera mengikuti dan jejak rutenya digambar. Teman yang offline diikuti di lokasi terakhirnya."),
    GuidePage(Icons.Default.History, "4. Riwayat & favorit", "Tab Riwayat menyimpan perjalanan 7 hari dan bisa diputar ulang. Beri bintang pada teman penting dan atur grup (Keluarga, Kantor) lalu saring di peta."),
    GuidePage(Icons.Default.Home, "5. Tempat & kabar teman", "Di tab Tempat simpan Rumah/Kantor saat Anda berada di sana. Teman yang Anda setujui diberi tahu saat Anda tiba atau pergi, begitu juga sebaliknya."),
    GuidePage(Icons.Default.Chat, "6. Chat, suara & SOS", "Di tab Teman ketuk ikon chat: kirim teks, atau tahan tombol mic untuk pesan suara (maks 30 detik). Tombol \"Minta aktifkan\" meminta teman menyalakan berbagi lokasi: ia menerima notifikasi dan cukup ketuk Nyalakan. Tombol merah di Peta mengirim SOS beserta lokasi ke semua teman."),
    GuidePage(Icons.Default.Lock, "7. Atur privasi", "Di tab Profil Anda bisa mematikan atau menjeda berbagi lokasi (1 jam / 8 jam) kapan saja. Teman bisa dihapus kapan saja."),
    GuidePage(Icons.Default.BatteryChargingFull, "8. Jaga tetap aktif", "Pilih izin lokasi \"Selalu\". Di HP Xiaomi/Oppo/Vivo: Pengaturan > Baterai > GPS-Tracker > Tanpa batasan, dan kunci aplikasi di menu aplikasi terbaru."),
)

/** Popup petunjuk pemakaian. [showDontShowAgain] hanya untuk tampil otomatis pertama kali. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideDialog(
    showDontShowAgain: Boolean,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var dontShow by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onDismiss(dontShow) }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Petunjuk Pemakaian", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                HorizontalPager(state, Modifier.fillMaxWidth().height(270.dp)) { i ->
                    val p = pages[i]
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(p.icon, null, Modifier.size(40.dp), tint = Green) }
                        Spacer(Modifier.height(16.dp))
                        Text(p.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(p.body, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.size(if (i == state.currentPage) 18.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == state.currentPage) Green else MaterialTheme.colorScheme.outline)
                        )
                    }
                }
                if (showDontShowAgain) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(dontShow, { dontShow = it })
                        Text("Jangan tampilkan lagi", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val last = state.currentPage == pages.lastIndex
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onDismiss(dontShow) }) { Text("Tutup") }
                    Button(onClick = {
                        if (last) onDismiss(dontShow) else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                    }) { Text(if (last) "Mengerti" else "Lanjut") }
                }
            }
        }
    }
}

/** Popup kecil kontekstual (sekali tampil). */
@Composable
fun HintDialog(title: String, body: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Mengerti") } },
        shape = RoundedCornerShape(20.dp),
    )
}
