package com.example.yungpakhongpatrick.mapd726_project

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.yungpakhongpatrick.mapd726_project.BaseFragment
import com.example.yungpakhongpatrick.mapd726_project.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.ContextCompat

// Simple data class
data class CartItem(val name: String, val price: Double, val store: String, val type: String, var quantity: Int)

class AddItemsFragment : BaseFragment(R.layout.fragment_add_items) {

    // Variables to track which store is selected
    private var selectedStoreName: String? = null
    private var selectedPrice: Double = 0.0

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private var currentUserId: String = ""

    // Store categorized data locally
    private var categorizedData: Map<String, Map<String, List<Double>>> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SessionManager and get user ID
        sessionManager = SessionManager(requireContext())

        if (!sessionManager.isLoggedIn()) {
            Log.e("AddItemsFragment", "User not logged in!")
            navigateToLogin()
            return
        }

        currentUserId = sessionManager.getUserId() ?: run {
            Log.e("AddItemsFragment", "No user ID found!")
            navigateToLogin()
            return
        }

        Log.d("AddItemsFragment", "Current User ID: $currentUserId")

        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        // Initialize ApiService with base URL from strings.xml
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)
        Log.d("AddItemsFragment", "Base URL: $baseUrl")
        Log.d("AddItemsFragment", "Using user ID: $currentUserId")

        // Find Views
        val etProductName = view.findViewById<AutoCompleteTextView>(R.id.etProductName)
        val tvPriceWalmart = view.findViewById<TextView>(R.id.tvPriceWalmart)
        val tvPriceCostco = view.findViewById<TextView>(R.id.tvPriceCostco)
        val tvPriceSuperstore = view.findViewById<TextView>(R.id.tvPriceSuperstore)

        val etQuantity = view.findViewById<AutoCompleteTextView>(R.id.etQuantity)

        // Setup Quantity Dropdown
        val quantities = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        val qtyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, quantities)
        etQuantity.setAdapter(qtyAdapter)

        // Connect the Top-Left Back Arrow to the popup
        val btnBackArrow = view.findViewById<View>(R.id.btnBackArrow)
        btnBackArrow.setOnClickListener {
            handleExitAttempt()
        }

        // Connect the Android System Physical Back Button to the popup
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitAttempt()
            }
        })

        // Force the dropdown to show when clicked
        etQuantity.setOnClickListener {
            etQuantity.showDropDown()
        }

        // Store Layout Containers
        val containerWalmart = view.findViewById<LinearLayout>(R.id.containerWalmart)
        val containerCostco = view.findViewById<LinearLayout>(R.id.containerCostco)
        val containerSuperstore = view.findViewById<LinearLayout>(R.id.containerSuperstore)

        val llCartItemsContainer = view.findViewById<LinearLayout>(R.id.llCartItemsContainer)
        val tvEmptyHint = view.findViewById<TextView>(R.id.tvEmptyHint)
        val btnAddItem = view.findViewById<Button>(R.id.btnAddItem)

        val btnSaveList = view.findViewById<TextView>(R.id.btnSaveListHeader)
        val etItemType = view.findViewById<AutoCompleteTextView>(R.id.etItemType)

        // Fetch categorized data if not loaded
        if (viewModel.categorizedData.value == null) {
            viewModel.fetchCategorizedData(apiService)
        }

        // Observe categorized data
        viewModel.categorizedData.observe(viewLifecycleOwner) { data ->
            if (data != null && data.isNotEmpty()) {
                categorizedData = data
                setupCategoryDropdown(etItemType, etProductName, tvPriceWalmart, tvPriceCostco, tvPriceSuperstore,
                    containerWalmart, containerCostco, containerSuperstore)
            }
        }

        // Observe loading state
        viewModel.isLoadingProducts.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                Toast.makeText(requireContext(), "Loading products...", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe errors
        viewModel.productError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup store click listeners
        containerWalmart.setOnClickListener {
            highlightStore(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = "Walmart"
            val priceText = tvPriceWalmart.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        containerCostco.setOnClickListener {
            highlightStore(containerCostco, containerWalmart, containerSuperstore)
            selectedStoreName = "Costco"
            val priceText = tvPriceCostco.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        containerSuperstore.setOnClickListener {
            highlightStore(containerSuperstore, containerWalmart, containerCostco)
            selectedStoreName = "Superstore"
            val priceText = tvPriceSuperstore.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // Add Item Button Logic
        btnAddItem.setOnClickListener {
            val itemType = etItemType.text.toString().trim()
            val productName = etProductName.text.toString().trim()
            val quantityStr = etQuantity.text.toString().trim()

            val quantity = if (quantityStr.isNotEmpty()) quantityStr.toInt() else 1

            if (itemType.isEmpty() || productName.isEmpty() || selectedStoreName == null) {
                Toast.makeText(requireContext(), "Select type, product, and store!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newItem = CartItem(productName, selectedPrice, selectedStoreName!!, itemType, quantity)
            viewModel.draftCartList.add(newItem)
            tvEmptyHint.visibility = View.GONE

            refreshCartUI(llCartItemsContainer, tvEmptyHint, btnSaveList)

            val btnSaveHeader = view.findViewById<TextView>(R.id.btnSaveListHeader)
            btnSaveHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.smart_cart_orange))
            btnSaveHeader.text = "Save (${viewModel.draftCartList.size})"
            Toast.makeText(requireContext(), "Item added! Don't forget to Save.", Toast.LENGTH_SHORT).show()

            etItemType.setText("", false)
            etProductName.setText("", false)
            etQuantity.setText("1", false)

            tvPriceWalmart.text = "--"
            tvPriceCostco.text = "--"
            tvPriceSuperstore.text = "--"
            resetStoreSelection(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = null
        }

        // Save List Button
        btnSaveList.setOnClickListener {
            if (viewModel.draftCartList.isEmpty()) {
                Toast.makeText(requireContext(), "Your list is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSaveListDialog(llCartItemsContainer, tvEmptyHint)
        }

        val btnSaveHeader = view.findViewById<TextView>(R.id.btnSaveListHeader)
        refreshCartUI(llCartItemsContainer, tvEmptyHint, btnSaveHeader)
    }

    private fun setupCategoryDropdown(
        etItemType: AutoCompleteTextView,
        etProductName: AutoCompleteTextView,
        tvPriceWalmart: TextView,
        tvPriceCostco: TextView,
        tvPriceSuperstore: TextView,
        containerWalmart: LinearLayout,
        containerCostco: LinearLayout,
        containerSuperstore: LinearLayout
    ) {
        val categories = categorizedData.keys.toList()
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        etItemType.setAdapter(typeAdapter)
        etItemType.setOnClickListener { etItemType.showDropDown() }

        // When Category is picked -> Fill the Product Dropdown
        etItemType.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            val productsInCategory = categorizedData[selectedCategory]?.keys?.toList() ?: emptyList()

            val productAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productsInCategory)
            etProductName.setAdapter(productAdapter)

            etProductName.text.clear()
            tvPriceWalmart.text = "--"
            tvPriceCostco.text = "--"
            tvPriceSuperstore.text = "--"
            resetStoreSelection(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = null
        }

        etProductName.setOnClickListener { etProductName.showDropDown() }

        // When Product is picked -> Show the Prices
        etProductName.setOnItemClickListener { _, _, _, _ ->
            val selectedCategory = etItemType.text.toString()
            val selectedProduct = etProductName.text.toString()

            val prices = categorizedData[selectedCategory]?.get(selectedProduct)
            if (prices != null) {
                tvPriceWalmart.text = "$${prices[0]}"
                tvPriceCostco.text = "$${prices[1]}"
                tvPriceSuperstore.text = "$${prices[2]}"
                resetStoreSelection(containerWalmart, containerCostco, containerSuperstore)
                selectedStoreName = null
            }
        }
    }

    private fun highlightStore(selected: View, other1: View, other2: View) {
        selected.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.best_deal_bg))
        other1.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_background))
        other2.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_background))
    }

    private fun resetStoreSelection(v1: View, v2: View, v3: View) {
        v1.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_background))
        v2.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_background))
        v3.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_background))
    }

    private suspend fun saveListToBackend(listName: String, items: List<CartItem>): Boolean {
        return try {
            Log.d("SAVE", "=== Starting save process ===")
            Log.d("SAVE", "List name: $listName")
            Log.d("SAVE", "Number of items: ${items.size}")
            Log.d("SAVE", "Using user ID: $currentUserId")

            val itemsArray = JSONArray()
            items.forEach { item ->
                val itemObj = JSONObject().apply {
                    put("name", "${item.name} (${item.type}) x${item.quantity} at ${item.store}")
                    put("price", item.price * item.quantity)
                    put("isChecked", false)
                }
                itemsArray.put(itemObj)
            }

            val requestBody = JSONObject().apply {
                put("topic", listName)
                put("items", itemsArray)
            }

            Log.d("SAVE", "Sending POST to: /users/$currentUserId/shoplists")
            val response = apiService.createShopList(currentUserId, requestBody)

            if (response.success) {
                Log.d("SAVE", "✓ List saved successfully!")
                true
            } else {
                Log.e("SAVE", "✗ Failed to save list. Code: ${response.statusCode}")
                false
            }
        } catch (e: Exception) {
            Log.e("SAVE", "✗ Exception in saveListToBackend", e)
            false
        }
    }

    private fun showSaveListDialog(llCartItemsContainer: LinearLayout, tvEmptyHint: TextView) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_list, null)
        val etListName = dialogView.findViewById<EditText>(R.id.etListName)

        val alertDialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialog.setOnShowListener {
            val saveButton = alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val listName = etListName.text.toString().trim()

                if (listName.isEmpty()) {
                    etListName.error = "Please enter a list name"
                } else {
                    saveButton.isEnabled = false
                    saveButton.text = "Saving..."

                    CoroutineScope(Dispatchers.IO).launch {
                        val success = saveListToBackend(listName, viewModel.draftCartList)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                saveListToLocal(listName, viewModel.draftCartList)
                                Toast.makeText(requireContext(), "List '$listName' saved to cloud!", Toast.LENGTH_SHORT).show()
                                alertDialog.dismiss()
                                viewModel.draftCartList.clear()
                                parentFragmentManager.popBackStack()
                            } else {
                                Toast.makeText(requireContext(), "Failed to save to cloud. Check Logcat.", Toast.LENGTH_LONG).show()
                                saveButton.isEnabled = true
                                saveButton.text = "Save"
                            }
                        }
                    }
                }
            }
        }

        alertDialog.show()
    }

    private fun saveListToLocal(name: String, items: List<CartItem>) {
        val savedItems = items.map {
            SavedItem(
                name = it.name,
                price = it.price,
                store = it.store,
                isChecked = false
            )
        }

        val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())

        val backendId = "local_${System.currentTimeMillis()}"
        val localId = backendId.hashCode().toLong()

        val newListEntry = SavedList(
            id = localId,
            name = name,
            date = formattedDate,
            items = savedItems
        )

        viewModel.addList(newListEntry)
    }

    private fun navigateToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LogInFragment())
            .commit()
    }

    private fun handleExitAttempt() {
        if (viewModel.draftCartList.isEmpty()) {
            parentFragmentManager.popBackStack()
        } else {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Unsaved Items!")
                .setMessage("You have items in your list that haven't been saved. Are you sure you want to leave without saving?")
                .setPositiveButton("Leave Anyway") { _, _ ->
                    viewModel.draftCartList.clear()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshCartUI(
        llCartItemsContainer: LinearLayout,
        tvEmptyHint: TextView,
        btnSaveHeader: TextView
    ) {
        llCartItemsContainer.removeAllViews()

        if (viewModel.draftCartList.isEmpty()) {
            tvEmptyHint.visibility = View.VISIBLE
            btnSaveHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            btnSaveHeader.text = "Save"
            return
        }

        tvEmptyHint.visibility = View.GONE
        btnSaveHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.smart_cart_orange))
        btnSaveHeader.text = "Save (${viewModel.draftCartList.size})"

        viewModel.draftCartList.forEachIndexed { index, item ->
            val totalItemPrice = item.price * item.quantity
            val itemText = "${index + 1}. ${item.name} (${item.type}) x${item.quantity} - $${String.format(Locale.getDefault(), "%.2f", totalItemPrice)} at ${item.store}"

            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_cart_simple, llCartItemsContainer, false)
            val tvName = itemView.findViewById<TextView>(R.id.item_name)
            tvName.text = itemText
            itemView.findViewById<TextView>(R.id.item_details).visibility = View.GONE

            itemView.setOnClickListener {
                showEditQuantityDialog(item, llCartItemsContainer, tvEmptyHint, btnSaveHeader)
            }

            llCartItemsContainer.addView(itemView)
        }
    }

    private fun showEditQuantityDialog(
        item: CartItem,
        llCartItemsContainer: LinearLayout,
        tvEmptyHint: TextView,
        btnSaveHeader: TextView
    ) {
        val quantities = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Quantity: ${item.name}")
            .setItems(quantities) { _, which ->
                val newQuantity = quantities[which].toInt()
                item.quantity = newQuantity
                Toast.makeText(requireContext(), "Quantity updated!", Toast.LENGTH_SHORT).show()
                refreshCartUI(llCartItemsContainer, tvEmptyHint, btnSaveHeader)
            }
            .setNeutralButton("Delete Item") { _, _ ->
                viewModel.draftCartList.remove(item)
                refreshCartUI(llCartItemsContainer, tvEmptyHint, btnSaveHeader)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}