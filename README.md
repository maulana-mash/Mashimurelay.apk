<p align="center">
  <a href="https://github.com/maulana-mash/Mashimurelay.apk">
    <img src="app/src/main/res/drawable/logo_mashimurelay.png" alt="Logo Mashimurelay Android" width="140" />
  </a>
</p>

<h1 align="center">Mashimurelay Android</h1>

<p align="center">
  <strong>Simulator CAD Kontrol Relay Elektromekanikal &amp; Motor Industri Berstandar IEC 60617 (100% Pure Kotlin Native)</strong><br />
  Aplikasi Android native murni tanpa WebView dengan akselerasi GPU Canvas 60–120 FPS, 117 simbol vektor industri, mesin solver sirkuit ekuipotensial, dan sinkronisasi tugas kelas offline via Hotspot LAN.
</p>

<p align="center">
  <a href="https://github.com/maulana-mash/Mashimurelay.apk/releases/latest">
    <img src="https://img.shields.io/badge/UNDUH%20APK-RILIS%20TERBARU-1e293b?style=for-the-badge&logo=android&logoColor=white&labelColor=334155" alt="Unduh APK" />
  </a>
  <img src="https://img.shields.io/badge/KOTLIN-PURE%20NATIVE-1e293b?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=334155" alt="Pure Kotlin Native" />
  <img src="https://img.shields.io/badge/STANDAR-IEC%2060617-1e293b?style=for-the-badge&labelColor=334155" alt="IEC 60617" />
  <img src="https://img.shields.io/badge/KOMPONEN-117%20SIMBOL-1e293b?style=for-the-badge&labelColor=334155" alt="117 Komponen" />
  <img src="https://img.shields.io/badge/ANDROID-API%2024%E2%80%9335-1e293b?style=for-the-badge&labelColor=334155" alt="Android API 24-35" />
</p>

<p align="center">
  <a href="#ringkasan-proyek">Ringkasan</a> &bull;
  <a href="#arsitektur-teknis--fitur-unggulan">Fitur Unggulan</a> &bull;
  <a href="#pustaka-komponen-standar-iec-60617-117-simbol-industri">117 Komponen</a> &bull;
  <a href="#ekosistem-lintas-platform">Ekosistem</a> &bull;
  <a href="#panduan-kompilasi--pemasangan">Kompilasi</a> &bull;
  <a href="#hak-cipta--lisensi">Lisensi</a>
</p>

---

## Ringkasan Proyek

**Mashimurelay Android** (`com.mashimurelay.app`) adalah aplikasi Computer-Aided Design (CAD) dan simulator interaktif waktu nyata (*real-time*) untuk perancangan rangkaian kendali relay elektromekanikal serta instalasi tenaga motor listrik industri berstandar **IEC 60617**.

Dibangun **100% menggunakan bahasa Kotlin Native** tanpa ketergantungan pada runtime WebView, Mashimurelay Android mengeksekusi mesin kalkulasi rangkaian langsung di atas *Android Runtime* (ART) dengan akselerasi perangkat keras GPU melalui `android.graphics.Canvas`. Aplikasi ini dirancang untuk kinerja sentuh responsif 60–120 FPS serta konsumsi RAM efisien bagi pendidikan vokasi, laboratorium teknik elektro/ketenagalistrikan, dan pelatihan otomasi industri.

---

## Arsitektur Teknis & Fitur Unggulan

### 1. 100% Pure Kotlin Native Canvas Engine (Zero WebView)
- **Direct GPU-Accelerated Pipeline:** Seluruh proses rendering kisi-kisi (*grid*), komponen vektor SVG, teks anotasi CAD, dan animasi kawat berarus (*marching ants*) digambar langsung menggunakan primitif `android.graphics.Canvas` dan pustaka `AndroidSVG`.
- **Double-Buffering & Matrix Transformations:** Mendukung pergeseran dua jari (*pan*), cubitan pembesaran (*pinch-to-zoom*), dan *touch-point snapping* dengan presisi grid 20px.
- **Orthogonal Wire Router:** Algoritma perutean kawat ortogonal (*manhattan routing*), deteksi simpul percabangan (*junction nodes*), dan validasi koneksi pin otomatis.

