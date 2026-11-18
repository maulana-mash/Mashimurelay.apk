package com.mashimurelay.app.nativecad

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.mashimurelay.app.R

sealed class LibraryRowItem {
    data class GroupHeader(
        val categoryKey: String,
        val title: String,
        val count: Int,
        var isExpanded: Boolean = true
    ) : LibraryRowItem()

    data class ComponentTile(
        val def: ComponentDef
    ) : LibraryRowItem()
}

class VisualComponentAdapter(
    private var allDefs: List<ComponentDef>,
    private val onItemClick: (ComponentDef) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TILE = 1

        val CATEGORY_TITLES = linkedMapOf(
            "sources" to "Sumber Daya (Sources)",
            "protections" to "Proteksi / MCB (Protections)",
            "switches" to "Saklar & Tombol (Switches)",
            "contactors" to "Kontaktor (Contactors)",
            "coils" to "Koil & Timer (Coils)",
            "auxiliaries" to "Kontak Bantu (Auxiliaries)",
            "motors" to "Motor Listrik (Motors)",
            "loads" to "Beban & Lampu (Loads)",
            "others" to "Terminal & Jalur (Others)"
        )
    }

    private val collapsedGroups = mutableSetOf<String>()
    private val displayItems = mutableListOf<LibraryRowItem>()
    private var activeFilterCategory: String? = null // null means all
    private var allowedComponentKeys: Set<String>? = null // null means all components allowed

    init {
        rebuildDisplayList()
    }

    fun setAllComponents(defs: List<ComponentDef>) {
        allDefs = defs
        rebuildDisplayList()
    }

    fun setAllowedComponentFilter(keys: Set<String>?) {
        allowedComponentKeys = keys
        rebuildDisplayList()
    }

    fun getAllowedComponentFilter(): Set<String>? = allowedComponentKeys

    fun setCategoryFilter(categoryKey: String?) {
        activeFilterCategory = if (categoryKey == "all" || categoryKey == null) null else categoryKey
        rebuildDisplayList()
    }

    private fun rebuildDisplayList() {
        displayItems.clear()

        val baseDefs = if (allowedComponentKeys != null) {
            allDefs.filter {
                allowedComponentKeys!!.contains(it.type) ||
                allowedComponentKeys!!.contains(it.file.removeSuffix(".svg"))
            }
        } else {
            allDefs
        }

        val filteredDefs = if (activeFilterCategory == null) {
            baseDefs
        } else {
            baseDefs.filter { it.category.equals(activeFilterCategory, ignoreCase = true) }
        }

        // Group by category following standard order
        for ((catKey, catTitle) in CATEGORY_TITLES) {
            val groupItems = filteredDefs.filter { it.category.equals(catKey, ignoreCase = true) }
            if (groupItems.isEmpty()) continue

            val isExpanded = !collapsedGroups.contains(catKey)
            displayItems.add(
                LibraryRowItem.GroupHeader(
                    categoryKey = catKey,
                    title = catTitle,
                    count = groupItems.size,
                    isExpanded = isExpanded
                )
            )

            if (isExpanded) {
                for (def in groupItems) {
                    displayItems.add(LibraryRowItem.ComponentTile(def))
                }
            }
        }

        // Catch-all for any uncategorized components
        val alreadyCategorized = CATEGORY_TITLES.keys
        val otherItems = filteredDefs.filter { !alreadyCategorized.contains(it.category.lowercase()) }
        if (otherItems.isNotEmpty()) {
            val catKey = "misc"
            val isExpanded = !collapsedGroups.contains(catKey)
            displayItems.add(
                LibraryRowItem.GroupHeader(
                    categoryKey = catKey,
                    title = "Komponen Lainnya (Miscellaneous)",
                    count = otherItems.size,
                    isExpanded = isExpanded
                )
            )
            if (isExpanded) {
                for (def in otherItems) {
                    displayItems.add(LibraryRowItem.ComponentTile(def))
                }
            }
        }

        notifyDataSetChanged()
    }

    fun getSpanSizeLookup(spanCount: Int = 3): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> spanCount
                    else -> 1
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is LibraryRowItem.GroupHeader -> VIEW_TYPE_HEADER
            is LibraryRowItem.ComponentTile -> VIEW_TYPE_TILE
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_library_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_component_tile, parent, false)
            TileViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is LibraryRowItem.GroupHeader -> {
                val h = holder as HeaderViewHolder
                h.tvTitle.text = item.title
                h.tvCount.text = item.count.toString()
                h.tvChevron.text = if (item.isExpanded) "▾" else "▸"

                h.itemView.setOnClickListener {
                    if (collapsedGroups.contains(item.categoryKey)) {
                        collapsedGroups.remove(item.categoryKey)
                    } else {
                        collapsedGroups.add(item.categoryKey)
                    }
                    rebuildDisplayList()
                }
            }
            is LibraryRowItem.ComponentTile -> {
                val t = holder as TileViewHolder
                val def = item.def
                val context = t.itemView.context

                // 1. Render Centered SVG Thumbnail
                val bmp = SvgComponentRenderer.getThumbnailBitmap(context, def.file)
                if (bmp != null) {
                    t.ivThumbnail.setImageBitmap(bmp)
                } else {
                    t.ivThumbnail.setImageDrawable(null)
                }

                // 2. Category Color Accent Line at Bottom
                val accentColor = when (def.category) {
                    "switches" -> Color.parseColor("#38bdf8")     // Sky blue
                    "auxiliaries" -> Color.parseColor("#c084fc")  // Purple
                    "contactors" -> Color.parseColor("#22d3ee")   // Cyan
                    "coils" -> Color.parseColor("#a855f7")        // Violet
                    "protections" -> Color.parseColor("#f59e0b")  // Amber
                    "motors" -> Color.parseColor("#34d399")       // Emerald green
                    "loads" -> Color.parseColor("#facc15")        // Yellow
                    "sources" -> Color.parseColor("#fb7185")      // Rose
                    else -> Color.parseColor("#64748b")           // Slate gray
                }
                t.viewCategoryAccent.setBackgroundColor(accentColor)

                t.cardTile.setOnClickListener {
                    onItemClick(def)
                }

                t.cardTile.setOnLongClickListener { anchorView ->
                    anchorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showComponentTooltip(anchorView, def.name)
                    true
                }
            }
        }
    }

    private fun showComponentTooltip(anchorView: View, text: String) {
        val context = anchorView.context
        val density = context.resources.displayMetrics.density

        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        val textView = TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#0f172a"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#f1f5f9"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#cbd5e1"))
            }
            background = shape
            elevation = dp(8).toFloat()
        }

        val popup = PopupWindow(
            textView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }

        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupW = textView.measuredWidth
        val popupH = textView.measuredHeight

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

        val posX = anchorX + (anchorView.width - popupW) / 2
        val posY = anchorY - popupH - dp(8)

        popup.showAtLocation(
            anchorView,
            Gravity.NO_GRAVITY,
            maxOf(dp(10), posX),
            maxOf(dp(25), posY)
        )
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
        val tvCount: TextView = view.findViewById(R.id.tvHeaderCount)
        val tvChevron: TextView = view.findViewById(R.id.tvChevron)
    }

    class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardTile: MaterialCardView = view.findViewById(R.id.cardTile)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val viewCategoryAccent: View = view.findViewById(R.id.viewCategoryAccent)
    }
}
