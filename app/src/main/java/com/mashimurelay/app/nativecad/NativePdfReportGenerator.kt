package com.mashimurelay.app.nativecad

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    var count: Int,
    val specs: String,
    val typeCode: String = ""
)

object NativePdfReportGenerator {

    private val printSvgCache = mutableMapOf<String, SVG>()
    private val printThumbnailCache = mutableMapOf<String, Bitmap>()

    fun clearCache() {
        printSvgCache.clear()
        printThumbnailCache.values.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        printThumbnailCache.clear()
    }

    // =========================================================================
    // 1. KAMUS STANDAR VOKASI KELISTRIKAN (INDONESIAN VOCATIONAL DICTIONARY)
    // =========================================================================
    private fun getVocationalName(svgData: String): String {
        val s = svgData.lowercase(Locale.US)
        if (s.contains("flasher")) return "Flasher Elektronik (Lampu Sein)"
        if (s.contains("battery") || s.contains("dcpower")) return "Baterai / Akumulator (Sumber DC)"
        if (s.contains("horn") || s.contains("klakson")) return "Klakson Listrik (Electric Horn)"
        if (s.contains("delayon") || s.contains("ton")) return "Timer On-Delay Relay (TON)"
        if (s.contains("delayoff") || s.contains("tof")) return "Timer Off-Delay Relay (TOF)"
        if (s.contains("relaycoil") || s == "coil") return "Kumparan Relay Elektromagnetik"
        if (s.contains("lampblink")) return "Lampu Indikator Kedip (Blinker)"
        if (s.contains("lamp")) return "Lampu Filamen / Indikator (Pilot Lamp)"
        if (s.contains("buttonno") || s == "button_no" || s == "pushbtnno") return "Tombol Tekan NO (Push Button Normally Open)"
        if (s.contains("buttonnc") || s == "button_nc" || s == "pushbtnnc") return "Tombol Tekan NC (Push Button Normally Closed)"
        if (s.contains("emergency") || s == "pushbtnemerg") return "Tombol Darurat (Emergency Stop Switch)"
        if (s.contains("fuse")) return "Sekring Pengaman (Blade Fuse)"
        if (s.contains("overload") || s.contains("tor") || s.contains("contacttor")) return "Thermal Overload Relay (TOR)"
        if (s.contains("breaker") || s.contains("mcb")) return "Miniature Circuit Breaker (MCB)"
        if (s.contains("motor3p") || s == "motor3") return "Motor Listrik 3 Fasa AC"
        if (s.contains("motordc") || s.contains("starter")) return "Motor Listrik DC / Motor Starter"
        if (s.contains("wiper")) return "Motor Wiper Kaca Depan"
        if (s.contains("contactor")) return "Kontaktor Utama (Magnetic Contactor)"
        if (s.contains("ground")) return "Terminal Massa Rangka (Chassis Ground)"
        if (s.contains("switch3p") || s.contains("hazard")) return "Saklar Kombinasi Sein & Hazard"
        if (s.contains("switch") || s.contains("key")) return "Kunci Kontak / Saklar Utama"

        val def = NativeComponentRegistry.get(svgData)
        if (def != null && def.name.isNotEmpty()) return def.name
        return svgData
    }

    private fun getComponentTypeCode(svgData: String): String {
        return when {
            svgData.startsWith("coilDelay") -> "TDR / IEC 60947"
            svgData.startsWith("coil") || svgData == "relayAutomotive" -> "AC-3 / IEC 60947"
            svgData.startsWith("contactTor") || svgData == "tor" -> "Bimetal / IEC 60947"
            svgData.startsWith("contact") -> "Aux / Power Contact"
            svgData.startsWith("pushBtn") || svgData.startsWith("switch") -> "Control Switch"
            svgData.startsWith("motor") -> "Induction Motor"
            svgData.startsWith("fuse") -> "gG / gL Protection"
            svgData.startsWith("breaker") -> "MCB / IEC 60898"
            svgData.startsWith("power") || svgData.startsWith("source") || svgData == "battery" -> "Power Source"
            svgData.startsWith("lamp") -> "Optical Indicator"
            svgData == "flasher" -> "Automotive Flasher"
            else -> "Industrial CAD"
        }
    }

