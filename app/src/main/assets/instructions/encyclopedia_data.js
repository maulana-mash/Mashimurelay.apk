/**
 * Mashimurelay Component Encyclopedia Technical Knowledge Base
 * Specific, educational, and technically accurate descriptions and operating mechanisms
 * Formulated by Gemini 4 Argon Architecture
 */

window.COMPONENT_ENCYCLOPEDIA_KNOWLEDGE = {
  // === SOURCES & POWER ===
  sourceL1: {
    desc: 'Terminal catu daya fasa L1 (Fasa R / Fasa 1) sistem AC 50/60 Hz.',
    how: 'Menyuplai potensial tegangan bolak-balik 230V terhadap Netral atau 400V terhadap L2/L3 sebagai jalur fasa utama rangkaian kendali dan tenaga.'
  },
  sourceL2: {
    desc: 'Terminal catu daya fasa L2 (Fasa S / Fasa 2) sistem AC 50/60 Hz.',
    how: 'Menyuplai tegangan bolak-balik dengan pergeseran sudut fasa 120 derajat listrik terhadap L1 untuk membentuk sistem tiga fasa simetris.'
  },
  sourceL3: {
    desc: 'Terminal catu daya fasa L3 (Fasa T / Fasa 3) sistem AC 50/60 Hz.',
    how: 'Menyuplai tegangan bolak-balik dengan pergeseran sudut fasa 240 derajat listrik terhadap L1 untuk melengkapi sistem medan putar tiga fasa.'
  },
  sourceL1L2L3: {
    desc: 'Rel terminal gabungan sumber daya 3-Fasa AC (L1-L2-L3) 400V.',
    how: 'Menyediakan koneksi kompak ketiga fasa secara simultan untuk menyuplai sirkuit daya motor induksi dan kontaktor utama.'
  },
  sourceL1L2L3N: {
    desc: 'Rel terminal catu daya 3-Fasa 4-Kawat (L1-L2-L3-N) 400/230V.',
    how: 'Menyuplai tegangan 3-fasa 400V untuk beban tenaga dan tegangan fasa-ke-netral 230V untuk rangkaian instrumen kontrol panel.'
  },
  sourceN: {
    desc: 'Terminal penghantar Netral (N) sistem tenaga listrik AC.',
    how: 'Berfungsi sebagai jalur arus balik sirkuit satu fasa (230V) dan titik bintang netral pada transformator distribusi sekunder.'
  },
  sourcePE: {
    desc: 'Terminal pembumian pengaman (Protective Earth - PE / Ground).',
    how: 'Menghubungkan kerangka logam peralatan ke sistem elektroda tanah untuk membuang arus gangguan dan mencegah tegangan sentuh berbahaya.'
  },
  sourceGnd: {
    desc: 'Titik referensi pembumian sirkuit (Ground).',
    how: 'Menetapkan potensi nol volt referensi tanah bagi sirkuit elektronika daya, instrumen, dan penangkal gangguan interferensi EMI/RFI.'
  },
  sourcePlus: {
    desc: 'Terminal positif catu daya arus searah (+24V / +12V DC).',
    how: 'Mengalirkan arus listrik DC polaritas positif konstan untuk menyuplai relai bantu, sensor industri, PLC, dan komponen semikonduktor.'
  },
  sourceNegative: {
    desc: 'Terminal negatif catu daya arus searah (0V / -DC).',
    how: 'Berfungsi sebagai rel referensi nol volt atau kutub negatif tempat kembalinya arus rangkaian kontrol arus searah.'
  },
  battery: {
    desc: 'Sumber energi elektrokimia baterai akumulator DC (12V / 24V).',
    how: 'Mengubah energi kimia internal menjadi energi listrik DC stabil untuk sistem daya cadangan darurat (UPS) dan kontrol tripping pemutus beban.'
  },
  powerSupply: {
    desc: 'Unit catu daya teregulasi switching (SMPS) input AC ke output DC.',
    how: 'Menyearahkan tegangan AC 230V dan menurunkannya melalui switching frekuensi tinggi menjadi tegangan DC 24V stabil dengan isolasi galvanis.'
  },
  powerSupply2: {
    desc: 'Catu daya industri redundan (Dual Output DC Power Supply).',
    how: 'Menghasilkan suplai DC teregulasi ganda dengan proteksi pembatas arus hubung singkat dan penstabil fluktuasi beban kontrol.'
  },
  generator3ph: {
    desc: 'Generator sinkron listrik 3-Fasa AC (Genset Cadangan Otomatis).',
    how: 'Mengubah energi mekanik putaran mesin penggerak menjadi energi listrik 3-fasa 400V lengkap dengan terminal sinyal otomatisasi start (C1-C2).'
  },

  // === PROTECTIONS ===
  breaker1p: {
    desc: 'Miniature Circuit Breaker (MCB) 1-Kutub untuk proteksi fasa tunggal.',
    how: 'Mendeteksi beban lebih melalui pembengkokan strip bimetal dan memutus seketika (<10ms) via koil magnetik saat terjadi korsleting.',
    simVideo: 'short_circuit'
  },
  breaker2p: {
    desc: 'MCB 2-Kutub untuk proteksi simultan 2-Kawat (Fasa + Netral atau DC 2-Kutub).',
    how: 'Memutus kedua kutub secara mekanis serentak ketika salah satu kutub mendeteksi arus lebih atau arus hubung singkat.',
    simVideo: 'short_circuit'
  },
  breaker3p: {
    desc: 'MCB 3-Kutub untuk proteksi saluran tenaga 3-Fasa AC.',
    how: 'Mengamankan ketiga jalur fasa L1-L2-L3 secara simultan untuk mencegah kerusakan fatal akibat hubung singkat antar-fasa pada motor.',
    simVideo: 'short_circuit'
  },
  breaker4p: {
    desc: 'MCB 4-Kutub (L1-L2-L3-N) untuk proteksi total instalasi 3-fasa 4-kawat.',
    how: 'Memutus ketiga fasa dan penghantar netral secara bersamaan untuk mencegah pergeseran titik netral saat isolasi perawatan sistem.',
    simVideo: 'short_circuit'
  },
  breaker: {
    desc: 'Motor Protection Circuit Breaker (MPCB / Manual Motor Starter).',
    how: 'Mengintegrasikan pengaman arus lebih termal terkalibrasi khusus FLA motor dan trip magnetik instan 13x In untuk beban rotor terkunci.',
    simVideo: 'dol_starter'
  },
  motorBreaker1p: {
    desc: 'Motor Protection Breaker 1-Kutub untuk motor kecil fasa tunggal.',
    how: 'Memproteksi motor 1-fasa dari arus start macet dan arus lebih kontinu menggunakan mekanisme trip bimetal kelas 10.',
    simVideo: 'short_circuit'
  },
  motorBreaker2p: {
    desc: 'Motor Protection Breaker 2-Kutub untuk motor 1-fasa 230V.',
    how: 'Memutus fasa dan netral serempak dengan kompensasi suhu sekitar untuk melindungi motor penggerak dari panas berlebih.',
    simVideo: 'short_circuit'
  },
  motorBreaker3p: {
    desc: 'Motor Protection Breaker 3-Kutub industri (MPCB 3P).',
    how: 'Dilengkapi tuas putar manual, pengaman kehilangan fasa diferensial, serta kontak trip bantu untuk mengamankan motor 3-fasa.',
    simVideo: 'dol_starter'
  },
  motorBreaker4p: {
    desc: 'Motor Protection Breaker 4-Kutub untuk instalasi motor khusus.',
    how: 'Memberikan proteksi komprehensif pada ketiga fasa daya dan netral terhadap arus berlebih serta gangguan hubung singkat.',
    simVideo: 'short_circuit'
  },
  tmBreaker1p: {
    desc: 'Thermal-Magnetic Circuit Breaker 1P berdaya pemutus tinggi.',
    how: 'Menggunakan bimetal untuk respon lambat beban lebih dan elektromagnet plunger untuk trip super cepat saat hubung singkat.',
    simVideo: 'short_circuit'
  },
  tmBreaker2p: {
    desc: 'Thermal-Magnetic Circuit Breaker 2P.',
    how: 'Memberikan proteksi ganda termal dan magnetik pada 2 kutub dengan ruang pemadam busur api listrik (arc chutes) terpadu.',
    simVideo: 'short_circuit'
  },
  tmBreaker3p: {
    desc: 'Thermal-Magnetic Circuit Breaker 3P (MCCB 3-Kutub).',
    how: 'Dirancang memutus arus hubung singkat puluhan kiloampere (kA) pada feeder utama panel distribusi motor industri.',
    simVideo: 'short_circuit'
  },
  tmBreaker4p: {
    desc: 'Thermal-Magnetic Circuit Breaker 4P (MCCB 4-Kutub).',
    how: 'Melindungi seluruh sistem feeder 3-fasa beserta netral dari arus beban lebih dan arus hubung tanah masif.',
    simVideo: 'short_circuit'
  },
  rcboBreaker2p: {
    desc: 'Residual Current Breaker with Overcurrent Protection (RCBO) 2P.',
    how: 'Mendeteksi arus bocor ke tanah (>30mA) via trafo arus toroidal sekaligus memproteksi beban lebih dan hubung singkat dalam satu unit kompak.'
  },
  rcboBreaker4p: {
    desc: 'RCBO 4-Kutub untuk sistem tenaga 3-Fasa dan Netral.',
    how: 'Memutus keempat saluran seketika bila terjadi sengatan listrik pada manusia atau kebocoran isolasi kabel ke rangka ground mesin.'
  },
  rccbBreaker2p: {
    desc: 'Residual Current Circuit Breaker (RCCB / ELCB) 2-Kutub.',
    how: 'Murni mendeteksi arus sisa bocor tanah (differential residual current) tanpa proteksi arus lebih; wajib diseri dengan MCB.'
  },
  rccbBreaker4p: {
    desc: 'RCCB / ELCB 4-Kutub untuk sistem distribusi 3-fasa.',
    how: 'Membandingkan jumlah vektor arus L1+L2+L3+N. Jika selisihnya tidak nol (>30mA), mekanisme mekanis langsung membuka seluruh kutub.'
  },
  overload1p: {
    desc: 'Thermal Overload Relay (TOR) 1-Kutub.',
    how: 'Strip bimetal melengkung perlahan akibat panas arus lebih, mendorong tuas mekanik untuk membuka kontak kontrol NC pada sirkuit koil.',
    simVideo: 'thermal_overload'
  },
  overload2p: {
    desc: 'Thermal Overload Relay (TOR) 2-Kutub.',
    how: 'Memantau arus pada 2 fasa untuk melindungi motor 1-fasa atau DC dari kenaikan temperatur isolasi yang merusak belitan.',
    simVideo: 'thermal_overload'
  },
  overload3p: {
    desc: 'Thermal Overload Relay (TOR) 3-Kutub Bimetal Motor (-F1).',
    how: 'Dipasang langsung di bawah kontaktor utama; bimetal mendeteksi arus lebih kontinu 3-fasa, membuka kontak NC 95-96 untuk mematikan koil kontaktor.',
    simVideo: 'thermal_overload'
  },
  phaseFailure: {
    desc: 'Phase Failure & Phase Asymmetry Relay (Relai Pengaman Hilang Fasa).',
    how: 'Memonitor kesimetrisan tegangan L1-L2-L3; jika ada fasa yang putus (single-phasing) atau urutan terbalik, kontak relai membuka untuk mencegah motor terbakar.'
  },
  fuse1p: {
    desc: 'Sekring pelebur (Fuse) 1-Kutub tipe tabung kaca / HRC.',
    how: 'Kawat perak berkalibrasi melebur akibat efek Joule panas ekstrem saat arus hubung singkat melampaui rating, memutuskan sirkuit permanen.'
  },
  fuse2p: {
    desc: 'Sekring lebur 2-Kutub.',
    how: 'Dua elemen lebur terpisah untuk mengamankan kutub positif-negatif catu daya DC atau fasa-netral kontrol instrumen.'
  },
  fuse3p: {
    desc: 'Sekring lebur HRC 3-Kutub untuk proteksi cadangan feeder motor.',
    how: 'Kapasitas pemutus sangat tinggi (hingga 100kA) untuk memadamkan busur api gangguan hubung singkat paling masif.'
  },
  fuse4p: {
    desc: 'Sekring lebur 4-Kutub lengkap dengan proteksi saluran netral.',
    how: 'Mengamankan sirkuit 3-fasa 4-kawat dari lonjakan arus abnormal dengan isolasi termal keramik tahan ledakan.'
  },
  fuseSwitch1p: {
    desc: 'Saklar pemisah bersekring 1-Kutub (Fuse Switch Disconnector).',
    how: 'Menggabungkan fungsionalitas pengaman lebur dengan engsel tuas yang dapat ditarik manual untuk isolasi sirkuit berbeban.'
  },
  fuseSwitch1pn: {
    desc: 'Saklar bersekring 1P+N dengan kutub netral tanpa sekring.',
    how: 'Memutus fasa bersekring dan menghubungkan netral secara simultan untuk keamanan penuh saat penggantian elemen lebur.'
  },
  fuseSwitch2p: {
    desc: 'Saklar pemisah bersekring 2-Kutub.',
    how: 'Tuas ganda membuka kedua kutub sekaligus, memudahkan penggantian sekring tanpa menyentuh terminal aktif bertegangan.'
  },
  fuseSwitch3p: {
    desc: 'Saklar pemisah bersekring 3-Kutub untuk panel tenaga motor.',
    how: 'Ketiga sekring terpasang pada satu pintu tuas engsel; menarik gagang membuka ketiga kontak daya secara simultan.'
  },
  fuseSwitch3pn: {
    desc: 'Saklar bersekring 3P+N untuk panel daya utama.',
    how: 'Menjamin pemutusan ketiga fasa bersekring dan pemutusan netral secara mekanis terkoordinasi.'
  },
  fuseSwitch4p: {
    desc: 'Saklar pemisah bersekring 4-Kutub.',
    how: 'Menyediakan titik isolasi aman untuk sistem daya 4-kawat dengan proteksi lebur pada seluruh penghantar.'
  },
  isolator1p: {
    desc: 'Saklar isolator pemutus beban 1-Kutub (Disconnect Switch).',
    how: 'Memisahkan kontak fisik dengan celah udara terlihat (*visible break gap*) untuk menjamin keamanan teknisi saat perbaikan sirkuit.'
  },
  isolator2p: {
    desc: 'Saklar isolator pemutus beban 2-Kutub.',
    how: 'Membuka dua kutub konduktor secara mekanis kaku tanpa pengaman otomatis; khusus digunakan untuk saklar isolasi manual.'
  },
  isolator3p: {
    desc: 'Saklar isolator 3-Kutub (Main Isolator Switch).',
    how: 'Saklar utama di pintu panel listrik untuk memutus seluruh suplai daya 3-fasa sebelum pintu panel dapat dibuka (door interlock).'
  },
  isolator4p: {
    desc: 'Saklar isolator 4-Kutub (L1-L2-L3-N).',
    how: 'Mengisolasi seluruh penghantar aktif sistem 3-fasa dari trafo atau genset untuk pemeliharaan total instalasi.'
  },

  // === SWITCHES & PUSH BUTTONS ===
  buttonNO: {
    desc: 'Push Button Normally Open (Tombol START Hijau).',
    how: 'Kontak terbuka saat diam; saat ditekan sesaat kontak menutup menyalurkan arus, dan kembali membuka didorong pegas saat dilepas.',
    simVideo: 'dol_starter'
  },
  buttonNC: {
    desc: 'Push Button Normally Closed (Tombol STOP Merah).',
    how: 'Kontak tertutup saat diam; saat ditekan kontak membuka memutus aliran arus kontrol, dan kembali menutup saat dilepas.',
    simVideo: 'dol_starter'
  },
  buttonNONC: {
    desc: 'Push Button Ganda dengan Kontak 1 NO dan 1 NC terpadu.',
    how: 'Menekan satu aktuator tombol memicu dua aksi simultan: membuka cabang kontrol NC dan menutup cabang kontrol NO.'
  },
  buttonCON: {
    desc: 'Push Button Changeover (SPDT Sesaat).',
    how: 'Terminal bersama (Common) terhubung ke kontak NC saat diam, dan beralih ke kontak NO sesaat selama tombol ditekan.'
  },
  buttonCrossover: {
    desc: 'Push Button Crossover (Tombol Balik Arah Sesaat).',
    how: 'Membalik koneksi dua jalur input ke dua jalur output secara simultan selama tombol ditahan.'
  },
  emergencyNC: {
    desc: 'Tombol Darurat Jamur (Emergency Stop NC - E-Stop).',
    how: 'Ditekan saat bahaya seketika membuka kontak NC dan terkunci mekanis (latching); harus diputar searah jarum jam untuk mereset.',
    simVideo: 'dol_starter'
  },
  emergencyNO: {
    desc: 'Emergency Stop dengan kontak NO untuk sinyal alarm.',
    how: 'Ditekan mengunci kontak NO menutup, menyalakan sirine atau lampu darurat untuk memberi tahu operator terjadi kegagalan.'
  },
  emergencyNONC: {
    desc: 'Emergency Stop Ganda 1 NC (Safety Cutoff) + 1 NO (Alarm Signal).',
    how: 'Satu pukulan tombol darurat serempak mematikan sirkuit kontrol motor (NC terbuka) dan menyalakan indikasi visual bahaya (NO menutup).'
  },
  emergencyCON: {
    desc: 'Emergency Stop tipe Changeover (SPDT Latching).',
    how: 'Memindahkan jalur daya dari status normal ke jalur darurat saat ditekan dan mempertahankan posisi hingga di-twist release.'
  },
  switchNO: {
    desc: 'Saklar Selektor Toggle / Rotary 2 Posisi (Normally Open).',
    how: 'Diputar ke posisi ON menutup kontak secara mantap (maintained), dan tetap tertutup sampai diputar kembali manual ke posisi OFF.'
  },
  switchNC: {
    desc: 'Saklar Selektor Toggle / Rotary 2 Posisi (Normally Closed).',
    how: 'Secara default tertutup; saat diputar manual ke posisi buka, kontak tetap membuka hingga diputar kembali ke posisi semula.'
  },
  switchNONC: {
    desc: 'Saklar Selektor 2 Posisi dengan Kontak Ganda NO dan NC.',
    how: 'Beralih antara membuka satu sirkuit dan menutup sirkuit lainnya secara terkunci (misal seleksi mode Manual / Otomatis).'
  },
  switchCON: {
    desc: 'Saklar Selektor Changeover 2 Posisi (SPDT Maintained).',
    how: 'Mengalihkan terminal common secara mantap antara dua cabang beban atau sumber daya yang berbeda.'
  },
  switchCrossover: {
    desc: 'Saklar Balik Kutub 2 Posisi (Polarity / Reversing Switch).',
    how: 'Membalik hubungan polaritas positif-negatif atau urutan dua fasa untuk membalik arah putaran motor DC atau aktuator.'
  },
  switch3p0: {
    desc: 'Saklar Selektor 3 Posisi I - 0 - II (Tengah Netral / Off).',
    how: 'Posisi tengah (0) semua kontak terbuka; diputar ke kiri (I) menyalakan mode 1, diputar ke kanan (II) menyalakan mode 2.'
  },
  switch3p: {
    desc: 'Saklar Selektor 3 Posisi I - II - III (Tanpa Posisi Netral).',
    how: 'Memilih satu dari tiga rangkaian operasional bertingkat secara berurutan (misal seleksi kecepatan motor Rendah-Sedang-Tinggi).'
  },
  rollerNO: {
    desc: 'Limit Switch Mekanis NO dengan Roda Roller (Saklar Pembatas).',
    how: 'Lengan roda tertekan oleh cam meja mesin menutup kontak NO; pegas internal membuka kontak kembali saat cam bergeser pergi.'
  },
  rollerNC: {
    desc: 'Limit Switch Mekanis NC (Saklar Pembatas Akhir Gerak).',
    how: 'Membuka kontak kontrol NC saat batas travel mesin tercapai untuk mematikan motor penggerak sebelum terjadi tabrakan mekanis.'
  },
  rollerNONC: {
    desc: 'Limit Switch Mekanis Ganda 1 NO + 1 NC Snap-Action.',
    how: 'Mekanisme pegas snap-action memicu transfer kontak kecepatan tinggi independen dari kecepatan dorong aktuator mesin.'
  },
  rollerCON: {
    desc: 'Limit Switch Mekanis Changeover (SPDT Roller Switch).',
    how: 'Mengalihkan sinyal dari indikator standby ke sinyal konfirmasi posisi akhir aktuator pneumatik atau silinder hidrolik.'
  },

  // === CONTACTORS ===
  contactor1p: {
    desc: 'Kontaktor Tenaga Utama 1-Kutub (Single-Pole Power Contactor).',
    how: 'Koil elektromagnetik menarik kontak daya berdaya hantar tinggi menutup untuk menyalurkan arus beban pemanas atau lampu besar.'
  },
  contactor2p: {
    desc: 'Kontaktor Tenaga Utama 2-Kutub.',
    how: 'Menghubungkan dan memutus dua kawat daya secara simultan pada beban listrik fasa tunggal 230V atau rangkaian DC motor.'
  },
  contactor3p: {
    desc: 'Kontaktor Tenaga Utama 3-Kutub (-KM1) Standar IEC AC-3.',
    how: 'Koil magnetik menarik armature menutup ketiga kontak daya 1-2, 3-4, 5-6 serempak untuk menjalankan motor induksi 3-fasa.',
    simVideo: 'dol_starter'
  },
  contactor4p: {
    desc: 'Kontaktor Tenaga Utama 4-Kutub (L1-L2-L3-N atau Sistem ATS Genset).',
    how: 'Memindahkan 4 penghantar daya secara bersamaan dalam panel saklar transfer otomatis (Automatic Transfer Switch).'
  },
  contactorNC1p: {
    desc: 'Kontaktor Tenaga Kutub NC 1P (Normally Closed Power Pole).',
    how: 'Tertutup saat de-energized dan membuka saat koil aktif; diaplikasikan pada pengereman dinamis motor atau beban proteksi khusus.'
  },
  contactorNC2p: {
    desc: 'Kontaktor Tenaga Kutub NC 2P.',
    how: 'Membuka sirkuit daya 2-kutub saat koil magnetik dialiri arus listrik.'
  },
  contactorNC3p: {
    desc: 'Kontaktor Tenaga Kutub NC 3P.',
    how: 'Membuka ketiga jalur daya 3-fasa saat koil elektromagnetik teraktivasi.'
  },
  contactorNC4p: {
    desc: 'Kontaktor Tenaga Kutub NC 4P.',
    how: 'Memutuskan 4 kutub daya secara serempak saat medan magnet elektromagnet koil bekerja.'
  },

  // === COILS & TIMERS ===
  coil: {
    desc: 'Koil Elektromagnetik Kontaktor / Relai Standar (-KM / -KA).',
    how: 'Dialiri tegangan pada terminal A1-A2 membangkitkan medan magnet kuat yang seketika menarik seluruh kontak utama dan bantu terkait.',
    simVideo: 'dol_starter'
  },
  coilDelayOn: {
    desc: 'Koil Relai Pewaktu On-Delay (Timer Delay ON -KT1).',
    how: 'Saat diberi daya, mekanisme internal menghitung mundur preset waktu; setelah waktu habis, kontak bantu pewaktu berpindah seketika.',
    simVideo: 'on_delay'
  },
  coilDelayOff: {
    desc: 'Koil Relai Pewaktu Off-Delay (Timer Delay OFF -KT1).',
    how: 'Kontak langsung aktif saat terenergi; saat tegangan input diputus, kontak tetap ditahan aktif hingga waktu tunda selesai baru membuka.',
    simVideo: 'off_delay'
  },
  coilDelayOnOff: {
    desc: 'Koil Relai Pewaktu Kombinasi Tunda Hidup & Tunda Mati.',
    how: 'Menerapkan waktu tunda terpisah saat koil diberi daya (tunda hidup) maupun saat koil dilepas dayanya (tunda mati).'
  },
  flasher: {
    name: 'Flasher Otomotif (Inline Flasher)',
    desc: 'Komponen kelistrikan yang digunakan untuk menghasilkan sinyal kedipan pada lampu sein atau lampu tanda peringatan (hazard) secara periodik otomatis.',
    how: 'Arus masuk melalui terminal 49 dan diteruskan ke beban lampu melalui terminal 49a. Flasher secara otomatis menyambung dan memutus aliran arus secara periodik langsung, menghasilkan efek kedipan lampu yang teratur.',
    io: [
      { pin: '49', type: 'INPUT', desc: 'Input Daya Positif (dari Saklar Kontak)' },
      { pin: '49a', type: 'OUTPUT', desc: 'Output Berkedip (ke Beban Lampu / Saklar Sein)' }
    ]
  },
  coilFlasher: {
    desc: 'Koil Relai Pengedip Asimetris (Flasher / Multivibrator Relay).',
    how: 'Osilator internal bergantian mengaktifkan dan menonaktifkan kontak secara periodik untuk lampu tanda bahaya atau sirine strobo.'
  },
  coilLatch: {
    desc: 'Koil Relai Impuls / Latching Bistable (Relai Tangga).',
    how: 'Setiap satu denyut sinyal singkat pada koil membalikkan status kontak dan menguncinya secara mekanis tanpa memerlukan arus kontinu.'
  },
  coilSolenoid: {
    desc: 'Koil Solenoid Aktuator Elektro-Pneumatik / Hidrolik (-Y1).',
    how: 'Menghasilkan gaya dorong magnetik translasi untuk menggeser spool katup fluida udara bertekanan atau cairan hidrolik.'
  },

  // === AUXILIARY CONTACTS ===
  contactNO: {
    desc: 'Kontak Bantu Normally Open (Terminal 13-14 -KM1).',
    how: 'Terbuka saat koil master mati; menutup saat koil aktif, umumnya diparalel dengan tombol Start untuk rangkaian pengunci mandiri (self-holding).',
    simVideo: 'dol_starter'
  },
  contactNC: {
    desc: 'Kontak Bantu Normally Closed (Terminal 21-22 -KM1).',
    how: 'Tertutup saat koil master mati; membuka saat koil aktif, digunakan untuk interlocking listrik pencegah tabrakan arah Forward dan Reverse.',
    simVideo: 'dol_starter'
  },
  contactNONC: {
    desc: 'Blok Kontak Bantu Ganda (1 NO 13-14 + 1 NC 21-22).',
    how: 'Menyediakan sinyal status ganda terkoordinasi untuk logika interlocking rangkaian kendali otomasi.'
  },
  contactCON: {
    desc: 'Kontak Bantu Changeover (SPDT Terminal 11-12-14).',
    how: 'Terminal common 11 memindahkan koneksi dari terminal NC 12 ke terminal NO 14 saat relai master terenergi.'
  },
  contactNODelayOn: {
    desc: 'Kontak Bantu Pewaktu On-Delay NO (Terminal 17-18 -KT1).',
    how: 'Tetap terbuka selama penghitungan mundur, dan menutup secara mendadak (snap action) begitu waktu tunda preset tercapai.',
    simVideo: 'on_delay'
  },
  contactNCDelayOn: {
    desc: 'Kontak Bantu Pewaktu On-Delay NC (Terminal 15-16 -KT1).',
    how: 'Tetap tertutup selama penghitungan mundur, dan membuka seketika saat waktu tunda preset tercapai untuk mematikan kontaktor Bintang (Star).',
    simVideo: 'on_delay'
  },
  contactNONCDelayOn: {
    desc: 'Kontak Bantu On-Delay Ganda (1 NO 17-18 + 1 NC 15-16).',
    how: 'Digunakan sebagai kontak sentral perpindahan transisi starter motor Bintang-Segitiga (Star-Delta Transition).'
  },
  contactCONDelayOn: {
    desc: 'Kontak Bantu On-Delay Changeover (SPDT Pewaktu Tunda Hidup).',
    how: 'Beralih dari jalur NC ke jalur NO secara permanen setelah waktu tunda preset timer terhitung habis.'
  },
  contactNODelayOff: {
    desc: 'Kontak Bantu Pewaktu Off-Delay NO (Terminal 27-28 -KT1).',
    how: 'Menutup instan saat koil timer aktif, dan tetap tertutup selama durasi preset setelah koil dimatikan sebelum kembali membuka.',
    simVideo: 'off_delay'
  },
  contactNCDelayOff: {
    desc: 'Kontak Bantu Pewaktu Off-Delay NC (Terminal 25-26 -KT1).',
    how: 'Membuka instan saat koil timer aktif, dan tetap terbuka selama durasi preset setelah daya koil diputus sebelum kembali menutup.',
    simVideo: 'off_delay'
  },
  contactNONCDelayOff: {
    desc: 'Kontak Bantu Off-Delay Ganda (1 NO 27-28 + 1 NC 25-26).',
    how: 'Menyediakan kontrol tunda mati simultan untuk sistem pendinginan ekstra (fan overrun) setelah mesin utama dimatikan.'
  },
  contactCONDelayOff: {
    desc: 'Kontak Bantu Off-Delay Changeover (SPDT Pewaktu Tunda Mati).',
    how: 'Mempertahankan posisi aktif selama waktu tunda pasca pemutusan sinyal kendali input.'
  },

  // === MOTORS ===
  motor3ph: {
    desc: 'Motor Induksi Tiga Fasa AC Sangkar Tupai (M 3~).',
    how: 'Arus 3-fasa simetris menghasilkan medan putar magnet pada celah udara stator, menginduksi arus pada batang rotor dan menghasilkan torsi mekanik kontinu.',
    simVideo: 'dol_starter'
  },
  motor1ph: {
    desc: 'Motor Induksi Fasa Tunggal AC dengan Kapasitor (M 1~).',
    how: 'Menggunakan belitan utama dan belitan bantu berseri kapasitor untuk menciptakan pergeseran fasa 90 derajat listrik penghasil medan putar start.'
  },
  motorDC: {
    desc: 'Motor Arus Searah Komutator (DC Motor).',
    how: 'Gaya elektromagnetik Lorentz memutar jangkar kawat berarus di dalam fluks magnet permanen; arah putaran dibalik dengan menukar polaritas tegangan input.'
  },
  motorStarDelta: {
    desc: 'Motor Induksi 3-Fasa dengan 6 Terminal Bebas (U1-V1-W1 & U2-V2-W2).',
    how: 'Dijalankan dalam sambungan Bintang (Y) untuk mereduksi arus kejut start hingga 33%, kemudian dialihkan ke sambungan Segitiga (Delta) untuk daya penuh.'
  },
  motorDhalander: {
    desc: 'Motor Induksi 2-Kecepatan Belitan Dahlander (Pole-Changing Motor).',
    how: 'Mengubah konfigurasi sambungan belitan dari seri ke paralel ganda (misal 4-kutub ke 2-kutub), menghasilkan rasio kecepatan putar 1:2.'
  },
  motorTwoSpeeds: {
    desc: 'Motor Induksi 2-Kecepatan Belitan Terpisah (Separate Winding Motor).',
    how: 'Memiliki dua pasang belitan stator independen yang diisolasi terpisah untuk menghasilkan dua nilai RPM kerja tanpa kompromi efisiensi.'
  },

  // === LOADS & SIGNALLING ===
  lamp: {
    desc: 'Lampu Indikator Panel Industri (Pilot Lamp LED / Filamen).',
    how: 'Menyala terang visual saat dialiri listrik untuk indikasi status mesin (Hijau = Jalan/Running, Merah = Berhenti/Stop, Kuning = Trip/Gangguan).',
    simVideo: 'on_delay'
  },
  lampBlink: {
    desc: 'Lampu Tanda Bahaya Berkedip (Flashing Hazard Beacon).',
    how: 'Sirkuit internal mengedipkan lampu secara periodik (500ms ON / 500ms OFF) untuk memberi peringatan visual intensif bagi operator.'
  },
  siren: {
    desc: 'Sirine / Klakson Peringatan Industri Suara Tinggi (Horn / Siren).',
    how: 'Transduser elektromagnetik menghasilkan gelombang suara frekuensi tinggi kontinu (>95 dB) saat dialiri arus untuk evakuasi darurat atau sinyal pra-start.'
  },
  ring: {
    desc: 'Bel Listrik Mekanis Industri (Electric Bell Ring).',
    how: 'Solenoid pemukul intermiten memukul mangkok logam secara berulang cepat, menghasilkan bunyi dering dering nyaring penanda pergantian shift kerja.'
  },
  buzzer: {
    desc: 'Buzzer Piezoelektrik Peringatan Panel (Audio Alarm Buzzer).',
    how: 'Elemen keramik piezoelektrik berosilasi pada frekuensi resonansi dengan jeda berirama untuk memberikan alarm audio kegagalan operasional.'
  },

  // === WIRING & TERMINALS ===
  junction2p: {
    desc: 'Titik sambungan kawat lurus 2-Arah (Junction Connection 2P).',
    how: 'Menghubungkan dua segmen kawat pada satu node potensial listrik yang sama.'
  },
  junction2p90: {
    desc: 'Titik sambungan kawat siku 90 Derajat (Junction Connection 90°).',
    how: 'Membelokkan jalur pengkabelan diagram secara rapi pada grid skematik 30px.'
  },
  junction3p: {
    desc: 'Titik percabangan kawat 3-Arah (Junction T-Node 3P).',
    how: 'Mencabangkan aliran arus dari satu kawat utama ke sirkuit cabang paralel tanpa drop tegangan.'
  },
  junction4p: {
    desc: 'Titik persimpangan kawat 4-Arah (Junction Cross-Node 4P).',
    how: 'Menghubungkan empat jalur konduktor sekaligus pada satu titik simpul ekuipotensial.'
  },
  junctionPE: {
    desc: 'Terminal blok sambungan konduktor pembumian pengaman (PE).',
    how: 'Menyatukan kabel grounding pelindung mesin ke busbar pembumian utama panel.'
  },
  diode: {
    desc: 'Dioda Semikonduktor Penyearah Silikon (Silicon Rectifier Diode).',
    how: 'Mengalirkan arus listrik hanya dari anoda ke katoda; digunakan sebagai peredam lonjakan tegangan induktif koil (flyback snubber) atau penyearah AC-DC.'
  },
  arrowSource: {
    desc: 'Panah Referensi Sumber Antar-Halaman (Source Signal Arrow).',
    how: 'Menandai asal sinyal atau kawat daya yang berlanjut dari lembar diagram sebelumnya.'
  },
  arrowDestination: {
    desc: 'Panah Referensi Tujuan Antar-Halaman (Destination Signal Arrow).',
    how: 'Menandai kelanjutan jalur kawat menuju ke nomor lembar diagram skematik berikutnya.'
  },
  text: {
    desc: 'Label teks anotasi & penamaan skematik (Schematic Text).',
    how: 'Menampilkan judul diagram, nomor kawat, instruksi operasional, atau spesifikasi komponen pada kanvas gambar.'
  }
};
