package com.mashimurelay.app.nativecad

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mashimurelay.app.R

/**
 * Centralized Theme & Visual Polish Engine for Mashimurelay Native Android.
 * Synchronizes Dark CAD (default) & Light Paper tokens with Mashimurelay Web / Desktop.
 */
object AppThemeHelper {

    fun getPotentialColor(pot: Potential, isLight: Boolean): Int {
        if (!isLight) return pot.color
        return when (pot) {
            Potential.NONE -> Color.parseColor("#64748b")
            Potential.L1 -> Color.parseColor("#dc2626")          // Crisp IEC Red
            Potential.L2 -> Color.parseColor("#d97706")          // Deep Amber (legible on white)
            Potential.L3 -> Color.parseColor("#059669")          // Deep Emerald
            Potential.N -> Color.parseColor("#2563eb")           // Royal Blue Neutral
            Potential.PE -> Color.parseColor("#16a34a")          // Earth Green
            Potential.GND -> Color.parseColor("#047857")         // Chassis Ground
            Potential.DC_P -> Color.parseColor("#e11d48")        // Crisp Rose DC+
            Potential.DC_N -> Color.parseColor("#0284c7")        // Crisp Cobalt DC-
            Potential.DC_OVERHEAT -> Color.parseColor("#dc2626")
            Potential.AC_SHORT -> Color.parseColor("#ea580c")
        }
    }

    fun adaptTextColorForTheme(rawColor: String, isLight: Boolean): Int {
        val key = rawColor.trim().lowercase()
        if (isLight) {
            return when (key) {
                "", "#93c5fd", "#60a5fa", "#38bdf8", "blue", "cyan" -> Color.parseColor("#1d4ed8")
                "#ffffff", "#fff", "#f8fafc", "#f1f5f9", "#e2e8f0", "white" -> Color.parseColor("#0f172a")
                "#cbd5e1", "#94a3b8", "gray" -> Color.parseColor("#475569")
                "#fbbf24", "#fde047", "yellow" -> Color.parseColor("#b45309")
                "#22c55e", "#34d399", "green" -> Color.parseColor("#15803d")
                "#ef4444", "#f87171", "red" -> Color.parseColor("#dc2626")
                else -> try {
                    val parsed = Color.parseColor(rawColor)
                    val lum = (0.299 * Color.red(parsed) + 0.587 * Color.green(parsed) + 0.114 * Color.blue(parsed))
                    if (lum > 185.0) Color.parseColor("#1e293b") else parsed
                } catch (_: Exception) {
                    Color.parseColor("#1d4ed8")
                }
            }
        }
        return try {
            Color.parseColor(rawColor)
        } catch (_: Exception) {
            when (key) {
                "white" -> Color.parseColor("#f8fafc")
                "yellow" -> Color.parseColor("#fbbf24")
                "red" -> Color.parseColor("#ef4444")
                "green" -> Color.parseColor("#22c55e")
                "gray" -> Color.parseColor("#94a3b8")
                else -> Color.parseColor("#93c5fd")
            }
        }
    }

