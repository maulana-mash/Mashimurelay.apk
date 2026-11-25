package com.mashimurelay.app.nativecad

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudentPdfInfo(
    val studentName: String = "",
    val studentPresensi: String = "",
    val studentClass: String = "",
    val studentSchool: String = "",
    val projectTitle: String = "Rangkaian Skematik Kelistrikan",
    val practiceDate: String = ""
)

data class BomGroupItem(
    val svgData: String,
    val name: String,
    val tags: MutableList<String>,
    val elements: MutableList<NativeElement>,
    var count: Int,
    var specs: String
)

private data class SystemVoltageInfo(
    val mode: String,         // "DC", "AC3", "AC1", "GENERIC"
    val label: String,
    val controlLabel: String
)

private data class WrappedBomRow(
    val itemIndex: Int,
    val item: BomGroupItem,
    val nameLines: List<String>,
    val tagLines: List<String>,
    val specLines: List<String>,
    val rowHeight: Float
)

object NativePdfReportGenerator {

    private val printThumbnailCache = mutableMapOf<String, Bitmap>()

    fun clearCache() {
        printThumbnailCache.values.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        printThumbnailCache.clear()
    }

    // =========================================================================
    // 1. FILTER NON-BOM & DETEKSI SISTEM TEGANGAN (AC / DC) — IDENTIK WEB/PC
    // =========================================================================
    private fun isNonBomElement(svgData: String): Boolean {
        val s = svgData.lowercase(Locale.US)
        if (s == "text") return true
        if (s.startsWith("junction") || s.startsWith("connection")) return true
        return false
    }

    private fun detectSystemType(elements: List<NativeElement>): SystemVoltageInfo {
        var has3Ph = false
        var has1Ph = false
        var hasDc = false
        for (el in elements) {
            val s = el.svgData.lowercase(Locale.US)
            when (s) {
                "sourcel1l2l3", "sourcel1l2l3n", "generator3ph",
                "motor3ph", "motorstardelta", "motordhalander",
                "motortwospeeds", "phasefailure" -> has3Ph = true

                "sourcel1", "sourcel2", "sourcel3", "sourcen", "motor1ph" -> has1Ph = true

                "battery", "sourceplus", "sourcenegative",
                "powersupply", "powersupply2", "motordc", "flasher" -> hasDc = true
            }
        }
        return when {
            hasDc && !has3Ph && !has1Ph -> SystemVoltageInfo("DC", "12V DC", "12V DC")
            has3Ph -> SystemVoltageInfo("AC3", "380V AC (3 Fasa)", "220V AC")
            has1Ph -> SystemVoltageInfo("AC1", "220V AC", "220V AC")
            else -> SystemVoltageInfo("GENERIC", "Standar IEC", "Standar IEC")
        }
    }

    private fun formatLampColorId(rawColor: String?): String {
        return when (rawColor?.trim()?.lowercase(Locale.US)) {
            "green" -> "Hijau"
            "red" -> "Merah"
            "blue" -> "Biru"
            "white" -> "Putih"
            "orange" -> "Oranye"
            else -> "Kuning"
        }
    }