    private fun getComponentSpecs(el: NativeElement): String {
        val s = el.svgData.lowercase(Locale.US)
        val isTimer = s.contains("delay") || s.contains("timer") || s.contains("flasher")
        if (isTimer && el.presetTime > 0f) {
            return "Preset Waktu: ${String.format(Locale.US, "%.1f", el.presetTime)} s"
        }
        if (el.lampColor.isNotEmpty() && (s.contains("lamp") || s.contains("indicator"))) {
            return "Warna Indikator: ${el.lampColor.uppercase(Locale.US)} (12V DC)"
        }
        if (s.contains("battery") || s.contains("dcpower") || s.contains("source")) {
            return "12V DC Sumber Catu Daya"
        }
        if (s.contains("fuse")) {
            return "Sekring Pengaman (Blade Fuse)"
        }
        if (s.contains("relay") || s.contains("coil")) {
            return "12V DC Kumparan Elektromagnetik"
        }
        if (s.contains("button") || s.contains("pushbtn")) {
            return "Saklar Tekan Pegas (Momentary)"
        }
        if (s.contains("horn") || s.contains("buzzer") || s.contains("bell") || s.contains("siren")) {
            return "12V DC Indikator Suara"
        }
        val def = NativeComponentRegistry.get(el.svgData)
        if (def != null && def.defaultComment.isNotEmpty()) {
            return def.defaultComment
        }
        return "Standar CAD Industri"
    }

    // =========================================================================
    // 2. VECTOR PRINT SVG LOADER (PENGUBAH STROKE CAD GELAP KONTRASTING)
    // =========================================================================
    fun getPrintSvg(context: Context, svgFileName: String, frameIndex: Int = 0, variantIndex: Int = 0): SVG? {
        val cacheKey = "${svgFileName}_print_f${frameIndex}_v${variantIndex}"
        if (printSvgCache.containsKey(cacheKey)) {
            return printSvgCache[cacheKey]
        }

        val raw = SvgComponentRenderer.getRawSvgText(context, svgFileName) ?: return null

        // Bersihkan warna stroke putih/terang agar menjadi hitam CAD (#0f172a)
        val inverted = raw
            .replace("#ffffff", "#0f172a", ignoreCase = true)
            .replace("#f9f9f9", "#0f172a", ignoreCase = true)
            .replace("#f8fafc", "#0f172a", ignoreCase = true)
            .replace("#00ffff", "#0284c7", ignoreCase = true)
            .replace("#cbd5e1", "#0f172a", ignoreCase = true)
            .replace("#94a3b8", "#334155", ignoreCase = true)
            .replace("stroke:white", "stroke:#0f172a", ignoreCase = true)
            .replace("stroke: white", "stroke: #0f172a", ignoreCase = true)
            .replace("stroke:#ffffff", "stroke:#0f172a", ignoreCase = true)
            .replace("stroke: #ffffff", "stroke: #0f172a", ignoreCase = true)
            .replace("stroke-width:2", "stroke-width:2.4", ignoreCase = true)

        try {
            val svg = SVG.getFromString(inverted)
            printSvgCache[cacheKey] = svg
            return svg
        } catch (_: Exception) {
            val fallback = SvgComponentRenderer.getSvg(context, svgFileName, frameIndex, variantIndex)
            if (fallback != null) {
                printSvgCache[cacheKey] = fallback
                return fallback
            }
        }
        return null
    }

