package com.mashimurelay.app.nativecad

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