    /**
     * Seamless 2-layer Drawer Header Drawable:
     * Top ~54% preserves solid diagonal Mashimurelay Royal Blue (#0f294a -> #1e40af -> #2563eb),
     * Bottom ~46% dissolves smoothly into the drawer background (#141418 Dark / #ffffff Light).
     */
    fun createDrawerHeaderDrawable(isLight: Boolean): Drawable {
        val targetColor = if (isLight) Color.parseColor("#ffffff") else Color.parseColor("#141418")
        val targetR = Color.red(targetColor)
        val targetG = Color.green(targetColor)
        val targetB = Color.blue(targetColor)

        return object : Drawable() {
            private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun draw(canvas: Canvas) {
                val b = bounds
                val w = b.width().toFloat().coerceAtLeast(1f)
                val h = b.height().toFloat().coerceAtLeast(1f)

                // Layer 1: Diagonal Mashimurelay Royal Blue (135deg equivalent)
                bluePaint.shader = LinearGradient(
                    0f, 0f, w, h * 0.72f,
                    intArrayOf(
                        Color.parseColor("#0f294a"),
                        Color.parseColor("#1e40af"),
                        Color.parseColor("#2563eb")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(b, bluePaint)

                // Layer 2: Wide silky vertical fade starting below logo/subtitle (54% -> 100%)
                fadePaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(
                        Color.argb(0, targetR, targetG, targetB),
                        Color.argb(0, targetR, targetG, targetB),
                        Color.argb(46, targetR, targetG, targetB),
                        Color.argb(122, targetR, targetG, targetB),
                        Color.argb(204, targetR, targetG, targetB),
                        Color.argb(255, targetR, targetG, targetB)
                    ),
                    floatArrayOf(0f, 0.54f, 0.66f, 0.78f, 0.90f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(b, fadePaint)
            }

            override fun setAlpha(alpha: Int) {
                bluePaint.alpha = alpha
                fadePaint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                bluePaint.colorFilter = colorFilter
                fadePaint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.OPAQUE
        }
    }

    fun createSegmentedTrackDrawable(isLight: Boolean, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(if (isLight) Color.parseColor("#e2e8f0") else Color.parseColor("#12121a"))
            setStroke((1f * density).toInt().coerceAtLeast(1), if (isLight) Color.parseColor("#cbd5e1") else Color.parseColor("#2c2c40"))
        }
    }

    fun styleSegmentedOption(view: TextView, selected: Boolean, isLight: Boolean, density: Float) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * density
            if (selected) {
                setColor(Color.parseColor("#2563eb"))
            } else {
                setColor(Color.TRANSPARENT)
            }
        }
        view.background = bg
        if (selected) {
            view.setTextColor(Color.WHITE)
            view.setTypeface(null, Typeface.BOLD)
        } else {
            view.setTextColor(if (isLight) Color.parseColor("#475569") else Color.parseColor("#94a3b8"))
            view.setTypeface(null, Typeface.NORMAL)
        }
    }

    fun createInputDrawable(isLight: Boolean, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(if (isLight) Color.parseColor("#f8fafc") else Color.parseColor("#1e2235"))
            setStroke((1f * density).toInt().coerceAtLeast(1), if (isLight) Color.parseColor("#cbd5e1") else Color.parseColor("#333852"))
            val padX = (12f * density).toInt()
            val padY = (10f * density).toInt()
            setPadding(padX, padY, padX, padY)
        }
    }

