package com.mashimurelay.app.nativecad

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mashimurelay.app.R

class WorkspaceProjectsAdapter(
    private var items: List<ProjectItem>,
    private val onItemClick: (ProjectItem) -> Unit,
    private val onOptionsClick: (View, ProjectItem) -> Unit
) : RecyclerView.Adapter<WorkspaceProjectsAdapter.ProjectViewHolder>() {

    var isLightTheme: Boolean = false
        private set

    fun setLightTheme(light: Boolean) {
        if (isLightTheme != light) {
            isLightTheme = light
            notifyDataSetChanged()
        }
    }

    fun updateItems(newItems: List<ProjectItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workspace_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.name
        holder.tvMeta.text = if (item.isExample) {
            "Contoh Rangkaian • Standar IEC"
        } else {
            "${item.elementCount} komponen"
        }

        if (isLightTheme) {
            holder.tvTitle.setTextColor(Color.parseColor("#0f172a"))
            holder.tvMeta.setTextColor(Color.parseColor("#64748b"))
            holder.ivIcon.setColorFilter(Color.parseColor("#2563eb"))
            holder.btnOptions.setColorFilter(Color.parseColor("#475569"))
        } else {
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.tvMeta.setTextColor(Color.parseColor("#64748b"))
            holder.ivIcon.clearColorFilter()
            holder.btnOptions.clearColorFilter()
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.btnOptions.visibility = if (item.isExample) View.GONE else View.VISIBLE
        holder.btnOptions.setOnClickListener { view ->
            onOptionsClick(view, item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ProjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvProjectTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvProjectMeta)
        val btnOptions: ImageView = view.findViewById(R.id.btnProjectOptions)
    }
}
