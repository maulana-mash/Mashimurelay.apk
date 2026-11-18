package com.mashimurelay.app.nativecad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import java.io.InputStream

object SvgComponentRenderer {

    private val rawSvgTextCache = mutableMapOf<String, String>()
    private val frameSvgCache = mutableMapOf<String, SVG>()
    private val framePictureCache = mutableMapOf<String, android.graphics.Picture>()
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun clearCache() {
        frameSvgCache.clear()
        framePictureCache.clear()
        bitmapCache.values.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        bitmapCache.clear()
    }

    fun getRawSvgText(context: Context, svgFileName: String): String? {
        if (rawSvgTextCache.containsKey(svgFileName)) {
            return rawSvgTextCache[svgFileName]
        }

        val altPath = if (svgFileName.endsWith(".svg")) svgFileName else "$svgFileName.svg"
        val candidatePaths = listOf(
            "assets/icons/$svgFileName",
            "assets/icons/$altPath",
            "icons/$svgFileName",
            "icons/$altPath",
            svgFileName,
            altPath
        )

        var stream: InputStream? = null
        for (candidate in candidatePaths) {
            try {
                stream = context.assets.open(candidate)
                if (stream != null) break
            } catch (_: Exception) {}
        }

        if (stream != null) {
            try {
                val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                rawSvgTextCache[svgFileName] = text
                return text
            } catch (_: Exception) {}
        }
        return null
    }

    fun getSvg(context: Context, svgFileName: String, frameIndex: Int = 0, variantIndex: Int = 0, lampColor: String = "yellow"): SVG? {
        val cacheKey = "${svgFileName}_f${frameIndex}_v${variantIndex}_c${lampColor.lowercase()}"
        if (frameSvgCache.containsKey(cacheKey)) {
            return frameSvgCache[cacheKey]
        }

        val raw = getRawSvgText(context, svgFileName) ?: return null
        val processedSvg = applyFrameToSvgString(raw, frameIndex, variantIndex, lampColor)

        try {
            val svg = SVG.getFromString(processedSvg)
            frameSvgCache[cacheKey] = svg
            return svg
        } catch (_: Exception) {
            try {
                val fallbackSvg = SVG.getFromString(raw)
                frameSvgCache[cacheKey] = fallbackSvg
                return fallbackSvg
            } catch (_: Exception) {}
        }
        return null
    }