### 2. Discrete-Event Equipotential Circuit Solver
- **Multi-Rail Bitmask Propagation:** Melacak distribusi potensial fasa dan polaritas daya (`L1`, `L2`, `L3`, `N`, `PE`, `DC+`, `DC-`) secara simultan menggunakan *Breadth-First Search* (BFS).
- **Series & Parallel Load Resolution:** Menyelesaikan pembagian tegangan pada beban pasif 2-terminal seri maupun paralel melalui *Depth-First Search* (DFS), lengkap dengan pemodelan konduksi dioda semikonduktor.
- **Fixed-Point Equilibrium Convergence:** Mengevaluasi interaksi tertutup antara koil kontaktor, relay tunda waktu (*timer*), kontak bantu (*auxiliary contacts*), serta *interlock* mekanis hingga mencapai titik stabil deterministik.
- **Fault & Overload Protection:** Mendeteksi hubung singkat (*short circuit*) antar-fasa atau fasa-ke-netral, kelebihan beban DC, peleburan sekring (*fuse*), serta simulasi *trip* pada *Thermal Overload Relay*.

### 3. Adaptive Edge-to-Edge & Cutout System
- **Display Cutout Adaptation:** Mengimplementasikan `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` dan `WindowInsetsCompat.Type.displayCutout()` untuk tampilan penuh menembus poni kamera (*notch*) maupun lubang kamera (*punch hole*).
- **Dynamic Inset Padding & OEM Compatibility:** Menyesuaikan jarak aman bilah kontrol pada orientasi *portrait* maupun *landscape*, termasuk dukungan khusus Xiaomi MIUI/HyperOS (`notch.config`) dan Huawei EMUI.

### 4. Fitur Pedagogis & Produktivitas Mobile
- **Floating Action Pill:** Kontrol cepat saat komponen disentuh (ubah status saklar, *Test-Trip* TOR, putar 90°, edit properti, hapus instan).
- **Visual Component Library Side Drawer:** Grid visual 3-kolom dilengkapi filter kategori, pencarian cepat, dan pratinjau simbol SVG asli.
- **Teacher Lab Mode, Encrypted QR Scanner & Offline Hotspot Sync:** Pemindaian soal praktikum terenkripsi dan pengiriman lembar tugas ke server komputer guru melalui Wi-Fi Hotspot lokal tanpa internet publik.
- **Native Vector PDF Report Generator:** Ekspor skematik rangkaian dan daftar komponen (*Bill of Materials*) ke dokumen PDF siap cetak langsung dari ponsel.
- **Built-in Industrial Templates:** Template siap uji untuk *Direct On-Line (DOL) Starter*, *Forward-Reverse Interlocked Starter*, dan *Automatic Star-Delta Starter*.

---

## Pustaka Komponen Standar IEC 60617 (117 Simbol Industri)

| Kategori | Cakupan Komponen Utama |
| :--- | :--- |
| **Sources** | Suplai Fasa Tunggal (`L1`), 3-Fasa (`L1, L2, L3`, `3P`, `3P+N`), Netral (`N`), Ground Proteksi (`PE`), Suplai DC (`+`, `-`, Battery), Power Supply AC/DC (1P & 3P), Generator 3-Fasa (Manual & Auto Control `C1-C2`). |
| **Protections** | MCB / Circuit Breaker (1P–4P), Thermal-Magnetic Breaker, MPCB, RCCB, RCBO, Fuse (1P–3P + N), Fuse Disconnector, Isolator Switch, Thermal Overload Relay (TOR) 1P–3P dengan tombol *Test-Trip*. |
| **Switches** | Saklar Manual (`NO`, `NC`, `CON`), Saklar Selektor 3-Posisi (`I-0-II`, `I-II-III`), Pushbutton (`NO`, `NC`, `CON`, `NONC`, `Crossover`), Emergency Stop, Limit, Roller, Foot Pedal, Key, Pressure, Temperature, Float, Flow, & Proximity Sensor. |
| **Contactors** | Kontaktor Daya Utama `NO` dan `NC` (1P hingga 4P) yang tersinkronisasi otomatis dengan koil kendali (`-KM`). |
| **Coils & Timers** | Koil Kontaktor Standar, Latching Relay, Solenoid, On-Delay Timer (`KT`), Off-Delay Timer, On-Off Delay Timer, Flasher/Blinker Relay, dan Phase Failure Relay (`PFR`). |
| **Auxiliaries** | Kontak Bantu Instan (`NO`, `NC`, `CON`, `NONC`), Kontak Bantu Tunda Waktu (*On-Delay*, *Off-Delay*, *On-Off Delay*), serta Kontak Bantu *Thermal Overload* (`95-96` NC & `97-98` NO). |
| **Motors** | Motor Induksi 3-Fasa (`U1, V1, W1`), Motor 3-Fasa 6-Terminal *Star-Delta* (`U1..W2`), Motor Dahlander 2-Kecepatan, Motor 2-Belitan Terpisah, Motor Induksi 1-Fasa, dan Motor DC (indikator CW/CCW). |
| **Loads** | Lampu Indikator Multi-Warna, Blinking Lamp, Buzzer, Siren, dan Industrial Bell dengan sintesis audio waktu nyata. |
| **Others** | Terminal Block, Junction Node, Cross-Reference Arrows (`arrowSource` & `arrowDestination`), Dioda Penyearah, dan Anotasi Teks CAD. |

