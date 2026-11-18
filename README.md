<div align="center">
  <img src="app/src/main/res/drawable/logo_mashimurelay.png" alt="Mashimurelay Logo" width="140" />

  <h1>Mashimurelay Android</h1>
  <p><strong>Pure Kotlin Native Industrial Electromechanical Relay & Motor Control CAD Simulator</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android_Native_Kotlin-3ddc84?style=flat-square" alt="Android Native Kotlin" />
    <img src="https://img.shields.io/badge/Standard-IEC_60617-1e293b?style=flat-square" alt="IEC 60617 Standard" />
    <img src="https://img.shields.io/badge/Components-117_Industrial_Symbols-0f766e?style=flat-square" alt="117 Components" />
    <img src="https://img.shields.io/badge/Min_SDK-24_(Android_7.0+)-475569?style=flat-square" alt="Min SDK 24" />
    <img src="https://img.shields.io/badge/Target_SDK-35_(Android_15)-16a34a?style=flat-square" alt="Target SDK 35" />
    <img src="https://img.shields.io/badge/Render-Hardware_Accelerated_Canvas-0284c7?style=flat-square" alt="Hardware Canvas" />
    <img src="https://img.shields.io/badge/Architecture-Discrete_Event_Equipotential-334155?style=flat-square" alt="Equipotential Solver" />
    <img src="https://img.shields.io/badge/Display-Adaptive_Edge_to_Edge-6366f1?style=flat-square" alt="Edge to Edge" />
  </p>
</div>

---

## Ringkasan Proyek (Overview)

**Mashimurelay Android** (`com.mashimurelay.app`) adalah aplikasi Computer-Aided Design (CAD) dan simulator interaktif waktu nyata (*real-time*) untuk perancangan rangkaian kendali relay elektromekanikal serta instalasi tenaga motor listrik industri berstandar **IEC 60617**.

Dibangun **100% menggunakan bahasa Kotlin Native** tanpa ketergantungan pada runtime WebView, Mashimurelay Android mengeksekusi mesin kalkulasi rangkaian langsung di atas *Android Runtime* (ART) dengan akselerasi perangkat keras GPU melalui `android.graphics.Canvas`. Aplikasi ini dirancang untuk memberikan kinerja sentuh responsif pada laju 60–120 FPS serta konsumsi memori (RAM) yang sangat efisien untuk kebutuhan pendidikan vokasi, laboratorium teknik elektro/ketenagalistrikan, dan pelatihan otomasi industri.

---

## Arsitektur Teknis & Fitur Unggulan

### 1. 100% Pure Kotlin Native Canvas Engine (Zero WebView)
- **Direct GPU-Accelerated Pipeline**: Seluruh proses rendering kisi-kisi (*grid*), komponen vektor SVG, teks anotasi CAD, dan animasi kawat berarus (*marching ants*) digambar langsung menggunakan primitif `android.graphics.Canvas` dan pustaka `AndroidSVG`.
- **Double-Buffering & Matrix Transformations**: Sistem navigasi kanvas mendukung pergeseran dua jari (*pan*), cubitan pembesaran (*pinch-to-zoom*), dan *touch-point snapping* dengan presisi grid 20px.
- **Orthogonal Wire Router**: Algoritma perutean kawat ortogonal cerdas dengan pembuatan titik belok siku-siku (*manhattan routing*), pendeteksian simpul percabangan (*junction nodes*), dan validasi koneksi pin otomatis.

### 2. Discrete-Event Equipotential Circuit Solver
- **Multi-Rail Bitmask Propagation**: Melacak distribusi potensial fasa dan polaritas daya (`L1`, `L2`, `L3`, `N`, `PE`, `DC+`, `DC-`) secara simultan menggunakan penelusuran graf konduktif *Breadth-First Search* (BFS).
- **Series & Parallel Load Resolution**: Menyelesaikan pembagian tegangan pada beban pasif 2-terminal yang disusun secara seri maupun paralel melalui penelusuran *Depth-First Search* (DFS), lengkap dengan pemodelan konduksi dioda semikonduktor.
- **Fixed-Point Equilibrium Convergence**: Mengevaluasi interaksi tertutup antara koil kontaktor, relay tunda waktu (*timer*), kontak bantu (*auxiliary contacts*), serta mekanisme *interlock* mekanis hingga mencapai titik stabil dalam siklus frame deterministik.
- **Fault & Overload Protection**: Mendeteksi hubung singkat (*short circuit*) antar-fasa atau fasa-ke-netral, kelebihan beban DC, karakteristik peleburan sekring (*fuse*), serta simulasi *trip* pada *Thermal Overload Relay*.

