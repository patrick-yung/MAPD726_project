package com.example.yungpakhongpatrick.mapd726_project

import ComparisonFragment
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HomeFragment : Fragment() {

    // 1. Load the XML Layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    // 2. Set up the Logic (Button Clicks)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionManager = SessionManager(requireContext())
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        val userName = sessionManager.getUserName() ?: "Iffat"

        tvWelcome.text = "Welcome back, $userName"

        // 1. Find the container created in XML
        val llRecentListsContainer = view.findViewById<LinearLayout>(R.id.llRecentListsContainer)

        // 2. Initialize ListViewModel
        val viewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        // 3. OBSERVE the LiveData
        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->

            //  don't accidentally stack duplicates
            llRecentListsContainer.removeAllViews()

            if (allLists.isNullOrEmpty()) {
                return@observe
            }

            // Grab the last 3 lists added, and reverse them so the absolute newest is at the very top!
            val recentLists = allLists.takeLast(3).reversed()


            for (savedList in recentLists) {
                val cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_home_list_card, llRecentListsContainer, false)
                val tvListName = cardView.findViewById<TextView>(R.id.tvListName)
                val tvListMetadata = cardView.findViewById<TextView>(R.id.tvListMetadata)
                val tvListPrice = cardView.findViewById<TextView>(R.id.tvListPrice)

                // Set the Name and Date
                tvListName.text = savedList.name
                tvListMetadata.text = "${savedList.items.size} items • ${savedList.date}"

                // Calculate the Total Price
                val totalPrice = savedList.items.sumOf { it.price }
                tvListPrice.text = "$${String.format(java.util.Locale.getDefault(), "%.2f", totalPrice)}"

                // Add the fully built card to the screen
                llRecentListsContainer.addView(cardView)
            }
        }

        val btnCompare = view.findViewById<Button>(R.id.btnStartComparing)

        // Set the click listener to open the new screen
        btnCompare.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ComparisonFragment())
                .addToBackStack(null)
                .commit()
        }

    }

    override fun onResume() {
        super.onResume()

        // Bring the bottom menu back for the Home Screen!
        val bottomNav = requireActivity().findViewById<android.view.View>(R.id.bottom_navigation)
        bottomNav?.visibility = android.view.View.VISIBLE
    }
}