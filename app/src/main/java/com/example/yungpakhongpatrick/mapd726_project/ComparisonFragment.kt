package com.example.yungpakhongpatrick.mapd726_project

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import androidx.cardview.widget.CardView

class ComparisonFragment : BaseFragment(R.layout.fragment_comparison) {

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private val openFoodFactsService = OpenFoodFactsService()
    private var hasHandledSearch = false

    private data class ApiProductResult(
        val name: String,
        val brand: String,
        val quantity: String,
        val barcode: String
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]
        apiService = ApiService(getString(R.string.base_url))

        view.findViewById<View>(R.id.btnBackArrow).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val searchQuery = arguments?.getString("PRODUCT_QUERY")?.trim().orEmpty()

        if (searchQuery.isEmpty()) {
            showEmptyState()
            return
        }

        showSearchingState(searchQuery)

        val currentData = viewModel.categorizedData.value
        if (!currentData.isNullOrEmpty()) {
            handleQuery(searchQuery, currentData)
        } else {
            viewModel.fetchCategorizedData(apiService)
        }

        viewModel.categorizedData.observe(viewLifecycleOwner) { categorizedData ->
            if (hasHandledSearch) return@observe
            if (categorizedData != null) {
                handleQuery(searchQuery, categorizedData)
            }
        }