### 3. Adaptive Edge-to-Edge & Cutout System
- **Display Cutout Adaptation**: Mengimplementasikan `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` dengan integrasi `WindowInsetsCompat.Type.displayCutout()` untuk mendukung layar penuh menembus poni kamera (*waterdrop notch*) maupun lubang kamera (*punch hole*).
- **Dynamic Inset Padding**: Menghitung jarak aman (*safe insets*) secara otomatis pada bilah menu atas (*AppBarLayout*), laci navigasi, panel pustaka, dan tombol melayang (*floating widgets*) baik pada orientasi tegak (*portrait*) maupun mendatar (*landscape*).
- **OEM Notch Compatibility**: Konfigurasi khusus untuk Xiaomi MIUI / HyperOS (`notch.config`) dan Huawei EMUI (`android.notch_support`) untuk mencegah pemotongan bilah hitam (*letterboxing*).

### 4. Circuit State Snapshot & Zero-False-Positive Save Engine
- **Deterministic Hashing**: Algoritma verifikasi status perubahan (*dirty-state snapshot*) yang membandingkan struktur elemen, kawat, rotasi, dan teks secara presisi. Dialog konfirmasi simpan hanya muncul jika pengguna benar-benar melakukan perubahan pada lembar kerja.

---

## Pustaka Komponen Standar IEC 60617 (117 Simbol Industri)

Seluruh simbol komponen dirender dalam format vektor SVG tajam dan terbagi ke dalam 9 kategori standar:

| Kategori | Cakupan Komponen Utama |
| :--- | :--- |
| **Sources** | Suplai Fasa Tunggal (`L1`), 3-Fasa (`L1, L2, L3`, `3P`, `3P+N`), Netral (`N`), Ground Proteksi (`PE`), Suplai DC (`+`, `-`, Battery), Power Supply AC/DC (1P & 3P), Generator 3-Fasa (Manual & Auto Control `C1-C2`). |
| **Protections** | MCB / Circuit Breaker (1P, 2P, 3P, 4P), Thermal-Magnetic Breaker, Motor Protection Circuit Breaker (MPCB), RCCB, RCBO, Fuse (1P, 2P, 3P + N), Fuse Disconnector, Isolator Switch, Thermal Overload Relay (TOR) 1P–3P dengan tombol *Test-Trip*. |
| **Switches** | Saklar Manual (`NO`, `NC`, `CON`), Saklar Selektor 3-Posisi (`I-0-II`, `I-II-III`), Pushbutton (`NO`, `NC`, `CON`, `NONC`, `Crossover`), Emergency Stop (`NO`, `NC`, `CON`, `NONC`), Limit Switch, Roller Switch, Foot Pedal, Key Switch, Pressure, Temperature, Float, Flow, & Proximity Sensor. |
| **Contactors** | Kontaktor Daya Utama `NO` dan `NC` (1P hingga 4P) yang tersinkronisasi otomatis dengan koil kendali (`-KM`). |
| **Coils & Timers** | Koil Kontaktor Standar, Latching Relay, Solenoid, On-Delay Timer (`KT`), Off-Delay Timer, On-Off Delay Timer, Flasher/Blinker Relay, dan Phase Failure Relay (`PFR`). |
| **Auxiliaries** | Kontak Bantu Instan (`NO`, `NC`, `CON`, `NONC`), Kontak Bantu Tunda Waktu (*On-Delay*, *Off-Delay*, *On-Off Delay*), serta Kontak Bantu *Thermal Overload* (`95-96` NC & `97-98` NO). |
| **Motors** | Motor Induksi 3-Fasa (`U1, V1, W1`), Motor 3-Fasa 6-Terminal *Star-Delta* (`U1..W2`), Motor Dahlander 2-Kecepatan, Motor 2-Belitan Terpisah, Motor Induksi 1-Fasa, dan Motor DC (dengan indikator putaran CW/CCW). |
| **Loads** | Lampu Indikator Multi-Warna, Blinking Lamp, Buzzer, Siren, dan Industrial Bell dengan sintesis audio waktu nyata. |
| **Others** | Terminal Block, Junction Node, Cross-Reference Arrows (`arrowSource` & `arrowDestination` dengan penomoran otomatis `-REF`), Dioda Penyearah, dan Anotasi Teks CAD. |

---

## Fitur Pedagogis & Produktivitas Mobile

