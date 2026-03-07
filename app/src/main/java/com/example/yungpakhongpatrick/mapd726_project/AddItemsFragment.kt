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
data class CartItem(val name: String, val price: Double, val store: String, val type: String, val quantity: Int)
class AddItemsFragment : BaseFragment(R.layout.fragment_add_items) {

    // Variables to track which store is selected
    private var selectedStoreName: String? = null
    private var selectedPrice: Double = 0.0

    // Product Data
    private val categorizedData = mapOf(
        "Poultry" to mapOf(
            "Milk (4L)" to listOf(5.49, 5.29, 5.59),
            "Eggs (12pk)" to listOf(3.99, 3.50, 4.10)
        ),
        "Bakery" to mapOf(
            "Bread" to listOf(2.99, 2.79, 3.29),
            "Bagels (6pk)" to listOf(3.49, 3.99, 3.29)
        ),
        "Produce" to mapOf(
            "Bananas" to listOf(0.79, 0.69, 0.89),
            "Apples (1lb)" to listOf(2.49, 2.99, 2.29)
        ),
        "Pantry" to mapOf(
            "Rice (8kg)" to listOf(18.99, 17.99, 19.49),
            "Flour (2kg)" to listOf(4.49, 4.99, 4.29),
            "Sugar (1kg)" to listOf(2.99, 2.79, 3.19)
        )
    )
    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private var currentUserId: String = ""

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

        // 1. Setup Item Type (Category) Dropdown
        val categories = categorizedData.keys.toList()
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        etItemType.setAdapter(typeAdapter)

        etItemType.setOnClickListener { etItemType.showDropDown() }

        // 2. When Category is picked -> Fill the Product Dropdown
        etItemType.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            val productsInCategory = categorizedData[selectedCategory]?.keys?.toList() ?: emptyList()