    // =========================================================================
    // 3. THUMBNAIL BITMAP GENERATOR (1:1 BUJURSANGKAR SERAGAM)
    // =========================================================================
    fun getPrintThumbnailBitmap(context: Context, svgFileName: String, sizePx: Int = 120): Bitmap? {
        if (printThumbnailCache.containsKey(svgFileName)) {
            val cached = printThumbnailCache[svgFileName]
            if (cached != null && !cached.isRecycled) return cached
        }

        val svg = getPrintSvg(context, svgFileName, 0, 0) ?: return null
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

        printThumbnailCache[svgFileName] = finalBitmap
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
    // 4. GENERATOR PDF MULTI-HALAMAN UTAMA (A4 PORTRAIT STANDAR CAD)
    // =========================================================================
    fun generatePdfBytes(
        context: Context,
        elements: List<NativeElement>,
        connectors: List<NativeConnector>,
        info: StudentPdfInfo
    ): ByteArray {
        val document = PdfDocument()

        // 1. Ekstraksi BOM list lebih awal untuk menghitung total halaman dokumen
        val bomGroups = mutableMapOf<String, BomGroupItem>()
        for (el in elements) {
            // Label teks CAD tidak dimasukkan ke dalam Bill of Materials
            if (el.svgData == "text") continue
            val key = el.svgData
            if (!bomGroups.containsKey(key)) {
                val name = getVocationalName(el.svgData)
                val typeCode = getComponentTypeCode(el.svgData)
                val specs = getComponentSpecs(el)
                bomGroups[key] = BomGroupItem(
                    svgData = el.svgData,
                    name = name,
                    tags = mutableListOf(),
                    count = 0,
                    specs = specs,
                    typeCode = typeCode
                )
            }
            val item = bomGroups[key]!!
            item.count += 1
            if (el.tag.isNotEmpty() && !item.tags.contains(el.tag)) {
                item.tags.add(el.tag)
            }
        }
        val bomList = bomGroups.values.toList()
        val chunkSize = 18
        val bomChunks = if (bomList.isEmpty()) listOf(emptyList()) else bomList.chunked(chunkSize)
        val totalPages = 1 + bomChunks.size

        // ---------------------------------------------------------------------
        // HALAMAN 1 (PORTRAIT A4: 595 pt x 842 pt) — DIAGRAM SKEMATIK CAD & ETIKET
        // ---------------------------------------------------------------------
        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = document.startPage(page1Info)
        renderPage1Portrait(context, page1.canvas, elements, connectors, info, totalPages)
        document.finishPage(page1)

        // ---------------------------------------------------------------------
        // HALAMAN 2+ (PORTRAIT A4: 595 pt x 842 pt) — BILL OF MATERIALS (PAGINATED)
        // ---------------------------------------------------------------------
        for ((chunkIdx, chunk) in bomChunks.withIndex()) {
            val pageNum = 2 + chunkIdx
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            val page = document.startPage(pageInfo)
            renderBomPagePortrait(context, page.canvas, chunk, chunkIdx, chunkSize, info, pageNum, totalPages)
            document.finishPage(page)
        }

        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        clearCache()
        return out.toByteArray()
    }

    // =========================================================================
    // 5. RENDERER HALAMAN 1 (PORTRAIT A4: SKEMATIK CAD & ETIKET SISWA DI BAWAH)
    // =========================================================================
    private fun renderPage1Portrait(
        context: Context,
        canvas: Canvas,
        elements: List<NativeElement>,
        connectors: List<NativeConnector>,
        info: StudentPdfInfo,
        totalPages: Int
    ) {
        val w = 595f
        val h = 842f

        // Latar Belakang Putih Bersih
        canvas.drawColor(Color.WHITE)

        val showComponentNames = SettingsManager.isComponentNamesEnabled(context)
        val pageMarginL = 28f
        val pageMarginT = 28f
        val boxW = 539f
        val boxH = 776f
        val boxR = pageMarginL + boxW // 567f
        val boxB = pageMarginT + boxH // 804f

        // Bingkai Luar Gambar Teknik
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, boxB, outerBorderPaint)

        // Garis Aksen Ganda
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        canvas.drawRect(pageMarginL + 4.2f, pageMarginT + 4.2f, boxR - 4.2f, boxB - 4.2f, innerBorderPaint)

        // Header Bar Skematik (Bagian Atas Halaman 1)
        val headerH = 45f
        val headerBottom = pageMarginT + headerH // 73f
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, headerBgPaint)
        canvas.drawLine(pageMarginL, headerBottom, boxR, headerBottom, outerBorderPaint)