        viewModel.productError.observe(viewLifecycleOwner) { _ ->
            if (!hasHandledSearch) {
                handleApiLookup(searchQuery)
            }
        }
    }

    private fun handleQuery(
        query: String,
        categorizedData: Map<String, Map<String, List<Double>>>
    ) {
        var foundPrices: List<Double>? = null
        var foundProductName: String? = null
        var foundCategoryName: String? = null

        for ((categoryName, categoryMap) in categorizedData) {
            val match = categoryMap.keys.find { it.equals(query, true) }
                ?: categoryMap.keys.find { it.contains(query, true) }

            if (match != null) {
                foundPrices = categoryMap[match]
                foundProductName = match
                foundCategoryName = categoryName
                break
            }
        }

        // ALWAYS also fetch API (parallel logic)
        fetchApiAndMerge(query, foundPrices, foundProductName, foundCategoryName)
    }
    private fun handleApiLookup(query: String) {
        if (query.all { it.isDigit() } && query.length in 8..14) {
            searchByBarcode(query)
        } else {
            searchByText(query)
        }
    }

    private fun showSearchingState(query: String) {
        val currentView = view ?: return

        hideApiInfoCard()

        currentView.findViewById<TextView>(R.id.tvTitle).text = query
        currentView.findViewById<TextView>(R.id.tvTopDealStore).apply {
            text = "Searching..."
            setTextColor(Color.parseColor("#1B873F"))
        }
        currentView.findViewById<TextView>(R.id.tvTopDealPrice).text =
            "Checking local prices and live product API..."
        currentView.findViewById<TextView>(R.id.tvPrice1).text = "$--"
        currentView.findViewById<TextView>(R.id.tvPrice2).text = "$--"
        currentView.findViewById<TextView>(R.id.tvPrice3).text = "$--"

        currentView.findViewById<TextView>(R.id.badgeCostco).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeWalmart).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeSuperstore).visibility = View.GONE

        currentView.findViewById<Button>(R.id.btnAddBestDeal).apply {
            isEnabled = false
            alpha = 0.6f
            text = "Searching..."
        }
    }

    private fun showEmptyState() {
        val currentView = view ?: return

        hideApiInfoCard()

        currentView.findViewById<TextView>(R.id.tvTitle).text = "Price Comparison"
        currentView.findViewById<TextView>(R.id.tvTopDealStore).apply {
            text = "No product selected"
            setTextColor(Color.parseColor("#666666"))
        }
        currentView.findViewById<TextView>(R.id.tvTopDealPrice).text =
            "Go back and search for a product or barcode."
        currentView.findViewById<TextView>(R.id.tvPrice1).text = "$--"
        currentView.findViewById<TextView>(R.id.tvPrice2).text = "$--"
        currentView.findViewById<TextView>(R.id.tvPrice3).text = "$--"

        currentView.findViewById<TextView>(R.id.badgeCostco).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeWalmart).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeSuperstore).visibility = View.GONE

        currentView.findViewById<Button>(R.id.btnAddBestDeal).apply {
            isEnabled = false
            alpha = 0.6f
            text = "Add Best Deal to List"
        }
    }

    private fun showNoResultState(query: String) {
        val currentView = view ?: return

        hideApiInfoCard()

        currentView.findViewById<TextView>(R.id.tvTitle).text = query
        currentView.findViewById<TextView>(R.id.tvTopDealStore).apply {
            text = "No result found"
            setTextColor(Color.parseColor("#C62828"))
        }
        currentView.findViewById<TextView>(R.id.tvTopDealPrice).text =
            "Try a barcode for the most reliable API demo."
        currentView.findViewById<TextView>(R.id.tvPrice1).text = "N/A"
        currentView.findViewById<TextView>(R.id.tvPrice2).text = "N/A"
        currentView.findViewById<TextView>(R.id.tvPrice3).text = "N/A"

        currentView.findViewById<TextView>(R.id.badgeCostco).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeWalmart).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeSuperstore).visibility = View.GONE

        currentView.findViewById<Button>(R.id.btnAddBestDeal).apply {
            isEnabled = false
            alpha = 0.6f
            text = "No Product Found"
        }
    }

    private fun updateUIWithSearchResults(
        foundProductName: String,
        foundCategoryName: String,
        foundPrices: List<Double>
    ) {
        val currentView = view ?: return

        val walmartPrice = foundPrices[0]
        val costcoPrice = foundPrices[1]
        val superstorePrice = foundPrices[2]
        val minPrice = foundPrices.minOrNull() ?: 0.0

        currentView.findViewById<TextView>(R.id.tvTitle).text = foundProductName
        currentView.findViewById<TextView>(R.id.tvPrice2).text =
            "$${String.format(Locale.getDefault(), "%.2f", walmartPrice)}"
        currentView.findViewById<TextView>(R.id.tvPrice1).text =
            "$${String.format(Locale.getDefault(), "%.2f", costcoPrice)}"
        currentView.findViewById<TextView>(R.id.tvPrice3).text =
            "$${String.format(Locale.getDefault(), "%.2f", superstorePrice)}"

        val badgeCostco = currentView.findViewById<TextView>(R.id.badgeCostco)
        val badgeWalmart = currentView.findViewById<TextView>(R.id.badgeWalmart)
        val badgeSuperstore = currentView.findViewById<TextView>(R.id.badgeSuperstore)

        val tvTopStore = currentView.findViewById<TextView>(R.id.tvTopDealStore)
        val tvTopPrice = currentView.findViewById<TextView>(R.id.tvTopDealPrice)
        val btnAddBestDeal = currentView.findViewById<Button>(R.id.btnAddBestDeal)

        badgeCostco.visibility = View.GONE
        badgeWalmart.visibility = View.GONE
        badgeSuperstore.visibility = View.GONE

        var bestStoreName = ""
        var bestPrice = 0.0

        when (minPrice) {
            costcoPrice -> {
                badgeCostco.visibility = View.VISIBLE
                tvTopStore.text = "Costco"
                tvTopStore.setTextColor(Color.parseColor("#E31837"))
                tvTopPrice.text = "At $${String.format(Locale.getDefault(), "%.2f", costcoPrice)}, this is the lowest price."
                bestStoreName = "Costco"
                bestPrice = costcoPrice
            }

            walmartPrice -> {
                badgeWalmart.visibility = View.VISIBLE
                tvTopStore.text = "Walmart"
                tvTopStore.setTextColor(Color.parseColor("#004FB6"))
                tvTopPrice.text = "At $${String.format(Locale.getDefault(), "%.2f", walmartPrice)}, this is the lowest price."
                bestStoreName = "Walmart"
                bestPrice = walmartPrice
            }

            else -> {
                badgeSuperstore.visibility = View.VISIBLE
                tvTopStore.text = "Superstore"
                tvTopStore.setTextColor(Color.parseColor("#2E9E47"))
                tvTopPrice.text = "At $${String.format(Locale.getDefault(), "%.2f", superstorePrice)}, this is the lowest price."
                bestStoreName = "Superstore"
                bestPrice = superstorePrice
            }
        }

        btnAddBestDeal.apply {
            isEnabled = true
            alpha = 1f
            text = "Add Best Deal to List"
            setOnClickListener {
                val newItem = CartItem(
                    name = foundProductName,
                    price = bestPrice,
                    store = bestStoreName,
                    type = foundCategoryName,
                    quantity = 1
                )

                viewModel.draftCartList.add(newItem)

                Toast.makeText(
                    requireContext(),
                    "Added $foundProductName from $bestStoreName",
                    Toast.LENGTH_SHORT
                ).show()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AddItemsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun searchByBarcode(barcode: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = openFoodFactsService.getProductByBarcode(barcode)
                val apiProduct = parseBarcodeResponse(response)

                withContext(Dispatchers.Main) {
                    if (apiProduct != null) {
                        updateUIWithApiResult(apiProduct)
                    } else {
                        showNoResultState(barcode)
                        Toast.makeText(
                            requireContext(),
                            "No product found for barcode $barcode",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showNoResultState(barcode)
                }
            }
        }
    }

    private fun searchByText(query: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = openFoodFactsService.searchProducts(query)
                val apiProduct = parseSearchResponse(query, response)

                withContext(Dispatchers.Main) {
                    if (apiProduct != null) {
                        updateUIWithApiResult(apiProduct)
                    } else {
                        showNoResultState(query)
                        Toast.makeText(
                            requireContext(),
                            "No product found for \"$query\"",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showNoResultState(query)
                }
            }
        }
    }

    private fun parseBarcodeResponse(jsonResponse: String): ApiProductResult? {
        return try {
            if (jsonResponse.isBlank()) return null

            val root = JSONObject(jsonResponse)
            if (root.optInt("status", 0) != 1) return null

            val product = root.optJSONObject("product") ?: return null

            val name = product.optString(
                "product_name_en",
                product.optString("product_name", "")
            ).trim()

            if (name.isBlank()) return null

            val brand = product.optString("brands", "Unknown Brand").trim()
            val quantity = product.optString("quantity", "No quantity listed").trim()
            val barcode = product.optString("code", "").trim()

            ApiProductResult(
                name = name,
                brand = if (brand.isBlank()) "Unknown Brand" else brand,
                quantity = if (quantity.isBlank()) "No quantity listed" else quantity,
                barcode = barcode
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSearchResponse(query: String, jsonResponse: String): ApiProductResult? {
        return try {
            if (jsonResponse.isBlank()) return null

            val root = JSONObject(jsonResponse)
            val products = root.optJSONArray("products") ?: return null
            if (products.length() == 0) return null

            val normalizedQuery = query.trim().lowercase(Locale.getDefault())
            val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

            var bestMatch: ApiProductResult? = null

            for (i in 0 until products.length()) {
                val product = products.getJSONObject(i)

                val name = product.optString(
                    "product_name_en",
                    product.optString("product_name", "")
                ).trim()

                if (name.isBlank()) continue

                val brand = product.optString("brands", "Unknown Brand").trim()
                val quantity = product.optString("quantity", "No quantity listed").trim()
                val barcode = product.optString("code", "").trim()

                val candidate = ApiProductResult(
                    name = name,
                    brand = if (brand.isBlank()) "Unknown Brand" else brand,
                    quantity = if (quantity.isBlank()) "No quantity listed" else quantity,
                    barcode = barcode
                )

                val searchableText = "$name $brand".lowercase(Locale.getDefault())

                if (searchableText == normalizedQuery) {
                    return candidate
                }

                if (searchableText.contains(normalizedQuery)) {
                    return candidate
                }

                if (bestMatch == null && queryTokens.all { searchableText.contains(it) }) {
                    bestMatch = candidate
                }

                if (bestMatch == null) {
                    bestMatch = candidate
                }
            }

            bestMatch
        } catch (_: Exception) {
            null
        }
    }

    private fun updateUIWithApiResult(apiProduct: ApiProductResult) {
        val currentView = view ?: return

        currentView.findViewById<TextView>(R.id.tvTitle).text = apiProduct.name
        currentView.findViewById<TextView>(R.id.tvPrice1).text = "N/A"
        currentView.findViewById<TextView>(R.id.tvPrice2).text = "N/A"
        currentView.findViewById<TextView>(R.id.tvPrice3).text = "N/A"

        currentView.findViewById<TextView>(R.id.badgeCostco).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeWalmart).visibility = View.GONE
        currentView.findViewById<TextView>(R.id.badgeSuperstore).visibility = View.GONE

        val tvTopStore = currentView.findViewById<TextView>(R.id.tvTopDealStore)
        val tvTopPrice = currentView.findViewById<TextView>(R.id.tvTopDealPrice)
        val btnAddBestDeal = currentView.findViewById<Button>(R.id.btnAddBestDeal)

        tvTopStore.text = "Verified Product Info"
        tvTopStore.setTextColor(Color.parseColor("#8D6E63"))
        tvTopPrice.text = "Product found from Open Food Facts."

        showApiInfoCard(apiProduct)

        btnAddBestDeal.apply {
            isEnabled = true
            alpha = 1f
            text = "Add Product to List"
            setOnClickListener {
                val newItem = CartItem(
                    name = apiProduct.name,
                    price = 0.0,
                    store = "Open Food Facts",
                    type = "Product Info",
                    quantity = 1
                )

                viewModel.draftCartList.add(newItem)

                Toast.makeText(
                    requireContext(),
                    "Added ${apiProduct.name}",
                    Toast.LENGTH_SHORT
                ).show()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, AddItemsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun fetchApiAndMerge(
        query: String,
        localPrices: List<Double>?,
        localName: String?,
        localCategory: String?
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = openFoodFactsService.searchProducts(query)
                val apiProduct = parseSearchResponse(query, response)

                withContext(Dispatchers.Main) {

                    // 🔥 ALWAYS SHOW LOCAL IF EXISTS
                    if (localPrices != null && localPrices.size == 3 && localName != null && localCategory != null) {
                        updateUIWithSearchResults(localName, localCategory, localPrices)

                        // 🔥 ALSO SHOW API INFO
                        if (apiProduct != null) {
                            showApiInfoCard(apiProduct)
                        }
                    }

                    // 🔥 ONLY API (no local)
                    else if (apiProduct != null) {
                        updateUIWithApiResult(apiProduct)
                    }

                    // 🔥 NOTHING
                    else {
                        showNoResultState(query)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (localPrices != null && localName != null && localCategory != null) {
                        updateUIWithSearchResults(localName, localCategory, localPrices)
                    } else {
                        showNoResultState(query)
                    }
                }
            }
        }
    }


    private fun showApiInfoCard(apiProduct: ApiProductResult) {
        val currentView = view ?: return

        val cardApiInfo = currentView.findViewById<CardView>(R.id.cardApiInfo)
        val tvApiInfoTitle = currentView.findViewById<TextView>(R.id.tvApiInfoTitle)
        val tvApiInfoSubtitle = currentView.findViewById<TextView>(R.id.tvApiInfoSubtitle)
        val tvApiInfoDetails = currentView.findViewById<TextView>(R.id.tvApiInfoDetails)

        tvApiInfoTitle.text = "Verified Product Info"
        tvApiInfoSubtitle.text = "From Open Food Facts"
        tvApiInfoDetails.text =
            "Name: ${apiProduct.name}\n" +
                    "Brand: ${apiProduct.brand}\n" +
                    "Quantity: ${apiProduct.quantity}"

        cardApiInfo.visibility = View.VISIBLE
    }

    private fun hideApiInfoCard() {
        val currentView = view ?: return
        currentView.findViewById<CardView>(R.id.cardApiInfo).visibility = View.GONE
    }


}