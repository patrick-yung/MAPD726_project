package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.View
import com.example.yungpakhongpatrick.mapd726_project.R

class ListFragment : BaseFragment(R.layout.fragment_list) {
    private lateinit var listViewModel: ListViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        val rvShoppingList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvShoppingList)
        val tvTotalAmount = view.findViewById<android.widget.TextView>(R.id.tvTotalAmount)

        listViewModel.allShoppingLists.observe(viewLifecycleOwner) { updatedLists ->
            val grandTotal = updatedLists.sumOf { it.totalPrice }
            tvTotalAmount.text = "$${String.format("%.2f", grandTotal)}"

            rvShoppingList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            rvShoppingList.adapter = ListAdapter(updatedLists) { selectedList ->
                openListDetails(selectedList)
            }
        }
    }

    private fun openListDetails(selectedList: SavedList) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ListDetailsFragment.newInstance(selectedList.id))
            .addToBackStack(null)
            .commit()
    }
}