        // Teks Header Atas
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MASHIMURELAY SIMULATOR", pageMarginL + 14f, pageMarginT + 27f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cleanTitle = info.projectTitle.trim().ifEmpty { "RANGKAIAN SKEMATIK KELISTRIKAN" }.uppercase(Locale.US)
        val headerDisplayTitle = if (cleanTitle.length > 34) cleanTitle.substring(0, 32) + "..." else cleanTitle
        canvas.drawText(headerDisplayTitle, w * 0.5f, pageMarginT + 28f, titlePaint)

        val docTypePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LEMBAR KERJA SKEMATIK CAD", boxR - 14f, pageMarginT + 27f, docTypePaint)

        // Bounding Box Sirkuit Skematik (Area Tengah: y = 79f s.d. 670f)
        val schematicAreaL = pageMarginL + 8f
        val schematicAreaT = headerBottom + 6f
        val schematicAreaR = boxR - 8f
        val etiketH = 128f
        val etiketT = boxB - etiketH // 676f
        val schematicAreaB = etiketT - 6f
        val areaW = schematicAreaR - schematicAreaL
        val areaH = schematicAreaB - schematicAreaT

        if (elements.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Kanvas sirkuit masih kosong. Tidak ada komponen yang digambar.", w * 0.5f, (schematicAreaT + schematicAreaB) * 0.5f, emptyPaint)
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            for (el in elements) {
                val def = NativeComponentRegistry.get(el.svgData)
                val halfW = (def?.width ?: 60f) * 0.5f + 15f
                val halfH = (def?.height ?: 60f) * 0.5f + 15f
                minX = minOf(minX, el.x - halfW)
                minY = minOf(minY, el.y - halfH)
                maxX = maxOf(maxX, el.x + halfW)
                maxY = maxOf(maxY, el.y + halfH)
            }

            for (c in connectors) {
                val el1 = elements.find { it.id == c.elem1Id }
                val el2 = elements.find { it.id == c.elem2Id }
                if (el1 != null && el2 != null) {
                    val def1 = NativeComponentRegistry.get(el1.svgData)
                    val def2 = NativeComponentRegistry.get(el2.svgData)
                    if (def1 != null && def2 != null) {
                        val p1 = el1.getWorldNodePos(c.node1Idx, def1.nodes)
                        val p2 = el2.getWorldNodePos(c.node2Idx, def2.nodes)
                        val d1 = el1.getWorldNodeDirection(c.node1Idx, def1.nodes)
                        val d2 = el2.getWorldNodeDirection(c.node2Idx, def2.nodes)
                        val segs = c.computeSegments(p1, d1, p2, d2, 30f)
                        for (s in segs) {
                            minX = minOf(minX, s.startX, s.endX)
                            minY = minOf(minY, s.startY, s.endY)
                            maxX = maxOf(maxX, s.startX, s.endX)
                            maxY = maxOf(maxY, s.startY, s.endY)
                        }
                    }
                }
            }

            val cW = maxOf(maxX - minX + 80f, 150f)
            val cH = maxOf(maxY - minY + 80f, 120f)
            val scale = minOf(areaW / cW, areaH / cH, 1.25f)

            val cMidX = (minX + maxX) * 0.5f
            val cMidY = (minY + maxY) * 0.5f
            val targetMidX = (schematicAreaL + schematicAreaR) * 0.5f
            val targetMidY = (schematicAreaT + schematicAreaB) * 0.5f

            canvas.save()
            canvas.translate(targetMidX, targetMidY)
            canvas.scale(scale, scale)
            canvas.translate(-cMidX, -cMidY)

            // A. Gambar Kawat Penghantar (Connectors - Hitam CAD Tegak Lurus)
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

                val wirePath = android.graphics.Path()
                wirePath.moveTo(segs.first().startX, segs.first().startY)
                for (s in segs) {
                    wirePath.lineTo(s.endX, s.endY)
                }
                canvas.drawPath(wirePath, wirePaint)
            }

            // B. Gambar Komponen (Vector Print SVGs atau Text Langsung)
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0284C7")
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