---

## Ekosistem Lintas Platform

Format berkas proyek Mashimurelay (`.smrl` / `.srelay`) kompatibel penuh lintas platform:

1. **[Mashimurelay Android](https://github.com/maulana-mash/Mashimurelay.apk)** (`100% Pure Kotlin Native`) — **Repositori ini.**
2. **[Mashimurelay Web](https://github.com/maulana-mash/Mashimurelay.web)** (`PWA & Online Simulator`) — Berjalan di peramban modern dengan dukungan *offline-first* Service Worker.
3. **[Mashimurelay Windows Desktop](https://github.com/maulana-mash/Mashimurelay.exe)** (`Rust Tauri v2 Desktop`) — Stasiun kerja instruktur dengan microserver Hotspot LAN offline dan edisi Portable/NSIS Installer.

---

## Struktur Direktori Repositori

```text
mashimurelay-android/
├── app/
│   ├── build.gradle.kts                   # Konfigurasi dependensi modul aplikasi
│   ├── proguard-rules.pro                 # Aturan optimasi R8 / ProGuard
│   └── src/main/
│       ├── AndroidManifest.xml            # Manifes aplikasi & deklarasi fitur notch
│       ├── java/com/mashimurelay/app/
│       │   ├── MainActivity.kt            # Pengendali utama antarmuka & insets jendela
│       │   ├── PortraitCaptureActivity.kt # Pemindai QR kamera berorientasi portrait
│       │   └── nativecad/                 # NativeCanvasView, EquipotentialEngine, Registry, PDF
│       ├── res/                           # Layout XML, tema, display cutout, & ikon
│       └── assets/                        # components.json & 117 ikon vektor SVG IEC
├── gradle/wrapper/                        # Gradle Wrapper
├── build-apk.bat                          # Skrip otomatisasi build satu klik
└── README.md
```

---

## Panduan Kompilasi & Pemasangan

### Prasyarat
- JDK 17 atau lebih baru (`JAVA_HOME`)
- Android SDK Platform 35 (Android 15)

### Langkah Build & Instalasi ADB
```powershell
git clone https://github.com/maulana-mash/Mashimurelay.apk.git
cd Mashimurelay.apk
.\gradlew.bat assembleDebug      # Output: app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat assembleRelease    # Output rilis teroptimasi R8
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Hak Cipta & Lisensi

Hak Cipta (c) 2026 **Proyek Rekayasa Mashimurelay (Mash Maulana)**. Seluruh hak dilindungi undang-undang.

> **Keterangan Standar & Orisinalitas Aset:** Seluruh 117 simbol skematik kelistrikan mengacu pada standar internasional publik **IEC 60617 / DIN EN 60617** dan dibangun mandiri sebagai berkas vektor SVG *clean-room*. Mesin simulasi *equipotential graph*, proteksi hubung singkat/arus bocor per-jalur, serta sistem audio dan modul praktikum merupakan karya rekayasa mandiri Mashimurelay.