    // =========================================================================
    // 2. KAMUS PENAMAAN & SPESIFIKASI KOMPONEN STANDAR VOKASI (BEBAS BUG SUBSTRING)
    // =========================================================================
    private fun getVocationalName(svgData: String): String {
        val s = svgData.lowercase(Locale.US)

        // Sumber Daya
        when (s) {
            "battery" -> return "Baterai / Akumulator 12V DC"
            "sourceplus" -> return "Terminal Sumber Positif (+)"
            "sourcenegative" -> return "Terminal Massa / Negatif (-)"
            "sourcel1" -> return "Sumber Fasa L1 (AC)"
            "sourcel2" -> return "Sumber Fasa L2 (AC)"
            "sourcel3" -> return "Sumber Fasa L3 (AC)"
            "sourcen" -> return "Terminal Netral (N)"
            "sourcel1l2l3" -> return "Sumber 3 Fasa (L1-L2-L3)"
            "sourcel1l2l3n" -> return "Sumber 3 Fasa + Netral (3P+N)"
            "sourcepe", "sourcegnd" -> return "Terminal Pembumian (PE / Ground)"
            "powersupply", "powersupply2" -> return "Catu Daya DC (Power Supply)"
            "generator3ph" -> return "Generator 3 Fasa AC"
        }

        // Proteksi
        when (s) {
            "breaker1p" -> return "MCB 1 Kutub (1P)"
            "breaker2p" -> return "MCB 2 Kutub (2P)"
            "breaker3p" -> return "MCB 3 Kutub (3P)"
            "breaker4p" -> return "MCB 4 Kutub (4P)"
            "breaker", "motorbreaker3p" -> return "Motor Circuit Breaker 3P (MPCB)"
            "motorbreaker1p" -> return "Motor Circuit Breaker 1P (MPCB)"
            "motorbreaker2p" -> return "Motor Circuit Breaker 2P (MPCB)"
            "motorbreaker4p" -> return "Motor Circuit Breaker 4P (MPCB)"
            "fuse1p" -> return "Sekring Pengaman 1P (Fuse)"
            "fuse2p" -> return "Sekring Pengaman 2P (Fuse)"
            "fuse3p" -> return "Sekring Pengaman 3P (Fuse)"
            "fuse4p" -> return "Sekring Pengaman 4P (Fuse)"
            "overload1p", "overload2p", "overload3p" -> return "Thermal Overload Relay (TOR)"
            "phasefailure" -> return "Phase Failure Relay (PFR)"
        }
        if (s.startsWith("tmbreaker")) return "MCB Thermal-Magnetik ${svgData.takeLast(2).uppercase(Locale.US)}"
        if (s.startsWith("rcbobreaker")) return "RCBO (ELCB + MCB) ${svgData.takeLast(2).uppercase(Locale.US)}"
        if (s.startsWith("rccbbreaker")) return "RCCB / ELCB ${svgData.takeLast(2).uppercase(Locale.US)}"
        if (s.startsWith("fuseswitch")) return "Saklar Sekring (Fuse Switch)"
        if (s.startsWith("isolator")) return "Saklar Pemisah (Isolator ${svgData.takeLast(2).uppercase(Locale.US)})"

        // Koil & Timer
        when (s) {
            "coil" -> return "Koil Kontaktor / Relay"
            "coildelayon" -> return "Timer On-Delay Relay (TON)"
            "coildelayoff" -> return "Timer Off-Delay Relay (TOF)"
            "coildelayonoff" -> return "Timer On-Off Delay Relay"
            "coilflasher", "flasher" -> return "Unit Flasher Elektronik (Kedip)"
            "coillatch" -> return "Impulse / Latching Relay"
            "coilsolenoid" -> return "Koil Solenoid Elektromagnetik"
        }

        // Kontaktor Daya
        when (s) {
            "contactor1p" -> return "Kontaktor Daya Utama NO (1P)"
            "contactor2p" -> return "Kontaktor Daya Utama NO (2P)"
            "contactor3p" -> return "Kontaktor Daya Utama NO (3P)"
            "contactor4p" -> return "Kontaktor Daya Utama NO (4P)"
            "contactornc1p" -> return "Kontaktor Daya Utama NC (1P)"
            "contactornc2p" -> return "Kontaktor Daya Utama NC (2P)"
            "contactornc3p" -> return "Kontaktor Daya Utama NC (3P)"
            "contactornc4p" -> return "Kontaktor Daya Utama NC (4P)"
        }

        // Kontak Bantu & Timer
        when (s) {
            "contactno" -> return "Kontak Bantu NO"
            "contactnc" -> return "Kontak Bantu NC"
            "contactcon" -> return "Kontak Tukar (Changeover SPDT)"
            "contactnonc" -> return "Kontak Bantu Ganda (NO + NC)"
            "contactnodelayon" -> return "Kontak Timer NO (On-Delay)"
            "contactncdelayon" -> return "Kontak Timer NC (On-Delay)"
            "contactcondelayon" -> return "Kontak Timer Tukar (On-Delay)"
            "contactnoncdelayon" -> return "Kontak Timer Ganda (On-Delay)"
            "contactnodelayoff" -> return "Kontak Timer NO (Off-Delay)"
            "contactncdelayoff" -> return "Kontak Timer NC (Off-Delay)"
            "contactcondelayoff" -> return "Kontak Timer Tukar (Off-Delay)"
            "contactnoncdelayoff" -> return "Kontak Timer Ganda (Off-Delay)"
        }

        // Tombol & Saklar
        when (s) {
            "buttonno", "pushbtnno" -> return "Tombol Tekan NO (Push Button)"
            "buttonnc", "pushbtnnc" -> return "Tombol Tekan NC (Push Button)"
            "buttoncon" -> return "Tombol Tekan Tukar (PB SPDT)"
            "buttonnonc" -> return "Tombol Tekan Ganda (PB NO+NC)"
            "buttoncrossover" -> return "Tombol Tekan Silang (Crossover)"
            "switchno" -> return "Saklar Tunggal NO (Toggle / Kunci)"
            "switchnc" -> return "Saklar Tunggal NC"
            "switchcon" -> return "Saklar Tukar (SPDT / Dimmer)"
            "switchnonc" -> return "Saklar Ganda (NO + NC)"
            "switch3p0" -> return "Saklar Selektor 3 Posisi (I-0-II)"
            "switch3p" -> return "Saklar Selektor 3 Posisi (I-II-III)"
            "switchcrossover" -> return "Saklar Silang (Intermediate)"
        }
        if (s.startsWith("emergency") || s == "pushbtnemerg") return "Tombol Darurat (Emergency Stop)"
        if (s.startsWith("roller")) return "Limit Switch (Saklar Batas)"

        // Beban, Motor, & Lainnya
        when (s) {
            "lamp" -> return "Lampu Indikator (Pilot Lamp)"
            "lampblink" -> return "Lampu Indikator Kedip (Blinker)"
            "buzzer" -> return "Buzzer / Klakson Listrik"
            "ring" -> return "Bel Listrik (Alarm Bell)"
            "siren" -> return "Sirine / Klakson Alarm"
            "motor3ph", "motor3" -> return "Motor Induksi 3 Fasa AC"
            "motor1ph" -> return "Motor Induksi 1 Fasa AC"
            "motordc" -> return "Motor Listrik DC"
            "motorstardelta" -> return "Motor 3 Fasa Star-Delta"
            "motordhalander" -> return "Motor 3 Fasa Dahlander"
            "motortwospeeds" -> return "Motor 3 Fasa 2 Kecepatan"
            "diode" -> return "Dioda Penyearah / Isolasi"
            "arrowsource" -> return "Panah Referensi Sumber"
            "arrowdestination" -> return "Panah Referensi Tujuan"
        }

        val def = NativeComponentRegistry.get(svgData)
        if (def != null && def.name.isNotEmpty()) return def.name
        return svgData
    }