    fun createSpinnerDrawable(isLight: Boolean, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(if (isLight) Color.parseColor("#f8fafc") else Color.parseColor("#161622"))
            setStroke((1f * density).toInt().coerceAtLeast(1), if (isLight) Color.parseColor("#cbd5e1") else Color.parseColor("#40405c"))
            val padX = (10f * density).toInt()
            val padY = (4f * density).toInt()
            setPadding(padX, padY, padX, padY)
        }
    }

    fun createClearCanvasDangerStyle(btn: Button, isLight: Boolean) {
        if (isLight) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#fef2f2"))
            btn.setTextColor(Color.parseColor("#dc2626"))
            if (btn is MaterialButton) {
                btn.iconTint = ColorStateList.valueOf(Color.parseColor("#dc2626"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#fca5a5"))
            }
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2d1515"))
            btn.setTextColor(Color.parseColor("#f87171"))
            if (btn is MaterialButton) {
                btn.iconTint = ColorStateList.valueOf(Color.parseColor("#f87171"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#7f1d1d"))
            }
        }
    }

    /**
     * Recursively adapts any dark-authored Mashimurelay dialog XML tree into crisp Light Paper
     * without breaking semantic accent CTA buttons (emerald/blue/red/violet) or camera viewfinders.
     */
    fun applyThemeToDialog(root: View?, isLight: Boolean) {
        if (root == null || !isLight) return
        val density = root.resources.displayMetrics.density
        val foundRootCard = booleanArrayOf(false)
        applyLightRecursively(root, foundRootCard, density)
    }

    private fun isDarkNeutralColor(color: Int): Boolean {
        if (Color.alpha(color) < 180) return false
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        // Dark surface/slate/navy neutral (not vivid emerald/red/blue CTA)
        return maxC <= 82 && (maxC - minC) <= 45
    }

    private fun isAccentFilledColor(color: Int): Boolean {
        if (Color.alpha(color) < 180) return false
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val sat = maxOf(r, g, b) - minOf(r, g, b)
        return sat >= 55 && maxOf(r, g, b) >= 110
    }

    private fun createLightRoundedCard(fillHex: String, strokeHex: String, radiusDp: Float, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(Color.parseColor(fillHex))
            setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor(strokeHex))
        }
    }

    private fun applyLightRecursively(view: View, foundRootCard: BooleanArray, density: Float) {
        // Keep camera preview viewfinder dark, and skip custom segmented controls & primary blue gallery button
        if (view.id == R.id.cardQrCameraPreview ||
            view.id == R.id.btnPickFromGallery ||
            view.id == R.id.groupThemeMode || view.id == R.id.groupGridStyle ||
            view.id == R.id.btnThemeDark || view.id == R.id.btnThemeLight ||
            view.id == R.id.btnGridBoth || view.id == R.id.btnGridDots ||
            view.id == R.id.btnGridLines || view.id == R.id.btnGridNone
        ) {
            return
        }

        // Special light styling for QR Scanner custom drawable containers
        if (view.id == R.id.cardQrStatusRow) {
            view.background = createLightRoundedCard("#f8fafc", "#e2e8f0", 10f, density)
        } else if (view.id == R.id.boxQrStatusIcon) {
            view.background = createLightRoundedCard("#dbeafe", "#bfdbfe", 8f, density)
        } else if (view.id == R.id.ivQrStatusWifi && view is ImageView) {
            view.setColorFilter(Color.parseColor("#2563eb"))
        } else if (view.id == R.id.btnToggleTorch) {
            view.background = createLightRoundedCard("#f1f5f9", "#cbd5e1", 10f, density)
        } else if (view.id == R.id.ivTorchIcon && view is ImageView) {
            view.setColorFilter(Color.parseColor("#1e293b"))
        } else if (view.id == R.id.tvTorchText && view is TextView) {
            view.setTextColor(Color.parseColor("#0f172a"))
        } else if (view.id == R.id.cardExamWarningNotice && view is MaterialCardView) {
            view.setCardBackgroundColor(Color.parseColor("#fef2f2"))
            view.strokeColor = Color.parseColor("#fca5a5")
        }

        if (view is MaterialCardView) {
            val cardBg = view.cardBackgroundColor.defaultColor
            if (isDarkNeutralColor(cardBg)) {
                val isFirst = !foundRootCard[0]
                if (isFirst) foundRootCard[0] = true
                val targetBg = if (isFirst) Color.WHITE else Color.parseColor("#f8fafc")
                view.setCardBackgroundColor(targetBg)
                val stroke = view.strokeColor
                if (stroke != 0 && isDarkNeutralColor(stroke)) {
                    view.strokeColor = if (isFirst) Color.parseColor("#cbd5e1") else Color.parseColor("#e2e8f0")
                }
            }
        } else if (view is EditText) {
            view.background = createInputDrawable(true, density)
            view.setTextColor(Color.parseColor("#0f172a"))
            view.setHintTextColor(Color.parseColor("#94a3b8"))
        } else if (view is Spinner) {
            view.background = createSpinnerDrawable(true, density)
            view.setPopupBackgroundDrawable(ColorDrawable(Color.WHITE))
        } else if (view is Button) {
            val tint = view.backgroundTintList?.defaultColor ?: Color.TRANSPARENT
            if (view.id == R.id.btnClearCanvas) {
                createClearCanvasDangerStyle(view, true)
            } else if (isDarkNeutralColor(tint)) {
                view.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#f1f5f9"))
                val txtCol = view.currentTextColor
                if (txtCol == Color.WHITE || Color.red(txtCol) > 170) {
                    view.setTextColor(Color.parseColor("#1e293b"))
                } else if (txtCol == Color.parseColor("#38bdf8")) {
                    view.setTextColor(Color.parseColor("#1d4ed8"))
                }
                if (view is MaterialButton) {
                    view.strokeWidth = (1f * density).toInt().coerceAtLeast(1)
                    view.strokeColor = ColorStateList.valueOf(Color.parseColor("#cbd5e1"))
                }
            }
            // Do not recolor text on vibrant CTA buttons (green, blue, red, violet)
            return
        } else if (view is TextView) {
            // Skip status badges & radio badges whose text sits on colored pills
            val isBadge = view.id == R.id.tvStatusAutoSave ||
                view.id == R.id.tvStatusGlowWires ||
                view.id == R.id.tvStatusPinLabels ||
                view.id == R.id.tvStatusSoundEffects ||
                view.id == R.id.tvStatusComponentNames ||
                view.id == R.id.tvRadioSmrl ||
                view.id == R.id.tvRadioTxt ||
                view.id == R.id.tvRadioPdf
            if (!isBadge) {
                val bgColor = (view.background as? ColorDrawable)?.color ?: Color.TRANSPARENT
                if (isDarkNeutralColor(bgColor)) {
                    view.background = createLightRoundedCard("#eff6ff", "#bfdbfe", 6f, density)
                    view.setTextColor(Color.parseColor("#1d4ed8"))
                } else if (!isAccentFilledColor(bgColor)) {
                    val col = view.currentTextColor
                    when (col) {
                        Color.WHITE, Color.parseColor("#f8fafc"), Color.parseColor("#f1f5f9"), Color.parseColor("#f4f4f5") ->
                            view.setTextColor(Color.parseColor("#0f172a"))
                        Color.parseColor("#cbd5e1"), Color.parseColor("#e2e8f0") ->
                            view.setTextColor(Color.parseColor("#334155"))
                        Color.parseColor("#94a3b8") ->
                            view.setTextColor(Color.parseColor("#475569"))
                        Color.parseColor("#38bdf8"), Color.parseColor("#60a5fa"), Color.parseColor("#93c5fd") ->
                            view.setTextColor(Color.parseColor("#1d4ed8"))
                        Color.parseColor("#fbbf24") ->
                            view.setTextColor(Color.parseColor("#b45309"))
                        Color.parseColor("#fca5a5") ->
                            view.setTextColor(Color.parseColor("#991b1b"))
                        Color.parseColor("#a78bfa") ->
                            view.setTextColor(Color.parseColor("#6d28d9"))
                    }
                }
            }
        } else if (view is ImageView) {
            if (view.id == R.id.btnCloseQrScanner || view.id == R.id.btnCloseConfirmSendTeacher ||
                view.id == R.id.btnCloseSaveAs || view.id == R.id.btnCloseProp || view.id == R.id.btnCloseAbout
            ) {
                view.setColorFilter(Color.parseColor("#475569"))
            }
        } else {
            val bg = view.background
            if (bg is ColorDrawable) {
                val c = bg.color
                if (isDarkNeutralColor(c)) {
                    val isDivider = (view.layoutParams?.height in 1..4) || (view.layoutParams?.width in 1..4)
                    if (isDivider) {
                        view.setBackgroundColor(Color.parseColor("#e2e8f0"))
                    } else if (c == Color.parseColor("#11131f") || c == Color.parseColor("#0d0e17") || c == Color.parseColor("#111114")) {
                        view.setBackgroundColor(Color.parseColor("#f8fafc"))
                    } else {
                        view.setBackgroundColor(Color.parseColor("#f8fafc"))
                    }
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyLightRecursively(view.getChildAt(i), foundRootCard, density)
            }
        }
    }
}
