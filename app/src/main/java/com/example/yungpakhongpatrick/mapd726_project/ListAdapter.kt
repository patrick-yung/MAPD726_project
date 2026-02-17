package com.example.yungpakhongpatrick.mapd726_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ListAdapter(
    private val shoppingLists: List<SavedList>,
    private val onListClicked: (SavedList) -> Unit // Click handler for selection
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvListName)
        val tvDate: TextView = view.findViewById(R.id.tvListDate)
        val tvCount: TextView = view.findViewById(R.id.tvItemCount)
        val tvPrice: TextView = view.findViewById(R.id.tvListPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Uses your item_notebook_list.xml for the main rows
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notebook_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val list = shoppingLists[position]
        holder.tvName.text = list.name
        holder.tvDate.text = list.date
        holder.tvCount.text = "${list.itemCount} items"
        holder.tvPrice.text = "$${String.format("%.2f", list.totalPrice)}"

        // When user clicks the row, trigger the pop-up
        holder.itemView.setOnClickListener { onListClicked(list) }
    }

    override fun getItemCount() = shoppingLists.size
}