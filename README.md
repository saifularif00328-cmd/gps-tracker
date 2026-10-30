# GPS-Tracker by Saiful Arif

Aplikasi Android (Kotlin + Jetpack Compose) untuk melihat lokasi teman secara real-time: **bergerak** (bisa diikuti) atau **diam** (lama diam). Memakai layanan gratis: Firebase Spark (Auth + Firestore) dan peta OpenStreetMap (tanpa API key).

## Menjalankan
1. Buat proyek di https://console.firebase.google.com (paket Spark/gratis).
2. Tambah aplikasi Android dengan package `com.saifularif.gpstracker`, unduh `google-services.json`, simpan di `app/google-services.json` (file ini di-ignore git).
3. Aktifkan **Authentication > Email/Password** dan buat **Firestore Database**.
4. Salin isi `firestore.rules` ke tab Rules di Firestore lalu Publish.
5. Buka folder ini di Android Studio (Koala atau lebih baru), Sync, lalu Run di HP/emulator (minSdk 26).

## Fitur MVP
Splash, onboarding, login/daftar, peta real-time dengan status Bergerak/Diam/Offline, mode Ikuti (jejak rute), undang teman lewat kode/username dengan persetujuan, berbagi lokasi ON/OFF + jeda 1/8 jam, foreground service dengan notifikasi permanen, mulai otomatis setelah reboot, popup petunjuk pemakaian.

## Catatan
- Lokasi dikirim adaptif: bergerak ±5 dtk (min 10 m), diam ±60 dtk, agar kuota gratis Firestore aman.
- QR code, riwayat, geofence, grup, pesan/panggilan suara ada di Tahap 2-3.