            for (el in elements) {
                // Perbaikan Komponen "Text": Render langsung teks pengguna tanpa dummy text.svg
                if (el.svgData == "text") {
                    val displayText = el.comment.ifEmpty { el.tag.ifEmpty { "" } }
                    if (displayText.isNotEmpty()) {
                        val rawColor = el.textColor.ifEmpty { "#0F172A" }
                        val printColor = if (rawColor.equals("#ffffff", true) || rawColor.equals("#f8fafc", true) || rawColor.equals("white", true)) {
                            Color.parseColor("#0F172A") // Kontras hitam CAD di atas kertas putih
                        } else if (rawColor.equals("#fbbf24", true) || rawColor.equals("yellow", true)) {
                            Color.parseColor("#D97706") // Amber gelap agar terbaca jelas di atas kertas putih
                        } else {
                            try {
                                Color.parseColor(rawColor)
                            } catch (_: Exception) {
                                when (rawColor.lowercase()) {
                                    "red" -> Color.parseColor("#DC2626")
                                    "green" -> Color.parseColor("#16A34A")
                                    "gray" -> Color.parseColor("#64748B")
                                    else -> Color.parseColor("#0284C7")
                                }
                            }
                        }

                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = printColor
                            textSize = if (el.fontSize > 0f) el.fontSize else 18f
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

                val printSvg = getPrintSvg(context, def.file, frameIndex = 0, variantIndex = 0)
                if (printSvg != null) {
                    val rect = RectF(-def.width * 0.5f, -def.height * 0.5f, def.width * 0.5f, def.height * 0.5f)
                    printSvg.renderToCanvas(canvas, rect)
                }
                canvas.restore()

                // Tag & Komentar (Selalu tegak lurus untuk keterbacaan)
                if (el.tag.isNotEmpty()) {
                    val tagY = el.y - (def.height * 0.5f) - 6f
                    canvas.drawText(el.tag, el.x, tagY, tagTextPaint)
                }
                if (showComponentNames && el.comment.isNotEmpty()) {
                    val commentY = el.y + (def.height * 0.5f) + 14f
                    canvas.drawText(el.comment, el.x, commentY, commentTextPaint)
                }
            }

            canvas.restore()
        }

        // =====================================================================
        // KOTAK ETIKET IDENTITAS SISWA (BAGIAN BAWAH HALAMAN 1 — STANDAR GAMBAR TEKNIK)
        // =====================================================================
        val etiketL = pageMarginL
        val etiketW = boxW
        val etiketB = boxB

        val etiketBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
        }
        val etiketDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val etiketBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, etiketB, etiketBgPaint)
        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, etiketB, etiketBorderPaint)

        // Banner Header Etiket (Abu-abu Terang #F1F5F9)
        val bannerH = 17f
        val etiketHeaderStripBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        canvas.drawRect(etiketL, etiketT, etiketL + etiketW, etiketT + bannerH, etiketHeaderStripBg)
        canvas.drawLine(etiketL, etiketT + bannerH, etiketL + etiketW, etiketT + bannerH, etiketBorderPaint)

        val etiketBannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ETIKET IDENTITAS SISWA & LEMBAR PRAKTIKUM CAD", etiketL + etiketW * 0.5f, etiketT + 11.5f, etiketBannerText)

        // Pemisah Vertikal Antara Kolom Kiri dan Kanan (~59% : 41%)
        val midX = etiketL + 318f
        canvas.drawLine(midX, etiketT + bannerH, midX, etiketB, etiketDividerPaint)

        // Garis Pembatas Baris Horizontal (4 Baris Setara ~27.75 pt)
        val rowH = (etiketH - bannerH) / 4f
        val r1Top = etiketT + bannerH
        val r2Top = r1Top + rowH
        val r3Top = r2Top + rowH
        val r4Top = r3Top + rowH

        canvas.drawLine(etiketL, r2Top, etiketL + etiketW, r2Top, etiketDividerPaint)
        canvas.drawLine(etiketL, r3Top, etiketL + etiketW, r3Top, etiketDividerPaint)
        canvas.drawLine(etiketL, r4Top, etiketL + etiketW, r4Top, etiketDividerPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 6f
            typeface = Typeface.DEFAULT
        }
        val valBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valBoldBigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#059669")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Kolom Kiri: Data Siswa & Proyek (Format Dua Tingkat)
        val colLeftX = etiketL + 8f

        // Baris 1: Judul Proyek
        canvas.drawText("JUDUL PROYEK / TUGAS:", colLeftX, r1Top + 9.5f, labelPaint)
        val pTitle = info.projectTitle.trim().ifEmpty { "RANGKAIAN KELISTRIKAN" }
        val cleanPTitle = if (pTitle.length > 50) pTitle.substring(0, 48) + "..." else pTitle
        canvas.drawText(cleanPTitle, colLeftX, r1Top + 20.5f, valBoldPaint)

        // Baris 2: Nama Siswa
        canvas.drawText("NAMA SISWA / PRAKTIKAN:", colLeftX, r2Top + 9.5f, labelPaint)
        val sName = info.studentName.trim().ifEmpty { "-" }
        val cleanSName = if (sName.length > 48) sName.substring(0, 46) + "..." else sName
        canvas.drawText(cleanSName, colLeftX, r2Top + 20.5f, valBoldBigPaint)

        // Baris 3: Kelas & Jurusan
        canvas.drawText("KELAS / KOMPETENSI KEAHLIAN:", colLeftX, r3Top + 9.5f, labelPaint)
        val sClass = info.studentClass.trim().ifEmpty { "-" }
        val cleanSClass = if (sClass.length > 48) sClass.substring(0, 46) + "..." else sClass
        canvas.drawText(cleanSClass, colLeftX, r3Top + 20.5f, valBoldPaint)

        // Baris 4: Sekolah / Instansi
        canvas.drawText("SEKOLAH / INSTANSI PENDIDIKAN:", colLeftX, r4Top + 9.5f, labelPaint)
        val sSchool = info.studentSchool.trim().ifEmpty { "SMK Vokasi / Teknik Tenaga Listrik" }
        val cleanSSchool = if (sSchool.length > 48) sSchool.substring(0, 46) + "..." else sSchool
        canvas.drawText(cleanSSchool, colLeftX, r4Top + 20.5f, valBoldPaint)

        // Kolom Kanan: Tanggal, Presensi, Status CAD & Total Entitas
        val colRightX = midX + 8f
        val dateDisplay = if (info.practiceDate.isNotEmpty()) info.practiceDate else SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())

        // Baris 1: Tanggal Praktikum
        canvas.drawText("TANGGAL PRAKTIKUM:", colRightX, r1Top + 9.5f, labelPaint)
        val cleanDate = if (dateDisplay.length > 32) dateDisplay.substring(0, 30) + "..." else dateDisplay
        canvas.drawText(cleanDate, colRightX, r1Top + 20.5f, valBoldPaint)

        // Baris 2: NIS / No. Presensi
        canvas.drawText("NIS / NO. PRESENSI:", colRightX, r2Top + 9.5f, labelPaint)
        val sPres = info.studentPresensi.trim().ifEmpty { "-" }
        val cleanPres = if (sPres.length > 32) sPres.substring(0, 30) + "..." else sPres
        canvas.drawText(cleanPres, colRightX, r2Top + 20.5f, valBoldPaint)

        // Baris 3: Status Dokumen CAD
        canvas.drawText("STATUS DOKUMEN CAD:", colRightX, r3Top + 9.5f, labelPaint)
        canvas.drawText("TERVERIFIKASI CAD RESMI", colRightX, r3Top + 20.5f, valGreenPaint)

        // Baris 4: Total Entitas Sirkuit
        canvas.drawText("TOTAL ENTITAS SIRKUIT:", colRightX, r4Top + 9.5f, labelPaint)
        val circuitSummary = "${elements.size} Komponen  |  ${connectors.size} Jalur Kabel"
        canvas.drawText(circuitSummary, colRightX, r4Top + 20.5f, valBluePaint)

        // Footnote Halaman 1 di Bawah Etiket (Di luar kotak bingkai luar)
        val footnoteLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Dokumen ini dihasilkan secara otomatis oleh Mashimurelay CAD Simulator • Tanggal Cetak: $dateDisplay", pageMarginL + 4f, 820f, footnoteLeft)

        val footnoteRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Halaman 1 dari $totalPages", boxR - 4f, 820f, footnoteRight)
    }

    // =========================================================================
    // 6. RENDERER HALAMAN BOM MULTI-HALAMAN (PORTRAIT A4: BEBAS TABRAKAN)
    // =========================================================================
    private fun renderBomPagePortrait(
        context: Context,
        canvas: Canvas,
        chunkItems: List<BomGroupItem>,
        chunkIndex: Int,
        chunkSize: Int,
        info: StudentPdfInfo,
        pageNumber: Int,
        totalPages: Int
    ) {
        val w = 595f
        val h = 842f

        // Latar Belakang Putih
        canvas.drawColor(Color.WHITE)

        val pageMarginL = 28f
        val pageMarginT = 28f
        val boxW = 539f
        val boxH = 776f
        val boxR = pageMarginL + boxW // 567f
        val boxB = pageMarginT + boxH // 804f

        // Bingkai Luar Gambar Teknik
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, boxB, outerBorderPaint)

        // Garis Aksen Ganda
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        canvas.drawRect(pageMarginL + 4.2f, pageMarginT + 4.2f, boxR - 4.2f, boxB - 4.2f, innerBorderPaint)

        // Header Bar BOM
        val headerH = 45f
        val headerBottom = pageMarginT + headerH // 73f
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRect(pageMarginL, pageMarginT, boxR, headerBottom, headerBgPaint)
        canvas.drawLine(pageMarginL, headerBottom, boxR, headerBottom, outerBorderPaint)

        val bomTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DAFTAR KOMPONEN & SPESIFIKASI (BILL OF MATERIALS)", w * 0.5f, pageMarginT + 20f, bomTitlePaint)

        val bomSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cleanTitle = info.projectTitle.trim().ifEmpty { "RANGKAIAN KELISTRIKAN" }.uppercase(Locale.US)
        val subText = "PROYEK: " + (if (cleanTitle.length > 55) cleanTitle.substring(0, 53) + "..." else cleanTitle)
        canvas.drawText(subText, pageMarginL + 12f, pageMarginT + 38f, bomSubtitlePaint)

        val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("LEMBAR $pageNumber", boxR - 12f, pageMarginT + 38f, pageIndicatorPaint)

        // Definisi Lebar Kolom Tabel (Total Lebar = 539 pt, persis dari x=28f sampai x=567f)
        val tableLeft = pageMarginL
        val colWNo = 26f
        val colWIcon = 62f
        val colWName = 142f
        val colWTag = 74f
        val colWSpecs = 195f
        val colWCount = 40f
        val tableRight = tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount // 567f

        val tableHeaderTop = 74f
        val tableHeaderH = 22f

        // Gambar Header Tabel (Latar Gelap #0F172A)
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
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }

        // Garis Pembatas Vertikal di Header Tabel
        var hx = tableLeft
        hx += colWNo; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWIcon; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWName; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWTag; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)
        hx += colWSpecs; canvas.drawLine(hx, tableHeaderTop, hx, tableHeaderTop + tableHeaderH, tblHeaderDivider)

        val hTextY = tableHeaderTop + 14.5f
        canvas.drawText("No", tableLeft + colWNo * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Simbol CAD", tableLeft + colWNo + colWIcon * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Nama Komponen", tableLeft + colWNo + colWIcon + colWName * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Tag / Simbol", tableLeft + colWNo + colWIcon + colWName + colWTag * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Spesifikasi / Keterangan", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs * 0.5f, hTextY, tblHeaderTextCenter)
        canvas.drawText("Jumlah", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount * 0.5f, hTextY, tblHeaderTextCenter)

        // Baris-baris Data Tabel BOM (Grid Kotak-Kotak Penuh)
        val rowHeight = 36f
        var rowY = tableHeaderTop + tableHeaderH
        val rowEvenBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val rowOddBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }

        val cellTextCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        val cellTextBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellTextNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.5f
        }
        val cellTextTagCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cellTextCountCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for ((idx, item) in chunkItems.withIndex()) {
            val bg = if (idx % 2 == 0) rowEvenBg else rowOddBg
            canvas.drawRect(tableLeft, rowY, tableRight, rowY + rowHeight, bg)

            // Garis Grid Horizontal Bawah
            canvas.drawLine(tableLeft, rowY + rowHeight, tableRight, rowY + rowHeight, gridLinePaint)

            // Garis Grid Vertikal Pembatas Sel
            var vx = tableLeft
            canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            vx += colWNo; canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            vx += colWIcon; canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            vx += colWName; canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            vx += colWTag; canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            vx += colWSpecs; canvas.drawLine(vx, rowY, vx, rowY + rowHeight, gridLinePaint)
            canvas.drawLine(tableRight, rowY, tableRight, rowY + rowHeight, gridLinePaint)

            val itemIndex = chunkIndex * chunkSize + idx + 1
            val textY = rowY + 22f

            // 1. No (Rata Tengah)
            canvas.drawText("$itemIndex", tableLeft + colWNo * 0.5f, textY, cellTextCenter)

            // 2. Simbol CAD Thumbnail (Tengah Sel)
            val thumb = getPrintThumbnailBitmap(context, item.svgData, 120)
            if (thumb != null) {
                val iconSize = 26f
                val iconLeft = tableLeft + colWNo + (colWIcon - iconSize) * 0.5f
                val iconTop = rowY + (rowHeight - iconSize) * 0.5f
                val destRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                canvas.drawBitmap(thumb, null, destRect, bitmapPaint)
            }

            // 3. Nama Komponen (Rata Kiri)
            val cleanName = if (item.name.length > 28) item.name.substring(0, 26) + "..." else item.name
            canvas.drawText(cleanName, tableLeft + colWNo + colWIcon + 6f, textY, cellTextBold)

            // 4. Tag / Simbol (Rata Tengah)
            val tagsStr = if (item.tags.isNotEmpty()) item.tags.joinToString(", ") else "-"
            val cleanTags = if (tagsStr.length > 18) tagsStr.substring(0, 16) + "..." else tagsStr
            canvas.drawText(cleanTags, tableLeft + colWNo + colWIcon + colWName + colWTag * 0.5f, textY, cellTextTagCenter)

            // 5. Spesifikasi / Keterangan (Rata Kiri)
            val cleanSpecs = if (item.specs.length > 42) item.specs.substring(0, 40) + "..." else item.specs
            canvas.drawText(cleanSpecs, tableLeft + colWNo + colWIcon + colWName + colWTag + 6f, textY, cellTextNormal)

            // 6. Jumlah (Rata Tengah)
            canvas.drawText("${item.count}", tableLeft + colWNo + colWIcon + colWName + colWTag + colWSpecs + colWCount * 0.5f, textY, cellTextCountCenter)

            rowY += rowHeight
        }

        // Footnote Halaman BOM di Bawah (Di luar bingkai luar)
        val footnoteLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Dokumen ini dihasilkan secara otomatis oleh Mashimurelay CAD Simulator", pageMarginL + 4f, 820f, footnoteLeft)

        val footnoteRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Halaman $pageNumber dari $totalPages", boxR - 4f, 820f, footnoteRight)
    }

    // =========================================================================
    // 7. PENYIMPANAN KE FOLDER DOWNLOADS ANDROID
    // =========================================================================
    fun savePdfToDownloads(context: Context, filename: String, pdfBytes: ByteArray): File? {
        val cleanFilename = if (filename.endsWith(".pdf", ignoreCase = true)) filename else "$filename.pdf"
        var localFile: File? = null

        // 1. Simpan selalu ke App-Specific Documents storage (100% aman untuk FileProvider di Android 10 s.d. 15+)
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

        // 2. Simpan salinan ke direktori berkas publik Downloads agar pengguna dapat menemukannya di File Manager
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
