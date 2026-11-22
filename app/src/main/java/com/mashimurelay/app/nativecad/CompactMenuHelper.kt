package com.mashimurelay.app.nativecad

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

data class CompactMenuItem(
    val id: Int,
    val iconResId: Int? = null,
    val label: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit
)

object CompactMenuHelper {

    fun show(
        context: Context,
        anchor: View,
        items: List<CompactMenuItem>,
        widthDp: Int = 185
    ) {
        val density = context.resources.displayMetrics.density
        val popupWidthPx = (widthDp * density).toInt()
        val isLight = SettingsManager.isLightTheme(context)

        val card = MaterialCardView(context).apply {
            radius = 10f * density
            strokeWidth = (1f * density).toInt()
            setStrokeColor(Color.parseColor(if (isLight) "#cbd5e1" else "#323246"))
            setCardBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#181822"))
            cardElevation = 14f * density
            useCompatPadding = false
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padY = (4f * density).toInt()
            setPadding(0, padY, 0, padY)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val popup = PopupWindow(
            card,
            popupWidthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f * density
            isOutsideTouchable = true
        }

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        val selectableBackgroundRes = outValue.resourceId

        for ((index, item) in items.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (38f * density).toInt()
                )
                val padX = (12f * density).toInt()
                setPadding(padX, 0, padX, 0)
                setBackgroundResource(selectableBackgroundRes)

                // Vector Icon
                if (item.iconResId != null) {
                    val ivIcon = ImageView(context).apply {
                        setImageResource(item.iconResId)
                        val iconSize = (16f * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        val tintColor = if (item.isDanger) {
                            Color.parseColor(if (isLight) "#dc2626" else "#f87171")
                        } else {
                            Color.parseColor(if (isLight) "#475569" else "#94a3b8")
                        }
                        setColorFilter(tintColor)
                    }
                    addView(ivIcon)
                }

                // Label Text
                val tvLabel = TextView(context).apply {
                    text = item.label
                    textSize = 12.5f
                    setTextColor(
                        if (item.isDanger) Color.parseColor(if (isLight) "#dc2626" else "#f87171")
                        else Color.parseColor(if (isLight) "#0f172a" else "#f1f5f9")
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = if (item.iconResId != null) (10f * density).toInt() else 0
                    }
                    includeFontPadding = false
                    setSingleLine(true)
                }
                addView(tvLabel)

                setOnClickListener {
                    popup.dismiss()
                    item.onClick()
                }
            }
            container.addView(row)

            // Divider halus antar item jika item bahaya (misal Hapus Proyek)
            if (item.isDanger && index > 0) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1f * density).toInt()
                    ).apply {
                        val mX = (10f * density).toInt()
                        setMargins(mX, (2f * density).toInt(), mX, (2f * density).toInt())
                    }
                    setBackgroundColor(Color.parseColor(if (isLight) "#e2e8f0" else "#2a2a3c"))
                }
                container.addView(divider, container.childCount - 1)
            }
        }

        card.addView(container)

        // Tampilkan tepat di samping atau bawah anchor secara proporsional dan presisi di pojok
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorRight = location[0] + anchor.width
        val screenWidth = context.resources.displayMetrics.widthPixels

        val xOffset = if (anchorRight + (popupWidthPx / 2) >= screenWidth / 2) {
            -(popupWidthPx - anchor.width).coerceAtLeast(0) - (4f * density).toInt()
        } else {
            0
        }
        popup.showAsDropDown(anchor, xOffset, (4f * density).toInt())
    }
}
