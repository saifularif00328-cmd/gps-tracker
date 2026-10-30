package com.saifularif.gpstracker.ui.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.saifularif.gpstracker.data.Place
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.GreenLight
import com.saifularif.gpstracker.ui.theme.Muted

@SuppressLint("MissingPermission")
@Composable
fun PlacesScreen(
    places: List<Place>,
    onAdd: (name: String, lat: Double, lng: Double, radius: Double) -> Unit,
    onDelete: (Place) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(100f) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tempat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { name = ""; radius = 100f; dialog = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Add, null)
                Text(" Tambah")
            }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = GreenLight)) {
            Text(
                "Teman yang Anda setujui diberi tahu saat Anda tiba atau meninggalkan tempat ini. Simpan tempat saat Anda sedang berada di sana.",
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (places.isEmpty()) Text("Belum ada tempat. Contoh: Rumah, Kantor, Sekolah.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(places, key = { it.id }) { p ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, null, tint = Green)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("Radius ${p.radius.toInt()} m", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Hapus", tint = Muted) }
                    }
                }
            }
        }
    }

    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text("Tambah tempat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nama tempat") })
                Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodySmall)
                Slider(radius, { radius = it }, valueRange = 50f..500f)
                Text("Lokasi tempat = posisi Anda saat ini.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { onMessage("Isi nama tempat"); return@TextButton }
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) { onMessage("Izin lokasi diperlukan"); return@TextButton }
                dialog = false
                val n = name
                val r = radius.toDouble()
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) onAdd(n, loc.latitude, loc.longitude, r)
                        else onMessage("Lokasi belum tersedia, coba lagi di tempat terbuka")
                    }
                    .addOnFailureListener { onMessage("Gagal mengambil lokasi") }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { dialog = false }) { Text("Batal") } },
    )
}