    private fun buildGroupSpecs(svgData: String, groupEls: List<NativeElement>, sys: SystemVoltageInfo): String {
        val s = svgData.lowercase(Locale.US)
        val voltCtrl = sys.controlLabel

        // Timer & Flasher
        if (s == "coildelayon" || s == "coildelayoff" || s == "coildelayonoff" || s == "coilflasher" || s == "flasher") {
            val presets = mutableListOf<String>()
            for (el in groupEls) {
                if (el.presetTime > 0f) {
                    val onStr = String.format(Locale.US, "%.1fs", el.presetTime)
                    val lbl = if (s == "coildelayonoff" && el.presetTimeOff > 0f) {
                        "ON $onStr / OFF ${String.format(Locale.US, "%.1fs", el.presetTimeOff)}"
                    } else {
                        onStr
                    }
                    if (!presets.contains(lbl)) presets.add(lbl)
                }
            }
            val pText = if (presets.isNotEmpty()) "Preset: ${presets.joinToString(", ")}" else "Pengatur Waktu"
            return "$pText ($voltCtrl)"
        }

        // Lampu
        if (s == "lamp" || s == "lampblink") {
            val colors = mutableListOf<String>()
            for (el in groupEls) {
                val col = formatLampColorId(el.lampColor)
                if (!colors.contains(col)) colors.add(col)
            }
            val blinkNote = if (s == "lampblink") ", Kedip" else ""
            return "Warna: ${colors.joinToString(", ")}$blinkNote ($voltCtrl)"
        }

        // Sumber Daya
        when (s) {
            "battery" -> return "Sumber Catu Daya DC 12V"
            "sourceplus" -> return "Sumber Tegangan Positif (+12V DC)"
            "sourcenegative" -> return "Jalur Massa / Negatif (- DC)"
            "sourcel1" -> return "Sumber Fasa Tunggal 220V AC"
            "sourcel2", "sourcel3" -> return "Sumber Fasa 220V / 380V AC"
            "sourcen" -> return "Jalur Netral 220V AC"
            "sourcel1l2l3" -> return "Catu Daya 3 Fasa 380V AC"
            "sourcel1l2l3n" -> return "Catu Daya 3 Fasa + Netral 380V/220V AC"
            "sourcepe", "sourcegnd" -> return "Proteksi Pembumian (Earth / PE)"
            "powersupply", "powersupply2" -> return "Konverter Catu Daya AC ke DC"
            "generator3ph" -> return "Pembangkit 3 Fasa 380V AC"
        }

        // Proteksi
        when (s) {
            "breaker1p" -> return "Pengaman Arus Lebih 1 Fasa (220V AC)"
            "breaker2p" -> return "Pengaman Arus Lebih 2 Kutub"
            "breaker3p", "breaker4p" -> return "Pengaman Arus Lebih 3 Fasa (380V AC)"
            "breaker" -> return "Pengaman Motor Thermal-Magnetik"
            "phasefailure" -> return "Proteksi Kegagalan & Urutan Fasa"
        }
        if (s.startsWith("motorbreaker")) return "Pengaman Motor Thermal-Magnetik"
        if (s.startsWith("tmbreaker")) return "Pengaman Beban Lebih & Hubung Singkat"
        if (s.startsWith("rcbo")) return "Proteksi Arus Bocor & Arus Lebih"
        if (s.startsWith("rccb")) return "Proteksi Arus Bocor ke Tanah"
        if (s.startsWith("fuse") && !s.startsWith("fuseswitch")) {
            return if (sys.mode == "DC") "Sekring Pengaman Blade Fuse (12V DC)" else "Sekring Pengaman Arus Lebih"
        }
        if (s.startsWith("fuseswitch")) return "Pemutus Beban Berpengaman Sekring"
        if (s.startsWith("isolator")) return "Saklar Pemisah Rangkaian"
        if (s.startsWith("overload")) return "Proteksi Beban Lebih Motor (Bimetal)"

        // Koil, Kontaktor, Kontak
        if (s == "coil") return "Kumparan Elektromagnetik ($voltCtrl)"
        if (s == "coilsolenoid") return "Kumparan Solenoid Aktuator ($voltCtrl)"
        if (s == "coillatch") return "Relay Pengunci Impuls ($voltCtrl)"
        if (s.startsWith("contactornc")) return "Kontak Daya Utama Normally Closed"
        if (s.startsWith("contactor")) return "Kontak Daya Utama Normally Open"
        if (s == "contactno") return "Kontak Bantu Normally Open"
        if (s == "contactnc") return "Kontak Bantu Normally Closed"
        if (s == "contactcon") return "Kontak Tukar 5-Kaki (NO-NC)"
        if (s == "contactnonc") return "Kontak Bantu Ganda (NO + NC)"
        if (s.contains("delayon")) return "Kontak Timer Tunda Hidup (TON)"
        if (s.contains("delayoff")) return "Kontak Timer Tunda Mati (TOF)"

        // Tombol & Saklar
        if (s == "buttonno" || s == "pushbtnno") return "Saklar Tekan Pegas (Normally Open)"
        if (s == "buttonnc" || s == "pushbtnnc") return "Saklar Tekan Pegas (Normally Closed)"
        if (s.startsWith("button")) return "Saklar Tekan Pegas (Momentary)"
        if (s.startsWith("emergency") || s == "pushbtnemerg") return "Saklar Darurat Pengunci (Latch)"
        if (s == "switchno") return "Saklar Pengunci Normally Open"
        if (s == "switchnc") return "Saklar Pengunci Normally Closed"
        if (s == "switchcon") return "Saklar Pengalih 2 Arah (Changeover)"
        if (s == "switch3p0") return "Saklar Selektor 3 Posisi (Kiri-Off-Kanan)"
        if (s == "switch3p") return "Saklar Selektor 3 Arah"
        if (s.startsWith("switch")) return "Saklar Kendali Manual"
        if (s.startsWith("roller")) return "Saklar Batas Mekanik (Limit Switch)"

        // Beban & Motor & Lainnya
        if (s == "buzzer" || s == "ring" || s == "siren") return "Indikator Suara / Alarm ($voltCtrl)"
        if (s == "motor3ph" || s == "motor3") return "Motor Induksi 3 Fasa 380V AC"
        if (s == "motor1ph") return "Motor Induksi 1 Fasa 220V AC"
        if (s == "motordc") return "Aktuator Motor DC (${if (sys.mode == "DC") "12V DC" else voltCtrl})"
        if (s == "motorstardelta") return "Motor 3 Fasa 6-Terminal (Star-Delta)"
        if (s == "motordhalander" || s == "motortwospeeds") return "Motor 3 Fasa 2 Kecepatan"
        if (s == "diode") return "Semikonduktor Penyearah / Isolasi Arus"

        return "Standar CAD Industri (IEC 60617)"
    }

    // =========================================================================
    // 3. UTILITAS WARNA CETAK KERTAS PUTIH & WORD-WRAP MULTI-BARIS
    // =========================================================================
    private fun darkenForWhitePaper(rawColor: String?): Int {
        val c = rawColor?.trim()?.lowercase(Locale.US) ?: "#0f172a"
        return when (c) {
            "#ffffff", "#f8fafc", "#f1f5f9", "white" -> Color.parseColor("#0F172A")
            "#fbbf24", "#fde047", "yellow" -> Color.parseColor("#B45309")
            "#38bdf8", "#93c5fd", "#60a5fa", "#00e5ff", "#00ffff" -> Color.parseColor("#0369A1")
            "#34d399", "#4ade80", "#86efac", "green" -> Color.parseColor("#047857")
            "#f87171", "#fca5a5", "red" -> Color.parseColor("#B91C1C")
            "#94a3b8", "#cbd5e1", "gray" -> Color.parseColor("#475569")
            "#c084fc", "#a78bfa" -> Color.parseColor("#6D28D9")
            else -> try {
                Color.parseColor(rawColor)
            } catch (_: Exception) {
                Color.parseColor("#0F172A")
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return listOf("-")
        if (paint.measureText(cleaned) <= maxWidth) return listOf(cleaned)

        val result = mutableListOf<String>()
        val words = cleaned.split(Regex("\\s+"))
        var currentLine = ""

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine)
                    currentLine = ""
                }
                if (paint.measureText(word) <= maxWidth) {
                    currentLine = word
                } else {
                    // Pecah kata super panjang agar tidak melampaui lebar sel
                    var start = 0
                    while (start < word.length) {
                        val count = paint.breakText(word, start, word.length, true, maxWidth, null).coerceAtLeast(1)
                        val chunk = word.substring(start, start + count)
                        if (start + count < word.length) {
                            result.add(chunk)
                        } else {
                            currentLine = chunk
                        }
                        start += count
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) result.add(currentLine)
        return if (result.isEmpty()) listOf("-") else result
    }

    // =========================================================================
    // 4. THUMBNAIL BITMAP TRANSPARAN 1:1 (MENGGUNAKAN def.file & LIGHT THEME)
    // =========================================================================
    private fun getPrintThumbnailBitmap(context: Context, svgData: String, sizePx: Int = 120): Bitmap? {
        val def = NativeComponentRegistry.get(svgData)
        val fileName = def?.file ?: svgData
        if (printThumbnailCache.containsKey(fileName)) {
            val cached = printThumbnailCache[fileName]
            if (cached != null && !cached.isRecycled) return cached
        }

        val svg: SVG = SvgComponentRenderer.getSvg(
            context = context,
            svgFileName = fileName,
            frameIndex = 0,
            variantIndex = 0,
            lampColor = "yellow",
            isLightTheme = true
        ) ?: return null

        val scratchSize = 200
        val scratchBitmap = Bitmap.createBitmap(scratchSize, scratchSize, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBitmap)
        val pad = 16f
        val renderRect = RectF(pad, pad, scratchSize - pad, scratchSize - pad)
        svg.renderToCanvas(scratchCanvas, renderRect)

        val finalBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)

        val inkBounds = findInkBounds(scratchBitmap)
        if (inkBounds != null && inkBounds.width() > 2 && inkBounds.height() > 2) {
            val padRatio = 0.12f
            val availSize = sizePx * (1f - padRatio * 2f)
            val inkW = inkBounds.width().toFloat()
            val inkH = inkBounds.height().toFloat()
            val scale = minOf(availSize / inkW, availSize / inkH)
            val destW = inkW * scale
            val destH = inkH * scale
            val destLeft = (sizePx - destW) * 0.5f
            val destTop = (sizePx - destH) * 0.5f
            val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            finalCanvas.drawBitmap(scratchBitmap, inkBounds, destRect, p)
        } else {
            val pRect = RectF(sizePx * 0.12f, sizePx * 0.12f, sizePx * 0.88f, sizePx * 0.88f)
            svg.renderToCanvas(finalCanvas, pRect)
        }
        scratchBitmap.recycle()

        printThumbnailCache[fileName] = finalBitmap
        return finalBitmap
    }