            val productAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, productsInCategory)
            etProductName.setAdapter(productAdapter)

            // Clear old selections
            etProductName.text.clear()
            tvPriceWalmart.text = "--"
            tvPriceCostco.text = "--"
            tvPriceSuperstore.text = "--"
            resetStoreSelection(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = null
        }

        etProductName.setOnClickListener { etProductName.showDropDown() }

        // 3. When Product is picked -> Show the Prices
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

        // Handle Store Clicks (Walmart)
        containerWalmart.setOnClickListener {
            highlightStore(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = "Walmart"
            val priceText = tvPriceWalmart.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // Handle Store Clicks (Costco)
        containerCostco.setOnClickListener {
            highlightStore(containerCostco, containerWalmart, containerSuperstore)
            selectedStoreName = "Costco"
            val priceText = tvPriceCostco.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // Handle Store Clicks (Superstore)
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

            // Safely convert quantity to a number, default to 1
            val quantity = if (quantityStr.isNotEmpty()) quantityStr.toInt() else 1

            if (itemType.isEmpty() || productName.isEmpty() || selectedStoreName == null) {
                Toast.makeText(requireContext(), "Select type, product, and store!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save the item
            val newItem = CartItem(productName, selectedPrice, selectedStoreName!!, itemType, quantity)
            viewModel.draftCartList.add(newItem)
//            currentCartList.add(newItem)
            tvEmptyHint.visibility = View.GONE

            // Calculate Total: Price x Quantity
            val totalItemPrice = newItem.price * quantity

            // Format for UI
            val itemText = "${viewModel.draftCartList.size}. ${newItem.name} (${newItem.type}) x$quantity - $${String.format(Locale.getDefault(), "%.2f", totalItemPrice)} at ${newItem.store}"

            // Add to Screen
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_cart_simple, llCartItemsContainer, false)

            val tvName = itemView.findViewById<TextView>(R.id.item_name)
            tvName.text = itemText
            itemView.findViewById<TextView>(R.id.item_details).visibility = View.GONE

            llCartItemsContainer.addView(itemView, 0)

            //Turns orange when they add an item
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
        // Save List Button - Using dynamic user ID
        btnSaveList.setOnClickListener {
            if (viewModel.draftCartList.isEmpty()) {
                Toast.makeText(requireContext(), "Your list is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show dialog to get list name
            showSaveListDialog(llCartItemsContainer, tvEmptyHint)
        }
        if (viewModel.draftCartList.isNotEmpty()) {
            tvEmptyHint.visibility = View.GONE

            // Keep the save button orange if they have unsaved items
            val btnSaveHeader = view.findViewById<TextView>(R.id.btnSaveListHeader)
            btnSaveHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.smart_cart_orange))
            btnSaveHeader.text = "Save (${viewModel.draftCartList.size})"

            // Clear container to prevent duplicates
            llCartItemsContainer.removeAllViews()

            // Re-draw each item onto the screen
            viewModel.draftCartList.forEachIndexed { index, item ->
                val totalItemPrice = item.price * item.quantity
                val itemText = "${index + 1}. ${item.name} (${item.type}) x${item.quantity} - $${String.format(Locale.getDefault(), "%.2f", totalItemPrice)} at ${item.store}"

                val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_cart_simple, llCartItemsContainer, false)
                val tvName = itemView.findViewById<TextView>(R.id.item_name)
                tvName.text = itemText
                itemView.findViewById<TextView>(R.id.item_details).visibility = View.GONE

                // Add to the bottom so the numbers stay in order (1, 2, 3...)
                llCartItemsContainer.addView(itemView)
            }
        }
    }

    // Function to save list to backend - using dynamic user ID
    private suspend fun saveListToBackend(listName: String, items: List<CartItem>): Boolean {
        return try {
            Log.d("SAVE", "=== Starting save process ===")
            Log.d("SAVE", "List name: $listName")
            Log.d("SAVE", "Number of items: ${items.size}")
            Log.d("SAVE", "Using user ID: $currentUserId")

            // Create JSON for items
            val itemsArray = JSONArray()
            items.forEachIndexed { index, item ->
                val itemObj = JSONObject().apply {
                    // Saves as: "Rice (8kg) (Pantry) x2 at Walmart"
                    put("name", "${item.name} (${item.type}) x${item.quantity} at ${item.store}")
                    // Saves the total price for that row
                    put("price", item.price * item.quantity)
                    put("isChecked", false)
                }
                itemsArray.put(itemObj)
            }

            // Create request body for shop list
            val requestBody = JSONObject().apply {
                put("topic", listName)
                put("items", itemsArray)
            }

            Log.d("SAVE", "Request body: $requestBody")
            Log.d("SAVE", "Sending POST to: /users/$currentUserId/shoplists")

            // Make API call to create shop list
            val response = apiService.createShopList(currentUserId, requestBody)

            Log.d("SAVE", "Create shop list response - Success: ${response.success}, Code: ${response.statusCode}")
            Log.d("SAVE", "Response body: ${response.body}")
            Log.d("SAVE", "Error message: ${response.errorMessage}")

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

    // Function to show the save list dialog
    private fun showSaveListDialog(llCartItemsContainer: LinearLayout, tvEmptyHint: TextView) {
        // 1. Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_list, null)
        val etListName = dialogView.findViewById<EditText>(R.id.etListName)

        // 2. Create the AlertDialog
        val alertDialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        // 3. Handle the "Save" click with validation
        alertDialog.setOnShowListener {
            val saveButton = alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val listName = etListName.text.toString().trim()

                if (listName.isEmpty()) {
                    etListName.error = "Please enter a list name"
                } else {
                    // Disable button to prevent double clicks
                    saveButton.isEnabled = false
                    saveButton.text = "Saving..."

                    // Save to backend in background
                    CoroutineScope(Dispatchers.IO).launch {
                        val success = saveListToBackend(listName, viewModel.draftCartList)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                // Also save locally via ViewModel
                                saveListToLocal(listName, viewModel.draftCartList)

                                Toast.makeText(requireContext(), "List '$listName' saved to cloud!", Toast.LENGTH_SHORT).show()
                                alertDialog.dismiss()

                                // Clear the current UI list after saving
                                viewModel.draftCartList.clear()
                                llCartItemsContainer.removeAllViews()
                                tvEmptyHint.visibility = View.VISIBLE

                                val btnSaveHeader = requireView().findViewById<TextView>(R.id.btnSaveListHeader)
                                btnSaveHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                                btnSaveHeader.text = "Save"
                            } else {
                                Toast.makeText(requireContext(), "Failed to save to cloud. Please check Logcat for details.", Toast.LENGTH_LONG).show()
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

    // --- HELPER FUNCTIONS ---

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
            // If the cart is empty, just let them leave normally
            parentFragmentManager.popBackStack()
        } else {
            // If they have items, show the warning popup!
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Unsaved Items!")
                .setMessage("You have items in your list that haven't been saved. Are you sure you want to leave without saving?")
                .setPositiveButton("Leave Anyway") { _, _ ->
                    // Clear the draft list and let them leave
                    viewModel.draftCartList.clear()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("Cancel", null) // Do nothing, close popup
                .show()
        }
    }
}