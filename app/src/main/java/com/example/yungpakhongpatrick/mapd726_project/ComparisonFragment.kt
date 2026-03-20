package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ComparisonFragment : BaseFragment(R.layout.fragment_comparison) {

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel and API Service
        viewModel = ViewModelProvider(requireActivity()).get(ListViewModel::class.java)
        apiService = ApiService(getString(R.string.base_url))

        // Fetch categorized data if not already loaded
        if (viewModel.categorizedData.value == null) {
            viewModel.fetchCategorizedData(apiService)
        }

        // Observe categorized data loading state
        viewModel.isLoadingProducts.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                Toast.makeText(requireContext(), "Loading products...", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe errors
        viewModel.productError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_LONG).show()
            }
        }

        // Observe categorized data and perform search when data is loaded
        viewModel.categorizedData.observe(viewLifecycleOwner) { categorizedData ->
            // Catch the word sent from the Home Screen search bar
            val searchQuery = arguments?.getString("PRODUCT_QUERY")

            if (!searchQuery.isNullOrEmpty() && categorizedData != null && categorizedData.isNotEmpty()) {
                performSearch(searchQuery, categorizedData)
            } else if (!searchQuery.isNullOrEmpty() && categorizedData != null && categorizedData.isEmpty()) {
                Toast.makeText(requireContext(), "No products available", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup Back Arrow
        view.findViewById<View>(R.id.btnBackArrow).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun performSearch(query: String, categorizedData: Map<String, Map<String, List<Double>>>) {
        var foundPrices: List<Double>? = null
        var foundProductName: String? = null
        var foundCategoryName: String? = null

        // Scan all categories in the categorized data for a match
        for ((categoryName, categoryMap) in categorizedData) {
            val match = categoryMap.keys.find { it.contains(query, ignoreCase = true) }
            if (match != null) {
                foundPrices = categoryMap[match]
                foundProductName = match
                foundCategoryName = categoryName
                break
            }
        }

        // Update the UI with the results
        if (foundPrices != null && foundPrices.size == 3) {
            updateUIWithSearchResults(
                foundProductName = foundProductName,
                foundCategoryName = foundCategoryName,
                foundPrices = foundPrices,
                query = query
            )
        } else {
            Toast.makeText(requireContext(), "No prices found for '$query'", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIWithSearchResults(
        foundProductName: String?,
        foundCategoryName: String?,
        foundPrices: List<Double>,
        query: String
    ) {
        val view = view ?: return

        val walmartPrice = foundPrices[0]
        val costcoPrice = foundPrices[1]
        val superstorePrice = foundPrices[2]

        // Find the lowest price mathematically
        val minPrice = foundPrices.minOrNull() ?: 0.0

        // Update the basic text fields
        view.findViewById<TextView>(R.id.tvTitle)?.text = foundProductName
        view.findViewById<TextView>(R.id.tvPrice2)?.text = "$${walmartPrice}" // Walmart
        view.findViewById<TextView>(R.id.tvPrice1)?.text = "$${costcoPrice}"  // Costco
        view.findViewById<TextView>(R.id.tvPrice3)?.text = "$${superstorePrice}" // Superstore

        // Setup Badge Views
        val badgeCostco = view.findViewById<TextView>(R.id.badgeCostco)
        val badgeWalmart = view.findViewById<TextView>(R.id.badgeWalmart)
        val badgeSuperstore = view.findViewById<TextView>(R.id.badgeSuperstore)

        // Setup Top Summary Views
        val tvTopStore = view.findViewById<TextView>(R.id.tvTopDealStore)
        val tvTopPrice = view.findViewById<TextView>(R.id.tvTopDealPrice)

        // Hide all badges initially to reset the UI
        badgeCostco?.visibility = View.GONE
        badgeWalmart?.visibility = View.GONE
        badgeSuperstore?.visibility = View.GONE

        var bestStoreName = ""
        var bestPrice = 0.0

        // Highlight the Winner based on the minimum price
        when (minPrice) {
            costcoPrice -> {
                badgeCostco?.visibility = View.VISIBLE
                tvTopStore?.text = "Costco"
                tvTopStore?.setTextColor(android.graphics.Color.parseColor("#E31837")) // Red
                tvTopPrice?.text = "At $${costcoPrice}, this is the lowest price."
                bestStoreName = "Costco"
                bestPrice = costcoPrice
            }
            walmartPrice -> {
                badgeWalmart?.visibility = View.VISIBLE
                tvTopStore?.text = "Walmart"
                tvTopStore?.setTextColor(android.graphics.Color.parseColor("#004FB6")) // Blue
                tvTopPrice?.text = "At $${walmartPrice}, this is the lowest price."
                bestStoreName = "Walmart"
                bestPrice = walmartPrice
            }
            superstorePrice -> {
                badgeSuperstore?.visibility = View.VISIBLE
                tvTopStore?.text = "Superstore"
                tvTopStore?.setTextColor(android.graphics.Color.parseColor("#2E9E47")) // Green
                tvTopPrice?.text = "At $${superstorePrice}, this is the lowest price."
                bestStoreName = "Superstore"
                bestPrice = superstorePrice
            }
        }

        // Setup the "Add Best Deal" Button
        val btnAddBestDeal = view.findViewById<Button>(R.id.btnAddBestDeal)
        btnAddBestDeal?.setOnClickListener {
            if (foundProductName != null && foundCategoryName != null) {
                // Create a CartItem using the same class from your AddItemsFragment
                val newItem = CartItem(
                    name = foundProductName!!,
                    price = bestPrice,
                    store = bestStoreName,
                    type = foundCategoryName!!,
                    quantity = 1
                )

                // Add it directly to the shared list
                viewModel.draftCartList.add(newItem)

                Toast.makeText(requireContext(), "Added $foundProductName from $bestStoreName!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
}