    private fun findInkBounds(bitmap: Bitmap, alphaThreshold: Int = 15): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > alphaThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX >= minX && maxY >= minY) Rect(minX, minY, maxX + 1, maxY + 1) else null
    }

    // =========================================================================
    // 5. HELPER BINGKAI GANDA A4 PORTRAIT (IDENTIK WEB/PC)
    // =========================================================================
    private fun drawPageFrame(canvas: Canvas) {
        val pageMarginL = 28f
        val pageMarginT = 28f
        val boxW = 539f
        val boxH = 776f
        val boxR = pageMarginL + boxW // 567f
        val boxB = pageMarginT + boxH // 804f

        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, boxB, outerBorderPaint)

        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        canvas.drawRect(pageMarginL + 5f, pageMarginT + 5f, boxR - 5f, boxB - 5f, innerBorderPaint)
    }

    private fun drawPageFootnote(canvas: Canvas, dateDisplay: String, pageNumber: Int, totalPages: Int) {
        val footnoteLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(
            "Dokumen dihasilkan otomatis oleh Mashimurelay CAD Simulator  •  Tanggal Cetak: $dateDisplay",
            32f,
            820f,
            footnoteLeft
        )

        val footnoteRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Halaman $pageNumber dari $totalPages", 563f, 820f, footnoteRight)
    }

    // =========================================================================
    // 6. GENERATOR PDF MULTI-HALAMAN UTAMA (A4 PORTRAIT 595 x 842 pt)
    // =========================================================================
    fun generatePdfBytes(
        context: Context,
        elements: List<NativeElement>,
        connectors: List<NativeConnector>,
        info: StudentPdfInfo
    ): ByteArray {
        val document = PdfDocument()
        val sys = detectSystemType(elements)
        val dateDisplay = info.practiceDate.trim().ifEmpty {
            SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
        }

        // 1. Ekstraksi BOM (hanya komponen listrik asli — bebas text & junction 2P/3P)
        val bomGroups = LinkedHashMap<String, BomGroupItem>()
        for (el in elements) {
            if (isNonBomElement(el.svgData)) continue
            val key = el.svgData
            val grp = bomGroups.getOrPut(key) {
                BomGroupItem(
                    svgData = el.svgData,
                    name = getVocationalName(el.svgData),
                    tags = mutableListOf(),
                    elements = mutableListOf(),
                    count = 0,
                    specs = "-"
                )
            }
            grp.count += 1
            grp.elements.add(el)
            val cleanTag = el.tag.trim()
            if (cleanTag.isNotEmpty() && !grp.tags.contains(cleanTag)) {
                grp.tags.add(cleanTag)
            }
        }
        val bomList = bomGroups.values.toList()
        for (grp in bomList) {
            grp.specs = buildGroupSpecs(grp.svgData, grp.elements, sys)
        }
        val realComponentCount = bomList.sumOf { it.count }

        // 2. Pra-hitung Word-Wrap & Paginasi Dinamis Halaman BOM (tanpa potongan "...")
        val colWName = 136f
        val colWTag = 77f
        val colWSpecs = 176f
        val cellPadH = 6f
        val lineHeight = 10.5f

        val namePaintMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tagPaintMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val specPaintMeasure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.4f
            typeface = Typeface.DEFAULT
        }

        val wrappedRows = bomList.mapIndexed { idx, item ->
            val nameLines = wrapText(item.name, namePaintMeasure, colWName - cellPadH * 2f)
            val tagText = if (item.tags.isNotEmpty()) item.tags.joinToString(", ") else "-"
            val tagLines = wrapText(tagText, tagPaintMeasure, colWTag - cellPadH * 2f)
            val specLines = wrapText(item.specs, specPaintMeasure, colWSpecs - cellPadH * 2f)
            val maxLines = maxOf(nameLines.size, tagLines.size, specLines.size)
            val computedH = maxOf(34f, maxLines * lineHeight + 13f)
            WrappedBomRow(
                itemIndex = idx + 1,
                item = item,
                nameLines = nameLines,
                tagLines = tagLines,
                specLines = specLines,
                rowHeight = computedH
            )
        }

        // Bagi baris BOM ke beberapa halaman (tabel mulai y=110pt, batas bawah aman y=786pt)
        val tableBodyStartY = 88f + 22f // headerTop (88) + headerH (22) = 110f
        val tableMaxBottom = 786f
        val footerRowH = 24f

        val bomPages = mutableListOf<List<WrappedBomRow>>()
        if (wrappedRows.isEmpty()) {
            bomPages.add(emptyList())
        } else {
            var currentPage = mutableListOf<WrappedBomRow>()
            var curY = tableBodyStartY
            for (row in wrappedRows) {
                if (currentPage.isNotEmpty() && curY + row.rowHeight > tableMaxBottom) {
                    bomPages.add(currentPage)
                    currentPage = mutableListOf()
                    curY = tableBodyStartY
                }
                currentPage.add(row)
                curY += row.rowHeight
            }
            if (currentPage.isNotEmpty()) {
                // Jika halaman terakhir tidak cukup untuk footer total, pindahkan 1 baris atau tambah halaman
                if (curY + footerRowH > tableMaxBottom && currentPage.size > 1) {
                    val lastRow = currentPage.removeAt(currentPage.lastIndex)
                    bomPages.add(currentPage)
                    bomPages.add(listOf(lastRow))
                } else {
                    bomPages.add(currentPage)
                }
            }
        }

        val totalPages = 1 + bomPages.size

        // ---------------------------------------------------------------------
        // HALAMAN 1 (A4 PORTRAIT): SKEMATIK CAD + PIN IEC/DIN + ETIKET & NILAI
        // ---------------------------------------------------------------------
        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = document.startPage(page1Info)
        renderPage1Portrait(
            context = context,
            canvas = page1.canvas,
            elements = elements,
            connectors = connectors,
            info = info,
            dateDisplay = dateDisplay,
            realComponentCount = realComponentCount,
            totalPages = totalPages
        )
        document.finishPage(page1)

        // ---------------------------------------------------------------------
        // HALAMAN 2+ (A4 PORTRAIT): TABEL BOM (PAGINASI DINAMIS BEBAS POTONGAN)
        // ---------------------------------------------------------------------
        for ((pageIdx, pageRows) in bomPages.withIndex()) {
            val pageNum = 2 + pageIdx
            val isLastPage = (pageIdx == bomPages.lastIndex)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            val page = document.startPage(pageInfo)
            renderBomPagePortrait(
                context = context,
                canvas = page.canvas,
                rows = pageRows,
                info = info,
                dateDisplay = dateDisplay,
                pageNumber = pageNum,
                totalPages = totalPages,
                isLastPage = isLastPage,
                totalUniqueTypes = bomList.size,
                totalUnitCount = realComponentCount
            )
            document.finishPage(page)
        }

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        clearCache()
        return out.toByteArray()
    }

    // =========================================================================
    // 7. RENDERER HALAMAN 1 (PORTRAIT A4: SKEMATIK + PIN + ETIKET & NILAI/PARAF)
    // =========================================================================
    private fun renderPage1Portrait(
        context: Context,
        canvas: Canvas,
        elements: List<NativeElement>,
        connectors: List<NativeConnector>,
        info: StudentPdfInfo,
        dateDisplay: String,
        realComponentCount: Int,
        totalPages: Int
    ) {
        val w = 595f
        canvas.drawColor(Color.WHITE)

        val showComponentNames = SettingsManager.isComponentNamesEnabled(context)
        val showPinLabels = SettingsManager.isPinLabelsEnabled(context)

        val pageMarginL = 28f
        val pageMarginT = 28f
        val boxW = 539f
        val boxR = pageMarginL + boxW // 567f
        val boxB = 804f

        drawPageFrame(canvas)

        // Header Bar Atas (2 tingkat rapi — judul panjang tidak pernah tabrakan!)
        val headerH = 48f
        val headerBottom = pageMarginT + headerH // 76f
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, headerBgPaint)
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, borderPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MASHIMURELAY CAD SIMULATOR", pageMarginL + 13f, pageMarginT + 15f, brandPaint)

        val docTypePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LEMBAR KERJA SKEMATIK", boxR - 13f, pageMarginT + 15f, docTypePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cleanTitle = info.projectTitle.trim().ifEmpty { "RANGKAIAN SKEMATIK KELISTRIKAN" }.uppercase(Locale.US)
        val headerDisplayTitle = if (cleanTitle.length > 58) cleanTitle.substring(0, 56) + "..." else cleanTitle
        canvas.drawText(headerDisplayTitle, w * 0.5f, pageMarginT + 37.5f, titlePaint)

        // Area Skematik (y = 82 .. 664 pt)
        val schematicAreaL = 39f
        val schematicAreaT = headerBottom + 6f // 82f
        val schematicAreaR = 556f
        val etiketH = 133f
        val etiketT = boxB - etiketH // 671f
        val schematicAreaB = etiketT - 7f // 664f
        val areaW = schematicAreaR - schematicAreaL
        val areaH = schematicAreaB - schematicAreaT

        if (elements.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "Kanvas sirkuit masih kosong. Tidak ada komponen yang digambar.",
                w * 0.5f,
                (schematicAreaT + schematicAreaB) * 0.5f,
                emptyPaint
            )
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            // Hitung bounding box komponen + teks lebar + margin pin badge
            for (el in elements) {
                if (el.svgData == "text") {
                    val displayText = el.comment.ifEmpty { el.tag }
                    val fs = if (el.fontSize > 0f) el.fontSize else 16f
                    val estHalfW = maxOf(50f, displayText.length * fs * 0.34f) + 16f
                    val estHalfH = maxOf(16f, fs * 0.9f) + 10f
                    minX = minOf(minX, el.x - estHalfW)
                    minY = minOf(minY, el.y - estHalfH)
                    maxX = maxOf(maxX, el.x + estHalfW)
                    maxY = maxOf(maxY, el.y + estHalfH)
                    continue
                }
                val def = NativeComponentRegistry.get(el.svgData)
                val bw = def?.width ?: 60f
                val bh = def?.height ?: 60f
                val rot = ((el.rotation % 4) + 4) % 4
                val effW = if (rot == 1 || rot == 3) bh else bw
                val effH = if (rot == 1 || rot == 3) bw else bh
                minX = minOf(minX, el.x - effW * 0.5f - 28f)
                minY = minOf(minY, el.y - effH * 0.5f - 32f)
                maxX = maxOf(maxX, el.x + effW * 0.5f + 42f)
                maxY = maxOf(maxY, el.y + effH * 0.5f + 28f)
            }

            // Peta jumlah koneksi per pin untuk menggambar titik junction presisi
            val pinWireCounts = mutableMapOf<String, Int>()
            for (c in connectors) {
                val k1 = "${c.elem1Id}:${c.node1Idx}"
                val k2 = "${c.elem2Id}:${c.node2Idx}"
                pinWireCounts[k1] = (pinWireCounts[k1] ?: 0) + 1
                pinWireCounts[k2] = (pinWireCounts[k2] ?: 0) + 1

                val el1 = elements.find { it.id == c.elem1Id } ?: continue
                val el2 = elements.find { it.id == c.elem2Id } ?: continue
                val def1 = NativeComponentRegistry.get(el1.svgData) ?: continue
                val def2 = NativeComponentRegistry.get(el2.svgData) ?: continue
                val p1 = el1.getWorldNodePos(c.node1Idx, def1.nodes)
                val p2 = el2.getWorldNodePos(c.node2Idx, def2.nodes)
                val d1 = el1.getWorldNodeDirection(c.node1Idx, def1.nodes)
                val d2 = el2.getWorldNodeDirection(c.node2Idx, def2.nodes)
                val segs = c.computeSegments(p1, d1, p2, d2, 30f)
                for (s in segs) {
                    minX = minOf(minX, s.startX - 16f, s.endX - 16f)
                    minY = minOf(minY, s.startY - 16f, s.endY - 16f)
                    maxX = maxOf(maxX, s.startX + 16f, s.endX + 16f)
                    maxY = maxOf(maxY, s.startY + 16f, s.endY + 16f)
                }
            }

            val cW = maxOf(maxX - minX + 40f, 180f)
            val cH = maxOf(maxY - minY + 40f, 140f)
            val scale = minOf(areaW / cW, areaH / cH, 1.15f)

            val cMidX = (minX + maxX) * 0.5f
            val cMidY = (minY + maxY) * 0.5f
            val targetMidX = (schematicAreaL + schematicAreaR) * 0.5f
            val targetMidY = (schematicAreaT + schematicAreaB) * 0.5f

            canvas.save()
            canvas.clipRect(schematicAreaL, schematicAreaT, schematicAreaR, schematicAreaB)
            canvas.translate(targetMidX, targetMidY)
            canvas.scale(scale, scale)
            canvas.translate(-cMidX, -cMidY)

            // A. Gambar Kawat Penghantar (Hitam CAD Solid)
            val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                strokeWidth = 2.4f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (c in connectors) {
                val el1 = elements.find { it.id == c.elem1Id } ?: continue
                val el2 = elements.find { it.id == c.elem2Id } ?: continue
                val def1 = NativeComponentRegistry.get(el1.svgData) ?: continue
                val def2 = NativeComponentRegistry.get(el2.svgData) ?: continue

                val p1 = el1.getWorldNodePos(c.node1Idx, def1.nodes)
                val p2 = el2.getWorldNodePos(c.node2Idx, def2.nodes)
                val d1 = el1.getWorldNodeDirection(c.node1Idx, def1.nodes)
                val d2 = el2.getWorldNodeDirection(c.node2Idx, def2.nodes)
                val segs = c.computeSegments(p1, d1, p2, d2, 30f)
                if (segs.isEmpty()) continue

                val wirePath = Path()
                wirePath.moveTo(segs.first().startX, segs.first().startY)
                for (s in segs) {
                    wirePath.lineTo(s.endX, s.endY)
                }
                canvas.drawPath(wirePath, wirePaint)
            }

            // B. Gambar Komponen (Vector Print Light Theme + Warna Indikator Lampu)
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0369A1")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val commentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 9.5f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }
            val timerPresetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            for (el in elements) {
                if (el.svgData == "text") {
                    val displayText = el.comment.ifEmpty { el.tag }
                    if (displayText.isNotEmpty()) {
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = darkenForWhitePaper(el.textColor)
                            textSize = if (el.fontSize > 0f) el.fontSize else 16f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.save()
                        canvas.translate(el.x, el.y)
                        canvas.rotate(el.rotationDegrees)
                        val textY = -(textPaint.descent() + textPaint.ascent()) * 0.5f
                        canvas.drawText(displayText, 0f, textY, textPaint)
                        canvas.restore()
                    }
                    continue
                }

                val def = NativeComponentRegistry.get(el.svgData) ?: continue
                canvas.save()
                canvas.translate(el.x, el.y)
                canvas.rotate(el.rotationDegrees)

                val printSvg = SvgComponentRenderer.getSvg(
                    context = context,
                    svgFileName = def.file,
                    frameIndex = 0,
                    variantIndex = 0,
                    lampColor = el.lampColor,
                    isLightTheme = true
                )
                if (printSvg != null) {
                    val rect = RectF(-def.width * 0.5f, -def.height * 0.5f, def.width * 0.5f, def.height * 0.5f)
                    printSvg.renderToCanvas(canvas, rect)
                }
                canvas.restore()

                val rot = ((el.rotation % 4) + 4) % 4
                val effH = if (rot == 1 || rot == 3) def.width else def.height

                // Tag Komponen
                if (el.tag.isNotEmpty()) {
                    val tagY = el.y - (effH * 0.5f) - 7f
                    canvas.drawText(el.tag, el.x, tagY, tagTextPaint)
                }
                // Preset waktu pada Timer / Flasher
                val lowSvg = el.svgData.lowercase(Locale.US)
                if ((lowSvg.startsWith("coildelay") || lowSvg == "coilflasher" || lowSvg == "flasher") && el.presetTime > 0f) {
                    val presetY = el.y - (effH * 0.5f) - 19f
                    canvas.drawText("${String.format(Locale.US, "%.1f", el.presetTime)}s", el.x, presetY, timerPresetPaint)
                }
                // Nama komponen (komentar) jika setting aktif
                if (showComponentNames && el.comment.isNotEmpty()) {
                    val commentY = el.y + (effH * 0.5f) + 14f
                    canvas.drawText(el.comment, el.x, commentY, commentTextPaint)
                }
            }

            // C. Gambar Titik Terminal & Penomoran Pin (IEC 60617 / DIN 72552)
            val pinDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            val pinDotWhiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val pinDotWhiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            val pinDotDarkRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.STROKE
                strokeWidth = 1.3f
            }
            val pinBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val pinBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val pinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val pinBadgeRect = RectF()

            for (el in elements) {
                if (el.svgData == "text") continue
                val def = NativeComponentRegistry.get(el.svgData) ?: continue
                for ((listIdx, node) in def.nodes.withIndex()) {
                    val p = el.getWorldNodePos(listIdx, def.nodes)
                    val wCount = pinWireCounts["${el.id}:$listIdx"] ?: 0

                    when {
                        wCount == 0 -> {
                            canvas.drawCircle(p.x, p.y, 2.8f, pinDotWhiteFill)
                            canvas.drawCircle(p.x, p.y, 2.8f, pinDotDarkRing)
                        }
                        wCount >= 2 -> {
                            canvas.drawCircle(p.x, p.y, 4.0f, pinDotFillPaint)
                            canvas.drawCircle(p.x, p.y, 4.0f, pinDotWhiteRing)
                        }
                        else -> {
                            canvas.drawCircle(p.x, p.y, 2.8f, pinDotFillPaint)
                            canvas.drawCircle(p.x, p.y, 2.8f, pinDotWhiteRing)
                        }
                    }

                    if (showPinLabels) {
                        val pinTag = el.getTerminalTag(listIdx, node.tag).trim()
                        if (pinTag.isNotEmpty()) {
                            val worldDir = el.getWorldNodeDirection(listIdx, def.nodes)
                            val textW = pinTextPaint.measureText(pinTag)
                            val pinRadius = if (wCount >= 2) 4.0f else 2.8f
                            val gap = 5f

                            val (tx, ty, align) = when (worldDir) {
                                1, 3 -> Triple(p.x, p.y - pinRadius - gap, Paint.Align.CENTER)
                                else -> Triple(p.x + pinRadius + gap, p.y + 3.2f, Paint.Align.LEFT)
                            }

                            val padX = 2.8f
                            val rectLeft = when (align) {
                                Paint.Align.CENTER -> tx - textW * 0.5f - padX
                                Paint.Align.RIGHT -> tx - textW - padX
                                else -> tx - padX
                            }
                            val rectTop = ty - 8.2f
                            val rectRight = rectLeft + textW + padX * 2f
                            val rectBottom = ty + 2.8f
                            pinBadgeRect.set(rectLeft, rectTop, rectRight, rectBottom)
                            canvas.drawRoundRect(pinBadgeRect, 2.5f, 2.5f, pinBadgeBgPaint)
                            canvas.drawRoundRect(pinBadgeRect, 2.5f, 2.5f, pinBadgeStrokePaint)

                            pinTextPaint.textAlign = align
                            canvas.drawText(pinTag, tx, ty, pinTextPaint)
                        }
                    }
                }
            }

            canvas.restore()
        }

        // =====================================================================
        // KOTAK ETIKET IDENTITAS SISWA + KOTAK NILAI & PARAF GURU (BAWAH HAL. 1)
        // =====================================================================
        val etiketL = pageMarginL
        val etiketW = boxW // 539f
        val etiketB = boxB // 804f
        val stripH = 17f
        val bodyT = etiketT + stripH // 688f
        val bodyH = etiketH - stripH // 116f

        val etiketBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val etiketStripBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        val etiketBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.25f
        }
        val etiketSubBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val etiketRowDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }

        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, etiketB, etiketBgPaint)
        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, bodyT, etiketStripBg)
        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, etiketB, etiketBorderPaint)
        canvas.drawLine(etiketL, bodyT, etiketL + etiketW, bodyT, etiketBorderPaint)

        val stripTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "ETIKET IDENTITAS SISWA & PENILAIAN PRAKTIKUM CAD",
            etiketL + etiketW * 0.5f,
            etiketT + 11.8f,
            stripTextPaint
        )

        // 3 Zona Kolom: Kiri (281pt) | Tengah (161pt) | Kanan Nilai & Paraf (97pt)
        val col1W = 281f
        val col2W = 161f
        val col2X = etiketL + col1W
        val col3X = col2X + col2W
        val col3W = etiketW - col1W - col2W // 97f

        canvas.drawLine(col2X, bodyT, col2X, etiketB, etiketSubBorderPaint)
        canvas.drawLine(col3X, bodyT, col3X, etiketB, etiketBorderPaint)

        // 4 Baris horizontal di Kolom Kiri & Tengah
        val rowH = bodyH / 4f // 29f
        for (r in 1..3) {
            val ry = bodyT + rowH * r
            canvas.drawLine(etiketL, ry, col3X, ry, etiketRowDivider)
        }

        // Pemisah NILAI (atas 44%) dan PARAF GURU (bawah 56%) di Kolom Kanan
        val gradeSplitY = bodyT + 51f
        canvas.drawLine(col3X, gradeSplitY, etiketL + etiketW, gradeSplitY, etiketSubBorderPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 5.8f
            typeface = Typeface.DEFAULT
        }
        val valPaintDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valPaintBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valPaintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valPaintGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#059669")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun drawField(x: Float, rowIdx: Int, label: String, value: String, maxLen: Int, paint: Paint) {
            val rTop = bodyT + rowH * rowIdx
            canvas.drawText(label, x + 8f, rTop + 10f, labelPaint)
            val clean = value.trim().ifEmpty { "-" }
            val disp = if (clean.length > maxLen) clean.substring(0, maxLen - 2) + "..." else clean
            canvas.drawText(disp, x + 8f, rTop + 22.5f, paint)
        }

        // Kolom Kiri
        drawField(etiketL, 0, "JUDUL PROYEK / TUGAS:", info.projectTitle.ifBlank { "Rangkaian Kelistrikan" }, 46, valPaintDark)
        drawField(etiketL, 1, "NAMA SISWA / PRAKTIKAN:", info.studentName, 44, valPaintBig)
        drawField(etiketL, 2, "KELAS / KOMPETENSI KEAHLIAN:", info.studentClass, 46, valPaintDark)
        drawField(etiketL, 3, "SEKOLAH / INSTANSI:", info.studentSchool, 46, valPaintDark)

        // Kolom Tengah
        drawField(col2X, 0, "TANGGAL PRAKTIKUM:", dateDisplay, 26, valPaintDark)
        drawField(col2X, 1, "NIS / NO. PRESENSI:", info.studentPresensi, 26, valPaintDark)
        drawField(col2X, 2, "TOTAL ENTITAS SIRKUIT:", "$realComponentCount Komponen  |  ${connectors.size} Kabel", 30, valPaintBlue)
        drawField(col2X, 3, "STATUS DOKUMEN:", "TERVERIFIKASI CAD", 26, valPaintGreen)

        // Kolom Kanan: NILAI (atas) & PARAF GURU (bawah)
        val col3Center = col3X + col3W * 0.5f
        val gradeHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 6.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("NILAI", col3Center, bodyT + 12f, gradeHeaderPaint)
        canvas.drawText("PARAF GURU", col3Center, gradeSplitY + 12f, gradeHeaderPaint)
        // Garis tipis tanda tangan guru
        canvas.drawLine(col3X + 14f, etiketB - 12f, etiketL + etiketW - 14f, etiketB - 12f, etiketRowDivider)

        drawPageFootnote(canvas, dateDisplay, 1, totalPages)
    }

    // =========================================================================
    // 8. RENDERER HALAMAN BOM MULTI-HALAMAN (WORD-WRAP PENUH & BEBAS BINGKAI)
    // =========================================================================
    private fun renderBomPagePortrait(
        context: Context,
        canvas: Canvas,
        rows: List<WrappedBomRow>,
        info: StudentPdfInfo,
        dateDisplay: String,
        pageNumber: Int,
        totalPages: Int,
        isLastPage: Boolean,
        totalUniqueTypes: Int,
        totalUnitCount: Int
    ) {
        val w = 595f
        canvas.drawColor(Color.WHITE)

        val pageMarginL = 28f
        val pageMarginT = 28f
        val boxW = 539f
        val boxR = pageMarginL + boxW // 567f

        drawPageFrame(canvas)

        // Header Bar BOM (identik Halaman 1)
        val headerH = 48f
        val headerBottom = pageMarginT + headerH // 76f
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, headerBgPaint)
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, borderPaint)

        val bomSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cleanTitle = info.projectTitle.trim().ifEmpty { "RANGKAIAN KELISTRIKAN" }.uppercase(Locale.US)
        val subText = "PROYEK: " + if (cleanTitle.length > 52) cleanTitle.substring(0, 50) + "..." else cleanTitle
        canvas.drawText(subText, pageMarginL + 13f, pageMarginT + 15f, bomSubtitlePaint)

        val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LEMBAR $pageNumber", boxR - 13f, pageMarginT + 15f, pageIndicatorPaint)

        val bomTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DAFTAR KOMPONEN & SPESIFIKASI (BILL OF MATERIALS)", w * 0.5f, pageMarginT + 37.5f, bomTitlePaint)

        // Tabel BOM ditempatkan RAPI DI DALAM bingkai aksen dalam (x = 41..554 pt, total 513 pt)
        val tableLeft = 41f
        val colWNo = 25f
        val colWIcon = 60f
        val colWName = 136f
        val colWTag = 77f
        val colWSpecs = 176f
        val colWCount = 39f
        val tableRight = tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount // 554f

        val tableHeaderTop = 88f
        val tableHeaderH = 22f

        val tblHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRect(tableLeft, tableHeaderTop, tableRight, tableHeaderTop + tableHeaderH, tblHeaderBg)

        val tblHeaderTextCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val tblHeaderDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        var hx = tableLeft
        hx += colWNo; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWIcon; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWName; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWTag; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWSpecs; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)

        val hTextY = tableHeaderTop + 14.2f
        canvas.drawText("No", tableLeft + colWNo * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Simbol", tableLeft + colWNo + colWIcon * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Nama Komponen", tableLeft + colWNo + colWIcon + colWName * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Tag", tableLeft + colWNo + colWIcon + colWName + colWTag * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Spesifikasi / Keterangan", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Jml", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount * 0.5f, hTextY, tblHeaderTextCenter)

        // Baris Data BOM
        var rowY = tableHeaderTop + tableHeaderH
        val lineHeight = 10.5f
        val rowEvenBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val rowOddBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        val cellTextCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.4f
            textAlign = Paint.Align.CENTER
        }
        val cellTextBoldLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val cellTextSpecLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 7.4f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val cellTextTagCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cellTextCountCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun drawWrappedCell(lines: List<String>, x: Float, top: Float, height: Float, paint: Paint) {
            val totalTextH = lines.size * lineHeight
            var baselineY = top + (height - totalTextH) * 0.5f + 8.2f
            for (line in lines) {
                canvas.drawText(line, x, baselineY, paint)
                baselineY += lineHeight
            }
        }

        if (rows.isEmpty()) {
            val emptyRowH = 34f
            canvas.drawRect(tableLeft, rowY, tableRight, rowY + emptyRowH, rowEvenBg)
            canvas.drawRect(tableLeft, rowY, tableRight, rowY + emptyRowH, gridLinePaint)
            canvas.drawText("Tidak ada komponen listrik terdeteksi", (tableLeft + tableRight) * 0.5f, rowY + 20f, cellTextCenter)
            rowY += emptyRowH
        } else {
            for ((idx, wRow) in rows.withIndex()) {
                val rH = wRow.rowHeight
                val bg = if (idx % 2 == 0) rowEvenBg else rowOddBg
                canvas.drawRect(tableLeft, rowY, tableRight, rowY + rH, bg)
                canvas.drawLine(tableLeft, rowY + rH, tableRight, rowY + rH, gridLinePaint)

                // Garis Grid Vertikal
                var vx = tableLeft
                canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                vx += colWNo; canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                vx += colWIcon; canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                vx += colWName; canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                vx += colWTag; canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                vx += colWSpecs; canvas.drawLine(vx, rowY, vx, rowY + rH, gridLinePaint)
                canvas.drawLine(tableRight, rowY, tableRight, rowY + rH, gridLinePaint)

                val centerY = rowY + rH * 0.5f + 2.8f

                // 1. No
                canvas.drawText("${wRow.itemIndex}", tableLeft + colWNo * 0.5f, centerY, cellTextCenter)

                // 2. Simbol CAD Transparan
                val thumb = getPrintThumbnailBitmap(context, wRow.item.svgData, 120)
                if (thumb != null) {
                    val iconSize = 26f
                    val iconLeft = tableLeft + colWNo + (colWIcon - iconSize) * 0.5f
                    val iconTop = rowY + (rH - iconSize) * 0.5f
                    val destRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    canvas.drawBitmap(thumb, null, destRect, bitmapPaint)
                }

                // 3. Nama Komponen (Multi-Line Word Wrap)
                drawWrappedCell(wRow.nameLines, tableLeft + colWNo + colWIcon + 6f, rowY, rH, cellTextBoldLeft)

                // 4. Tag (Multi-Line Word Wrap)
                drawWrappedCell(wRow.tagLines, tableLeft + colWNo + colWIcon + colWName + colWTag * 0.5f, rowY, rH, cellTextTagCenter)

                // 5. Spesifikasi (Multi-Line Word Wrap)
                drawWrappedCell(wRow.specLines, tableLeft + colWNo + colWIcon + colWName + colWTag + 6f, rowY, rH, cellTextSpecLeft)

                // 6. Jumlah
                canvas.drawText("${wRow.item.count}", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount * 0.5f, centerY, cellTextCountCenter)

                rowY += rH
            }
        }

        // Baris Ringkasan Total pada halaman terakhir BOM
        if (isLastPage) {
            val footH = 24f
            val footBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val footBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                strokeWidth = 0.8f
                style = Paint.Style.STROKE
            }
            val footLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val footValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0284C7")
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val splitX = tableRight - colWCount
            canvas.drawRect(tableLeft, rowY, tableRight, rowY + footH, footBg)
            canvas.drawRect(tableLeft, rowY, tableRight, rowY + footH, footBorder)
            canvas.drawLine(splitX, rowY, splitX, rowY + footH, footBorder)

            canvas.drawText(
                "TOTAL KOMPONEN LISTRIK ($totalUniqueTypes Jenis Komponen)",
                splitX - 8f,
                rowY + 15f,
                footLabelPaint
            )
            canvas.drawText(
                "$totalUnitCount",
                splitX + colWCount * 0.5f,
                rowY + 15f,
                footValPaint
            )
        }

        drawPageFootnote(canvas, dateDisplay, pageNumber, totalPages)
    }

    // =========================================================================
    // 9. PENYIMPANAN KE FOLDER DOWNLOADS ANDROID
    // =========================================================================
    fun savePdfToDownloads(context: Context, filename: String, pdfBytes: ByteArray): File? {
        val cleanFilename = if (filename.endsWith(".pdf", ignoreCase = true)) filename else "$filename.pdf"
        var localFile: File? = null

        try {
            val appDocDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir
            if (!appDocDir.exists()) {
                appDocDir.mkdirs()
            }
            val appFile = File(appDocDir, cleanFilename)
            FileOutputStream(appFile).use { fos ->
                fos.write(pdfBytes)
                fos.flush()
            }
            localFile = appFile
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(pdfBytes)
                        os.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    val publicFile = File(downloadsDir, cleanFilename)
                    FileOutputStream(publicFile).use { fos ->
                        fos.write(pdfBytes)
                        fos.flush()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return localFile
    }
}
