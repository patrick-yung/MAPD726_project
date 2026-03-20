package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONArray
import org.json.JSONObject

class AddItemsFragment : BottomSheetDialogFragment() {

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager

    // Views
    private lateinit var btnBackArrow: ImageView
    private lateinit var btnSaveListHeader: TextView
    private lateinit var llCartItemsContainer: LinearLayout
    private lateinit var tvEmptyHint: TextView
    private lateinit var etItemType: AutoCompleteTextView
    private lateinit var etProductName: AutoCompleteTextView
    private lateinit var etQuantity: AutoCompleteTextView
    private lateinit var btnAddItem: Button
    private lateinit var tvPriceWalmart: TextView
    private lateinit var tvPriceCostco: TextView
    private lateinit var tvPriceSuperstore: TextView
    private lateinit var containerWalmart: LinearLayout
    private lateinit var containerCostco: LinearLayout
    private lateinit var containerSuperstore: LinearLayout

    // Data
    private var categories = listOf<String>()
    private var productsMap = mutableMapOf<String, MutableMap<String, List<Double>>>()
    private var currentPrices: List<Double>? = null
    private var selectedStore = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_items, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(ListViewModel::class.java)
        apiService = ApiService(requireContext().getString(R.string.base_url))
        sessionManager = SessionManager(requireContext())

        initViews(view)
        setupClickListeners()

        // Fetch categorized data if not already loaded
        if (viewModel.categorizedData.value == null) {
            viewModel.fetchCategorizedData(apiService)
        }

        // Observe categorized data
        viewModel.categorizedData.observe(viewLifecycleOwner) { categorizedData ->
            if (categorizedData != null && categorizedData.isNotEmpty()) {
                setupDropdowns(categorizedData)
            }
        }

