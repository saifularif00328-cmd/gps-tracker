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

## Tahap 2
Riwayat perjalanan + putar ulang (disimpan 7 hari), Tempat (Rumah/Kantor) dengan notifikasi tiba/pergi dan baterai rendah untuk teman, favorit dan grup teman. Semua tetap gratis (tanpa Cloud Functions): perjalanan diringkas di HP lalu diunggah satu dokumen per perjalanan, notifikasi dibuat lokal dari peristiwa yang didengarkan.
Setelah memperbarui kode, tempel ulang `firestore.rules` di konsol Firebase lalu Publish.

## Tahap 3
Chat teks, tombol Ping (meminta teman memperbarui lokasi), pesan suara (tahan tombol mic, maks 30 detik, disimpan di Firestore), SOS (tombol merah di Peta; teman menerima notifikasi darurat dengan lokasi), dan widget layar utama (status berbagi, teman favorit, SOS, segarkan). Panggilan suara belum dikerjakan.
Setelah memperbarui kode, tempel ulang `firestore.rules` di konsol Firebase lalu Publish. Izinkan mikrofon saat pertama merekam.

## Perbaikan setelah uji di HP
Switch "Bagikan lokasi saya" di tab Teman dan chip di Peta, tombol "Minta aktifkan" untuk teman offline (teman menerima notifikasi Nyalakan/Tidak), ketuk kartu teman untuk langsung mengikuti, dialog izin baterai agar aplikasi tidak mudah dimatikan sistem, dan pesan error yang jelas bila aturan Firestore belum dipublish ("Missing or insufficient permissions").

## Catatan
- Lokasi dikirim adaptif: bergerak ±5 dtk (min 10 m), diam ±60 dtk, agar kuota gratis Firestore aman.
- QR code, riwayat, geofence, grup, pesan/panggilan suara ada di Tahap 2-3.