    private fun applyFrameToSvgString(rawSvg: String, frameIndex: Int, variantIndex: Int = 0, lampColor: String = "yellow"): String {
        val allManagedIds = mutableSetOf<String>()
        val activeIds = mutableSetOf<String>()
        val lampHex = when (lampColor.lowercase()) {
            "white" -> "#f8fafc"
            "red" -> "#ef4444"
            "green" -> "#22c55e"
            "blue" -> "#3b82f6"
            else -> "#fbbf24"
        }

        // 1. Kumpulkan seluruh ID pathFrame dari seluruh dokumen ke dalam allManagedIds
        val pathRegex = Regex("""<pathFrame\s+id=["']([^"']+)["']""")
        for (m in pathRegex.findAll(rawSvg)) {
            allManagedIds.add(m.groupValues[1])
        }

        // Tambahkan ID standar dan varian agar selalu terkendali
        allManagedIds.addAll(listOf(
            "mainPath1", "mainPath2", "mainPath3", "mainFill1", "mainFill2",
            "mainPath1_2", "mainPath2_2", "mainPath1_3", "mainPath2_3",
            "mainPath1_4", "mainPath2_4", "mainPath1_5", "mainPath2_5",
            "mainPath1_6", "mainPath2_6", "textPath1", "textPath2", "textPath3", "textPath4", "mainPath1-8"
        ))

        // Untuk motor dan power sources, textPath1/2/3/4 adalah label sah ("M3~", "L1", "N", dll.)
        val isMotorOrSource = rawSvg.contains("category=\"motors\"") || rawSvg.contains("category=\"sources\"")
        if (isMotorOrSource) {
            allManagedIds.remove("textPath1")
            allManagedIds.remove("textPath2")
            allManagedIds.remove("textPath3")
            allManagedIds.remove("textPath4")
        }

        // 2. Ekstrak frame aktif dari blok <frames> yang sesuai dengan variantIndex
        val framesBlocks = Regex("""<frames(?:\s+id=["'](\d+)["'])?\s*>(.*?)</frames>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(rawSvg).toList()

        if (framesBlocks.isNotEmpty()) {
            var targetBody: String? = null
            for (fb in framesBlocks) {
                val bId = fb.groupValues[1]
                if (bId == variantIndex.toString() || (bId.isEmpty() && variantIndex == 0)) {
                    targetBody = fb.groupValues[2]
                    break
                }
            }
            if (targetBody == null) {
                targetBody = framesBlocks.first().groupValues[2]
            }

            val frameRegex = Regex("""<frame\s+id=["'](\d+)["']\s*>(.*?)</frame>""", RegexOption.DOT_MATCHES_ALL)
            val frameList = frameRegex.findAll(targetBody).toList()
            val targetFrame = frameList.find { it.groupValues[1] == frameIndex.toString() } ?: frameList.firstOrNull()

            if (targetFrame != null) {
                for (pm in pathRegex.findAll(targetFrame.groupValues[2])) {
                    activeIds.add(pm.groupValues[1])
                }
            }
        }

        // 2b. Untuk komponen motor, jangan pernah menampilkan panah statis bawaan SVG
        // (strokePath1..4, pathFill3..4) karena panah putaran dan ekornya dirender secara dinamis 60fps oleh NativeCanvasView
        val isMotor = rawSvg.contains("category=\"motors\"") || rawSvg.contains("type=\"motor")
        if (isMotor) {
            val motorStaticArrowIds = setOf("strokePath1", "strokePath2", "strokePath3", "strokePath4", "pathFill3", "pathFill4")
            allManagedIds.addAll(motorStaticArrowIds)
            activeIds.removeAll(motorStaticArrowIds)

            allManagedIds.add("fillPath1")
            if (frameIndex >= 1) {
                activeIds.add("fillPath1")
            } else {
                activeIds.remove("fillPath1")
            }
        }

        // 3. Fallback jika activeIds kosong
        if (activeIds.isEmpty()) {
            val hasMainFill1 = rawSvg.contains("id=\"mainFill1\"") || rawSvg.contains("id='mainFill1'")
            if (hasMainFill1) {
                activeIds.add("mainPath1")
                if (frameIndex >= 1) activeIds.add("mainFill1")
            } else {
                when (frameIndex) {
                    1 -> activeIds.add("mainPath2")
                    2 -> activeIds.add("mainPath3")
                    else -> activeIds.add("mainPath1")
                }
            }
        }

        // 4. Terapkan display:inline atau display:none ke setiap elemen yang ID-nya cocok
        val lines = rawSvg.lines().map { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("<pathFrame")) {
                line
            } else {
                var matchedId: String? = null
                for (id in allManagedIds) {
                    if (line.contains("id=\"$id\"") || line.contains("id='$id'")) {
                        matchedId = id
                        break
                    }
                }

                if (matchedId != null) {
                    val isCoil = rawSvg.contains("category=\"coils\"") || rawSvg.contains("category='coils'") ||
                            rawSvg.contains("tagType=\"timerCoils\"") || rawSvg.contains("flasher", ignoreCase = true) ||
                            rawSvg.contains("type=\"coil\"", ignoreCase = true)
                    val effectiveHex = if (isCoil) "#ef4444" else lampHex
                    val shouldBeVisible = activeIds.contains(matchedId)
                    adjustElementVisibility(line, shouldBeVisible, effectiveHex)
                } else {
                    line
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun adjustElementVisibility(line: String, visible: Boolean, lampColorHex: String = "#fbbf24"): String {
        var clean = line
            .replace(Regex("""\s+display=["'][^"']*["']"""), "")
            .replace(Regex("""display\s*:\s*none\s*;?"""), "")
            .replace(Regex("""display\s*:\s*inline\s*;?"""), "")

        if (visible && (clean.contains("id=\"mainFill1\"") || clean.contains("id='mainFill1'"))) {
            clean = clean.replace(Regex("""fill\s*:\s*[^;"]+"""), "fill:$lampColorHex")
        }

        // Sanitasi: Hilangkan stroke kuning statis Inkscape pada garis sakelar mekanis agar tidak mengecoh visual arus
        if (clean.contains("stroke:#ffff00") || clean.contains("stroke: #ffff00")) {
            val isLampOrSource = lampColorHex.isNotEmpty() && (clean.contains("mainFill") || clean.contains("lamp"))
            if (!isLampOrSource) {
                clean = clean.replace("stroke:#ffff00", "stroke:#ffffff")
                    .replace("stroke: #ffff00", "stroke: #ffffff")
            }
        }

        return if (visible) {
            if (clean.contains("style=\"")) {
                clean.replace("style=\"", "style=\"display:inline;")
            } else if (clean.contains("style='")) {
                clean.replace("style='", "style='display:inline;")
            } else {
                clean.replace("<path ", "<path display=\"inline\" ")
                    .replace("<rect ", "<rect display=\"inline\" ")
                    .replace("<g ", "<g display=\"inline\" ")
            }
        } else {
            if (clean.contains("style=\"")) {
                clean.replace("style=\"", "style=\"display:none;")
                    .replace("<path ", "<path display=\"none\" ")
                    .replace("<rect ", "<rect display=\"none\" ")
                    .replace("<g ", "<g display=\"none\" ")
            } else if (clean.contains("style='")) {
                clean.replace("style='", "style='display:none;")
                    .replace("<path ", "<path display=\"none\" ")
                    .replace("<rect ", "<rect display=\"none\" ")
                    .replace("<g ", "<g display=\"none\" ")
            } else {
                clean.replace("<path ", "<path display=\"none\" ")
                    .replace("<rect ", "<rect display=\"none\" ")
                    .replace("<g ", "<g display=\"none\" ")
            }
        }
    }

    fun getPicture(
        context: Context,
        svgFileName: String,
        widthPx: Int,
        heightPx: Int,
        frameIndex: Int = 0,
        variantIndex: Int = 0,
        lampColor: String = "yellow"
    ): android.graphics.Picture? {
        val cacheKey = "${svgFileName}_f${frameIndex}_v${variantIndex}_c${lampColor.lowercase()}_${widthPx}x${heightPx}"
        if (framePictureCache.containsKey(cacheKey)) {
            return framePictureCache[cacheKey]
        }

        val svg = getSvg(context, svgFileName, frameIndex, variantIndex, lampColor) ?: return null
        return try {
            val w = widthPx.coerceAtLeast(1)
            val h = heightPx.coerceAtLeast(1)
            val pic = svg.renderToPicture(w, h)
            framePictureCache[cacheKey] = pic
            pic
        } catch (_: Exception) {
            null
        }
    }

    fun render(
        context: Context,
        canvas: Canvas,
        svgFileName: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        frameIndex: Int = 0,
        variantIndex: Int = 0,
        lampColor: String = "yellow"
    ): Boolean {
        val w = width.toInt()
        val h = height.toInt()
        val pic = getPicture(context, svgFileName, w, h, frameIndex, variantIndex, lampColor)
        if (pic != null) {
            canvas.save()
            canvas.translate(left, top)
            canvas.drawPicture(pic)
            canvas.restore()
            return true
        }

        val svg = getSvg(context, svgFileName, frameIndex, variantIndex, lampColor) ?: return false
        val rect = RectF(left, top, left + width, top + height)
        svg.renderToCanvas(canvas, rect)
        return true
    }

    fun getThumbnailBitmap(context: Context, svgFileName: String, sizePx: Int = 160): Bitmap? {
        if (bitmapCache.containsKey(svgFileName)) {
            val cached = bitmapCache[svgFileName]
            if (cached != null && !cached.isRecycled) return cached
        }
        val svg = getSvg(context, svgFileName, frameIndex = 0, variantIndex = 0) ?: return null

        val finalBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)

        try {
            val viewBox = svg.documentViewBox
            if (viewBox == null) {
                val docW = if (svg.documentWidth > 0f) svg.documentWidth else 120f
                val docH = if (svg.documentHeight > 0f) svg.documentHeight else 120f
                svg.setDocumentViewBox(0f, 0f, docW, docH)
            }
            svg.documentPreserveAspectRatio = com.caverock.androidsvg.PreserveAspectRatio.LETTERBOX

            val scratchSize = 256
            val scratchPad = 20f
            val scratchBitmap = Bitmap.createBitmap(scratchSize, scratchSize, Bitmap.Config.ARGB_8888)
            val scratchCanvas = Canvas(scratchBitmap)
            val renderRect = RectF(scratchPad, scratchPad, scratchSize - scratchPad, scratchSize - scratchPad)
            svg.renderToCanvas(scratchCanvas, renderRect)

            val inkBounds = findNonTransparentBounds(scratchBitmap)
            if (inkBounds != null && inkBounds.width() > 2 && inkBounds.height() > 2) {
                val paddingRatio = 0.14f
                val availableSize = sizePx * (1f - paddingRatio * 2f)
                val inkW = inkBounds.width().toFloat()
                val inkH = inkBounds.height().toFloat()
                val scale = minOf(availableSize / inkW, availableSize / inkH)
                val destW = inkW * scale
                val destH = inkH * scale

                val destLeft = (sizePx - destW) * 0.5f
                val destTop = (sizePx - destH) * 0.5f
                val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)

                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                finalCanvas.drawBitmap(scratchBitmap, inkBounds, destRect, paint)
            } else {
                val padPx = sizePx * 0.14f
                val fallbackRect = RectF(padPx, padPx, sizePx - padPx, sizePx - padPx)
                svg.renderToCanvas(finalCanvas, fallbackRect)
            }
            scratchBitmap.recycle()
        } catch (_: Exception) {
            val padPx = sizePx * 0.14f
            val fallbackRect = RectF(padPx, padPx, sizePx - padPx, sizePx - padPx)
            svg.renderToCanvas(finalCanvas, fallbackRect)
        }

        bitmapCache[svgFileName] = finalBitmap
        return finalBitmap
    }

    private fun findNonTransparentBounds(bitmap: Bitmap, alphaThreshold: Int = 12): android.graphics.Rect? {
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

        return if (maxX >= minX && maxY >= minY) {
            android.graphics.Rect(minX, minY, maxX + 1, maxY + 1)
        } else {
            null
        }
    }
}
