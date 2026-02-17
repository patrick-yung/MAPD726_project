package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ListDetailsFragment : BaseFragment(R.layout.fragment_list_details) {

    private lateinit var viewModel: ListViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listId = arguments?.getLong(ARG_LIST_ID) ?: return
        viewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]

        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvListDate)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvListTotalAmount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyItems)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvListItems)

        val checklistAdapter = ChecklistItemAdapter(emptyList()) { itemIndex, isChecked ->
            viewModel.updateItemChecked(listId, itemIndex, isChecked)
        }

        rvItems.layoutManager = LinearLayoutManager(requireContext())
        rvItems.adapter = checklistAdapter

        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
            val selectedList = allLists.firstOrNull { it.id == listId } ?: return@observe
            tvHeaderTitle.text = selectedList.name
            tvDate.text = selectedList.date
            tvTotalAmount.text = "$${String.format("%.2f", selectedList.totalPrice)}"

            checklistAdapter.submitItems(selectedList.items)
            tvEmpty.visibility = if (selectedList.items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val ARG_LIST_ID = "arg_list_id"

        fun newInstance(listId: Long): ListDetailsFragment {
            return ListDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_LIST_ID, listId)
                }
            }
        }
    }
}
