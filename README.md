# Nexus Auction Android — LAN Auto Discovery

Project Android Studio/Gradle yang siap diunggah ke GitHub dan dibangun oleh GitHub Actions tanpa Android Studio di VPS.

## Fitur
- Single Player offline
- Multiplayer LAN HP-ke-HP
- LAN room auto discovery melalui Android NSD
- Local signaling untuk koneksi WebRTC
- Host/Join tanpa copy-paste offer/answer pada jalur otomatis
- Fallback WebRTC manual tetap dipertahankan di game
- Maksimal 5 pemain
- Slot manusia yang kosong dapat diisi bot
- Kota 1, Kota 2, Kota 3 dengan sistem x2 per ronde
- Bid lock; bid yang tidak dikunci menjadi 0
- Profit/overbid
- Reveal item, simbol tipe, warna kualitas, ukuran fisik
- Figure 2x3 memanjang ke bawah
- Bot R1-R4 tidak akurat terhadap total nilai; R5 memakai estimasi yang dapat untung/rugi

## Build di GitHub tanpa Android Studio
1. Buat repository GitHub bernama `Nexus-Auction-Android`.
2. Upload SELURUH isi folder project ini, termasuk `.github/workflows/android.yml`.
3. Pastikan `app/`, `build.gradle`, `settings.gradle`, `gradle.properties`, dan `.github/` berada di root repository.
4. Buka tab **Actions**.
5. Pilih workflow **Build Nexus Auction APK**.
6. Tekan **Run workflow**.
7. Setelah selesai, buka hasil workflow dan download artifact **Nexus-Auction-debug-apk**.

## Struktur
```
Nexus-Auction-Android/
├── .github/
│   └── workflows/
│       └── android.yml
├── app/
│   ├── build.gradle
│   └── src/main/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── .gitignore
└── README.md
```
