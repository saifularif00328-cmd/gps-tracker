package com.saifularif.gpstracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.saifularif.gpstracker.service.LocationService
import com.saifularif.gpstracker.ui.auth.AuthScreen
import com.saifularif.gpstracker.ui.follow.FollowScreen
import com.saifularif.gpstracker.ui.friends.FriendsScreen
import com.saifularif.gpstracker.ui.chat.ChatScreen
import com.saifularif.gpstracker.ui.guide.GuideDialog
import com.saifularif.gpstracker.ui.guide.HintDialog
import com.saifularif.gpstracker.ui.history.HistoryScreen
import com.saifularif.gpstracker.ui.places.PlacesScreen
import com.saifularif.gpstracker.ui.map.MapScreen
import com.saifularif.gpstracker.ui.onboarding.OnboardingScreen
import com.saifularif.gpstracker.ui.profile.ProfileScreen
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.GpsTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { GpsTrackerTheme { Root(vm) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SOS, false) == true) vm.sosPrompt.value = true
    }

    companion object {
        const val EXTRA_SOS = "sos"
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1200); splash = false }

    val uid by vm.uid.collectAsState()
    val onboardingDone by vm.prefs.onboardingDoneFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    LaunchedEffect(msg) {
        msg?.let { snackbar.showSnackbar(it); vm.toast(null) }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            splash || onboardingDone == null -> SplashScreen()
            onboardingDone == false -> OnboardingScreen { scope.launch { vm.prefs.setOnboardingDone() } }
            uid == null -> AuthScreen(
                onSignIn = { e, p -> vm.signIn(e, p) },
                onSignUp = { n, u, e, p -> vm.signUp(n, u, e, p) },
            )
            else -> MainScaffold(vm)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Green), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).background(Color.White, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Place, null, Modifier.size(56.dp), tint = Green) }
            Text("GPS-Tracker", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text("by Saiful Arif", color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private enum class Tab(val label: String) { MAP("Peta"), FRIENDS("Teman"), HISTORY("Riwayat"), PLACES("Tempat"), PROFILE("Profil") }

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by vm.profile.collectAsState()
    val friends by vm.friends.collectAsState()
    val followUid by vm.followUid.collectAsState()
    val places by vm.places.collectAsState()
    val trips by vm.trips.collectAsState()
    val historyUid by vm.historyUid.collectAsState()
    val chatWith by vm.chatWith.collectAsState()
    val messages by vm.messages.collectAsState()
    val sosPrompt by vm.sosPrompt.collectAsState()
    val guideHidden by vm.prefs.guideHiddenFlow.collectAsState(initial = true)
    val followHintSeen by vm.prefs.followHintFlow.collectAsState(initial = true)
    val friendsHintSeen by vm.prefs.friendsHintFlow.collectAsState(initial = true)

    var tab by remember { mutableStateOf(Tab.MAP) }
    var guideOpen by remember { mutableStateOf(false) }
    var autoGuideHandled by remember { mutableStateOf(false) }
    LaunchedEffect(guideHidden) {
        // tampil otomatis sekali saat pertama membuka Peta (setelah preferensi terbaca)
        if (!guideHidden && !autoGuideHandled) { guideOpen = true; autoGuideHandled = true }
    }

    // ---- Izin bertahap ----
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var fineOk by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var bgOk by remember { mutableStateOf(granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) || Build.VERSION.SDK_INT < 29) }
    var explain by remember { mutableStateOf(false) }
    var explainBg by remember { mutableStateOf(false) }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { bgOk = it }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        fineOk = r[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineOk && !bgOk && Build.VERSION.SDK_INT >= 29) explainBg = true
    }
    LaunchedEffect(Unit) { if (!fineOk) explain = true else if (!bgOk) explainBg = true }

    val shareEnabled = me?.shareEnabled == true
    LaunchedEffect(fineOk, me?.uid) {
        // Layanan tetap berjalan saat login (untuk kabar teman); ia sendiri berhenti mengirim lokasi bila berbagi mati.
        if (me != null && fineOk) LocationService.start(context) else LocationService.stop(context)
    }

    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Izin lokasi") },
        text = { Text("Aplikasi memakai lokasi Anda untuk dibagikan hanya kepada teman yang Anda setujui, dan menampilkan posisi Anda di peta. Anda bisa mematikannya kapan saja di tab Profil.") },
        confirmButton = {
            TextButton(onClick = {
                explain = false
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                fineLauncher.launch(perms.toTypedArray())
            }) { Text("Lanjut") }
        },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Nanti") } },
    )
    if (explainBg) AlertDialog(
        onDismissRequest = { explainBg = false },
        title = { Text("Izinkan \"Selalu\"") },
        text = { Text("Agar teman tetap bisa melihat posisi Anda saat layar mati atau aplikasi ditutup, pilih \"Izinkan sepanjang waktu\" pada layar berikutnya.") },
        confirmButton = {
            TextButton(onClick = { explainBg = false; bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text("Lanjut") }
        },
        dismissButton = { TextButton(onClick = { explainBg = false }) { Text("Nanti") } },
    )

    if (guideOpen) GuideDialog(showDontShowAgain = !guideHidden) { dontShow ->
        guideOpen = false
        if (dontShow) scope.launch { vm.prefs.setGuideHidden(true) }
    }
    if (tab == Tab.MAP && followUid != null && !followHintSeen && !guideOpen) {
        HintDialog("Mode Ikuti", "Pilih teman yang sedang bergerak. Kamera akan mengikuti dan jejak rutenya digambar di peta.") {
            scope.launch { vm.prefs.setFollowHintSeen() }
        }
    }
    if (tab == Tab.FRIENDS && !friendsHintSeen && !guideOpen) {
        HintDialog("Menambah teman", "Bagikan kode Anda atau masukkan kode/username teman. Lokasi baru terlihat setelah teman menerima undangan.") {
            scope.launch { vm.prefs.setFriendsHintSeen() }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendSos() {
        if (!fineOk) { vm.toast("Izin lokasi diperlukan untuk SOS"); return }
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) vm.sendSos(loc.latitude, loc.longitude)
                else vm.toast("Lokasi belum tersedia. Coba lagi di tempat terbuka.")
            }
            .addOnFailureListener { vm.toast("Gagal mengambil lokasi untuk SOS") }
    }

    if (sosPrompt) AlertDialog(
        onDismissRequest = { vm.sosPrompt.value = false },
        title = { Text("Kirim SOS?") },
        text = { Text("Semua teman yang Anda setujui akan menerima peringatan darurat beserta lokasi Anda saat ini, walau berbagi lokasi sedang dimatikan.") },
        confirmButton = {
            TextButton(onClick = { vm.sosPrompt.value = false; sendSos() }) {
                Text("Kirim SOS", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { vm.sosPrompt.value = false }) { Text("Batal") } },
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.MAP -> Icons.Default.Place
                                    Tab.FRIENDS -> Icons.Default.Group
                                    Tab.HISTORY -> Icons.Default.History
                                    Tab.PLACES -> Icons.Default.Home
                                    Tab.PROFILE -> Icons.Default.Person
                                }, t.label
                            )
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = Green,
                            selectedTextColor = Green,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.MAP ->
                    if (followUid != null) FollowScreen(friends, followUid, onPick = vm::follow)
                    else MapScreen(friends, onFollow = { vm.follow(it.uid) }, onOpenGuide = { guideOpen = true }, onSos = { vm.sosPrompt.value = true })
                Tab.FRIENDS -> {
                    val chatFriend = friends.firstOrNull { it.uid == chatWith && it.status == "accepted" }
                    if (chatFriend != null) {
                        ChatScreen(
                            friend = chatFriend,
                            myUid = me?.uid ?: "",
                            messages = messages,
                            onSendText = vm::sendText,
                            onSendVoice = vm::sendVoice,
                            onPing = vm::sendPing,
                            onBack = { vm.openChat(null) },
                            onMessage = vm::toast,
                        )
                    } else {
                        FriendsScreen(
                            me, friends,
                            onInvite = vm::invite, onAccept = vm::accept, onRemove = vm::removeFriend,
                            onFollow = { vm.follow(it.uid); tab = Tab.MAP },
                            onFavorite = vm::setFavorite, onGroup = vm::setGroup,
                            onChat = { vm.openChat(it.uid) }, onPing = vm::pingFriend,
                        )
                    }
                }
                Tab.HISTORY -> HistoryScreen(friends, trips, historyUid, onSelect = vm::viewHistoryOf)
                Tab.PLACES -> PlacesScreen(
                    places,
                    onAdd = vm::addPlace, onDelete = vm::deletePlace, onMessage = vm::toast,
                )
                Tab.PROFILE -> ProfileScreen(
                    me,
                    onSetSharing = { on, ms -> vm.setSharing(on, ms) },
                    onOpenGuide = { guideOpen = true },
                    onSignOut = { LocationService.stop(context); vm.signOut() },
                )
            }
        }
    }
}