        // Display existing cart items
        displayCartItems()
    }

    private fun initViews(view: View) {
        btnBackArrow = view.findViewById(R.id.btnBackArrow)
        btnSaveListHeader = view.findViewById(R.id.btnSaveListHeader)
        llCartItemsContainer = view.findViewById(R.id.llCartItemsContainer)
        tvEmptyHint = view.findViewById(R.id.tvEmptyHint)
        etItemType = view.findViewById(R.id.etItemType)
        etProductName = view.findViewById(R.id.etProductName)
        etQuantity = view.findViewById(R.id.etQuantity)
        btnAddItem = view.findViewById(R.id.btnAddItem)
        tvPriceWalmart = view.findViewById(R.id.tvPriceWalmart)
        tvPriceCostco = view.findViewById(R.id.tvPriceCostco)
        tvPriceSuperstore = view.findViewById(R.id.tvPriceSuperstore)
        containerWalmart = view.findViewById(R.id.containerWalmart)
        containerCostco = view.findViewById(R.id.containerCostco)
        containerSuperstore = view.findViewById(R.id.containerSuperstore)

        etQuantity.setText("1")
    }

    private fun setupClickListeners() {
        btnBackArrow.setOnClickListener {
            dismiss()
        }

        btnSaveListHeader.setOnClickListener {
            saveCurrentList()
        }

        btnAddItem.setOnClickListener {
            addItemToCart()
        }

        containerWalmart.setOnClickListener { selectStore("Walmart") }
        containerCostco.setOnClickListener { selectStore("Costco") }
        containerSuperstore.setOnClickListener { selectStore("Superstore") }
    }

    private fun setupDropdowns(categorizedData: Map<String, Map<String, List<Double>>>) {
        categories = categorizedData.keys.toList()

        // Build products map with prices
        productsMap.clear()
        for ((category, products) in categorizedData) {
            val productMap = mutableMapOf<String, List<Double>>()
            for ((productName, prices) in products) {
                productMap[productName] = prices
            }
            productsMap[category] = productMap
        }

        // Setup category dropdown
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        etItemType.setAdapter(categoryAdapter)

        etItemType.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            updateProductDropdown(selectedCategory)
            etProductName.setText("")
            clearPriceDisplays()
        }

        // Setup product dropdown
        etProductName.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = etItemType.text.toString()
            val selectedProduct = currentProducts[position]
            updatePriceDisplays(selectedCategory, selectedProduct)
        }

        // Setup quantity dropdown
        val quantities = (1..20).map { it.toString() }
        val quantityAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            quantities
        )
        etQuantity.setAdapter(quantityAdapter)

        // Auto-select if only one category
        if (categories.size == 1) {
            etItemType.setText(categories[0], false)
            updateProductDropdown(categories[0])
        }
    }

    private var currentProducts = listOf<String>()

    private fun updateProductDropdown(category: String) {
        currentProducts = productsMap[category]?.keys?.toList() ?: emptyList()

        val productAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            currentProducts
        )
        etProductName.setAdapter(productAdapter)
    }

    private fun updatePriceDisplays(category: String, productName: String) {
        currentPrices = productsMap[category]?.get(productName)

        if (currentPrices != null && currentPrices!!.size == 3) {
            tvPriceWalmart.text = "$${currentPrices!![0]}"
            tvPriceCostco.text = "$${currentPrices!![1]}"
            tvPriceSuperstore.text = "$${currentPrices!![2]}"

            // Auto-select best price
            val minPrice = currentPrices!!.minOrNull() ?: return
            when (minPrice) {
                currentPrices!![0] -> selectStore("Walmart")
                currentPrices!![1] -> selectStore("Costco")
                currentPrices!![2] -> selectStore("Superstore")
            }
        } else {
            clearPriceDisplays()
        }
    }

    private fun selectStore(store: String) {
        selectedStore = store

        // Reset backgrounds
        containerWalmart.setBackgroundColor(0x00000000)
        containerCostco.setBackgroundColor(0x00000000)
        containerSuperstore.setBackgroundColor(0x00000000)

        // Highlight selected
        when (store) {
            "Walmart" -> containerWalmart.setBackgroundColor(0x20FFA500)
            "Costco" -> containerCostco.setBackgroundColor(0x20FFA500)
            "Superstore" -> containerSuperstore.setBackgroundColor(0x20FFA500)
        }
    }

    private fun clearPriceDisplays() {
        tvPriceWalmart.text = "--"
        tvPriceCostco.text = "--"
        tvPriceSuperstore.text = "--"

        containerWalmart.setBackgroundColor(0x00000000)
        containerCostco.setBackgroundColor(0x00000000)
        containerSuperstore.setBackgroundColor(0x00000000)

        selectedStore = ""
        currentPrices = null
    }

    private fun addItemToCart() {
        val selectedCategory = etItemType.text.toString()
        val selectedProduct = etProductName.text.toString()
        val quantity = etQuantity.text.toString().toIntOrNull() ?: 1

        if (selectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedProduct.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a product", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedStore.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a store", Toast.LENGTH_SHORT).show()
            return
        }

        val price = when (selectedStore) {
            "Walmart" -> currentPrices?.get(0)
            "Costco" -> currentPrices?.get(1)
            "Superstore" -> currentPrices?.get(2)
            else -> null
        }

        if (price == null) {
            Toast.makeText(requireContext(), "Invalid price", Toast.LENGTH_SHORT).show()
            return
        }

        val newItem = CartItem(
            name = selectedProduct,
            price = price,
            store = selectedStore,
            type = selectedCategory,
            quantity = quantity
        )

        viewModel.draftCartList.add(newItem)
        displayCartItems()

        // Reset after adding
        clearPriceDisplays()
        etProductName.setText("")

        Toast.makeText(requireContext(), "Added $quantity x $selectedProduct", Toast.LENGTH_SHORT).show()
    }

    private fun displayCartItems() {
        llCartItemsContainer.removeAllViews()

        if (viewModel.draftCartList.isEmpty()) {
            tvEmptyHint.visibility = View.VISIBLE
            return
        }

        tvEmptyHint.visibility = View.GONE

        for ((index, item) in viewModel.draftCartList.withIndex()) {
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                setPadding(16, 12, 16, 12)
                setBackgroundColor(0xFFF5F5F5.toInt())
            }

            val detailsLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nameText = TextView(requireContext()).apply {
                text = "${item.quantity}x ${item.name}"
                textSize = 16f
                setTextColor(0xFF212121.toInt())
            }

            val storeText = TextView(requireContext()).apply {
                text = item.store
                textSize = 12f
                setTextColor(0xFF757575.toInt())
            }

            detailsLayout.addView(nameText)
            detailsLayout.addView(storeText)

            val priceText = TextView(requireContext()).apply {
                text = "$${String.format("%.2f", item.price * item.quantity)}"
                textSize = 16f
                setTextColor(0xFFFF9800.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
            }

            val removeBtn = Button(requireContext()).apply {
                text = "X"
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFFFF4444.toInt())
                setPadding(24, 8, 24, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    viewModel.draftCartList.removeAt(index)
                    displayCartItems()
                }
            }

            itemLayout.addView(detailsLayout)
            itemLayout.addView(priceText)
            itemLayout.addView(removeBtn)

            llCartItemsContainer.addView(itemLayout)
        }
    }

    private fun saveCurrentList() {
        if (viewModel.draftCartList.isEmpty()) {
            Toast.makeText(requireContext(), "No items to save", Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(requireContext())
        editText.hint = "Enter list name"

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Save Shopping List")
            .setMessage("What would you like to name this list?")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val listName = editText.text.toString().ifEmpty { "Shopping List" }
                saveListToBackend(listName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveListToBackend(listName: String) {
        val itemsArray = JSONArray()
        for (item in viewModel.draftCartList) {
            val itemJson = JSONObject().apply {
                put("name", "${item.name} at ${item.store}")
                put("price", item.price)
                put("isChecked", false)
            }
            itemsArray.put(itemJson)
        }

        val shopListJson = JSONObject().apply {
            put("topic", listName)
            put("items", itemsArray)
        }

        val userId = sessionManager.getUserId() ?: ""

        Thread {
            try {
                val response = apiService.createShopList(userId, shopListJson)

                requireActivity().runOnUiThread {
                    if (response.success) {
                        Toast.makeText(requireContext(), "List saved!", Toast.LENGTH_SHORT).show()
                        viewModel.draftCartList.clear()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}

data class CartItem(
    val name: String,
    val price: Double,
    val store: String,
    val type: String,
    var quantity: Int = 1
)