- **Floating Action Pill**: Bilah kontrol mengambang yang muncul otomatis saat komponen disentuh, menyediakan pengubahan status saklar waktu nyata (`OFF / ON`, `Pos 1 / Pos 0 / Pos 2`, *Test-Trip* TOR), pemutaran 90 derajat (*Rotate*), penyesuaian tag/properti, dan penghapusan instan.
- **Visual Component Library Side Drawer**: Panel pemilihan komponen berbasis grid visual 3-kolom yang dilengkapi filter kategori, pencarian cepat, dan pratinjau simbol SVG asli.
- **Teacher Lab Mode & Encrypted QR Scanner**: Memungkinkan pengajar menyusun soal praktikum dengan komponen terkunci (*restricted kit*), mengekspor soal ke kode QR terenkripsi, serta memindai lembar tugas siswa menggunakan kamera perangkat.
- **Offline Classroom Exam Hotspot Sync**: Integrasi sinkronisasi lembar praktikum ke server komputer pengajar melalui jaringan Wi-Fi lokal (*Local Hotspot*) tanpa memerlukan koneksi internet publik.
- **Native Vector PDF Report Generator**: Ekspor skematik rangkaian dan tabel daftar komponen (*Bill of Materials*) ke format dokumen PDF berkualitas cetak langsung dari perangkat HP.
- **Built-in Industrial Templates**: Lembar kerja siap pakai untuk studi kasus industri standar:
  - Direct On-Line (DOL) Motor Starter
  - Forward-Reverse Interlocked Starter
  - Automatic Star-Delta Starter

---

## Ekosistem Lintas Platform

Format berkas proyek Mashimurelay (`.smrl` / `.srelay`) kompatibel secara penuh antar-platform:

1. **Mashimurelay Android (`100% Pure Kotlin Native`)** — Repositori ini.
2. **Mashimurelay Web (`PWA & WebAssembly`)** — Akses daring: [https://mashimurelay.vercel.app/](https://mashimurelay.vercel.app/)
3. **Mashimurelay Windows (`Rust Tauri v2 Desktop`)** — Edisi desktop mandiri berkinerja tinggi (`Mashimurelay.exe`).

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
│       │   └── nativecad/
│       │       ├── NativeCanvasView.kt    # Mesin kanvas GPU & gesture controller
│       │       ├── EquipotentialEngine.kt # Solver kalkulasi sirkuit waktu nyata
│       │       ├── NativeComponentRegistry.kt # Pendaftaran geometri & pin 117 simbol
│       │       ├── SvgComponentRenderer.kt# Parser & renderer vektor SVG AndroidSVG
│       │       ├── WorkspaceManager.kt    # Pengelola penyimpanan proyek (.smrl)
│       │       └── NativePdfReportGenerator.kt # Generator laporan teknis PDF
│       ├── res/
│       │   ├── layout/                    # Layout antarmuka XML responsif
│       │   ├── values/                    # Definisi tema dasar & warna
│       │   ├── values-v28/                # Konfigurasi Display Cutout (API 28+)
│       │   └── drawable/                  # Aset grafis & ikon logo aplikasi
│       └── assets/
│           ├── components.json            # Basis data topologi pin & simbol IEC
│           └── assets/icons/              # Berkas vektor SVG 117 simbol komponen
├── gradle/wrapper/                        # Gradle Wrapper binari & konfigurasi
├── build.gradle.kts                       # Konfigurasi Gradle tingkat proyek
├── settings.gradle.kts                    # Pengaturan modul Gradle
├── gradlew.bat                            # Skrip eksekusi Gradle Wrapper (Windows)
├── gradlew                                # Skrip eksekusi Gradle Wrapper (Unix/macOS)
├── build-apk.bat                          # Skrip otomatisasi build satu klik
├── .gitignore                             # Filter pengabaian berkas kompilasi & cache
└── README.md                              # Dokumentasi teknis repositori
```

---

## Panduan Kompilasi & Pemasangan

### Kebutuhan Lingkungan Pengembangan (Prerequisites)
- **Java Development Kit (JDK)**: Versi 17 atau lebih baru (`JAVA_HOME`).
- **Android SDK**: Platform SDK 35 (Android 15), Build-Tools 36.1.0 atau 35.0.0.
- **Gradle**: Versi 8.9+ (sudah terintegrasi via Gradle Wrapper).

### 1. Kompilasi Debug APK
Jalankan perintah berikut pada terminal PowerShell / CMD di direktori root repositori:

```powershell
.\gradlew.bat assembleDebug
```

Berkas keluaran biner APK akan dihasilkan pada:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Kompilasi Release APK (Tersign & Teroptimasi R8)
```powershell
.\gradlew.bat assembleRelease
```

### 3. Pemasangan Langsung ke Perangkat Fisik (via ADB)
Hubungkan perangkat Android dengan mode *USB Debugging* aktif, lalu jalankan:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Hak Cipta & Lisensi

Hak Cipta (c) 2026 **Proyek Rekayasa Mashimurelay**. Seluruh hak dilindungi undang-undang.  
Diteliti, dirancang, dan direkayasa untuk kemajuan pendidikan vokasi ketenagalistrikan, standardisasi instalasi industri, dan teknologi simulator teknik.
