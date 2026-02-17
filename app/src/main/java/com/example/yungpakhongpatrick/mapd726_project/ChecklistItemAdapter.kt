package com.example.yungpakhongpatrick.mapd726_project

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChecklistItemAdapter(
    private var items: List<SavedItem>,
    private val onCheckedChanged: (position: Int, isChecked: Boolean) -> Unit
) : RecyclerView.Adapter<ChecklistItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cbItem)
        val itemName: TextView = view.findViewById(R.id.tvItemName)
        val itemPrice: TextView = view.findViewById(R.id.tvItemPrice)
    }

    fun submitItems(updatedItems: List<SavedItem>) {
        items = updatedItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.itemName.text = "${item.name} (${item.store})"
        holder.itemPrice.text = "$${String.format("%.2f", item.price)}"

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.isChecked
        updateItemDecoration(holder.itemName, item.isChecked)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            updateItemDecoration(holder.itemName, isChecked)
            onCheckedChanged(position, isChecked)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun updateItemDecoration(textView: TextView, isChecked: Boolean) {
        textView.paintFlags = if (isChecked) {
            textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
}
