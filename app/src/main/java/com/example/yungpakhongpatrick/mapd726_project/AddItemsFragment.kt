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

class AddItemsFragment : BaseFragment(R.layout.fragment_add_items) {

    // --- 1. DEFINE VARIABLES AT CLASS LEVEL ---

    // Simple data class
    data class CartItem(val name: String, val price: Double, val store: String)

    // List to store added items
    private val currentCartList = ArrayList<CartItem>()

    // Variables to track which store is selected
    private var selectedStoreName: String? = null
    private var selectedPrice: Double = 0.0

    // Product Data
    private val productData = mapOf(
        "Milk (4L)" to listOf(5.49, 5.29, 5.59),
        "Eggs (12pk)" to listOf(3.99, 3.50, 4.10),
        "Bread" to listOf(2.99, 2.79, 3.29),
        "Bananas" to listOf(0.79, 0.69, 0.89),
        "Chicken" to listOf(14.00, 13.50, 14.50),
        "Rice (8kg)" to listOf(18.99, 17.99, 19.49)
    )

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private var userId: String? = null // Will store the user ID after creation/fetch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        // Initialize ApiService with base URL from strings.xml
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)

        // Find Views
        val etProductName = view.findViewById<AutoCompleteTextView>(R.id.etProductName)
        val tvPriceWalmart = view.findViewById<TextView>(R.id.tvPriceWalmart)
        val tvPriceCostco = view.findViewById<TextView>(R.id.tvPriceCostco)
        val tvPriceSuperstore = view.findViewById<TextView>(R.id.tvPriceSuperstore)

        // Store Layout Containers
        val containerWalmart = view.findViewById<LinearLayout>(R.id.containerWalmart) ?: view.findViewById<LinearLayout>(R.id.storeButtonsLayout).getChildAt(0) as LinearLayout
        val containerCostco = view.findViewById<LinearLayout>(R.id.containerCostco) ?: view.findViewById<LinearLayout>(R.id.storeButtonsLayout).getChildAt(1) as LinearLayout
        val containerSuperstore = view.findViewById<LinearLayout>(R.id.containerSuperstore) ?: view.findViewById<LinearLayout>(R.id.storeButtonsLayout).getChildAt(2) as LinearLayout

        val llCartItemsContainer = view.findViewById<LinearLayout>(R.id.llCartItemsContainer)
        val tvEmptyHint = view.findViewById<TextView>(R.id.tvEmptyHint)
        val btnAddItem = view.findViewById<Button>(R.id.btnAddItem)
        val btnSaveList = view.findViewById<Button>(R.id.btnSaveList)

        // Setup Dropdown Adapter
        val productNames = productData.keys.toList()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            productNames
        )
        etProductName.setAdapter(adapter)

        etProductName.setOnClickListener {
            etProductName.showDropDown()
        }

        // Handle selection from dropdown
        etProductName.setOnItemClickListener { parent, _, position, _ ->
            val selectedProduct = parent.getItemAtPosition(position).toString()
            val prices = productData[selectedProduct]
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
            val productName = etProductName.text.toString()

            if (productName.isEmpty() || selectedStoreName == null) {
                Toast.makeText(
                    requireContext(),
                    "Select product and store first!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else {
                val newItem = CartItem(productName, selectedPrice, selectedStoreName!!)
                currentCartList.add(newItem)
                tvEmptyHint.visibility = View.GONE

                // Format for list: 1. Milk $5.49 Walmart
                val itemText =
                    "${currentCartList.size}. ${newItem.name} $${newItem.price} ${newItem.store}"

                // Add to UI List
                val itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_cart_simple, llCartItemsContainer, false)

                val tvName = itemView.findViewById<TextView>(R.id.item_name)
                tvName.text = itemText

                itemView.findViewById<TextView>(R.id.item_details).visibility = View.GONE

                llCartItemsContainer.addView(itemView, 0)

                // Reset UI for next item
                etProductName.text.clear()
                tvPriceWalmart.text = "--"
                tvPriceCostco.text = "--"
                tvPriceSuperstore.text = "--"
                resetStoreSelection(containerWalmart, containerCostco, containerSuperstore)
                selectedStoreName = null
            }
        }

        // Save List Button - Now saves to backend
        btnSaveList.setOnClickListener {
            if (currentCartList.isEmpty()) {
                Toast.makeText(requireContext(), "Your list is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading message
            Toast.makeText(requireContext(), "Saving your list...", Toast.LENGTH_SHORT).show()

            // Run in background
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    // First, ensure we have a user (create or get existing)
                    ensureUserExists()

                    if (userId == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to create/get user", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Now show dialog to get list name (on main thread)
                    withContext(Dispatchers.Main) {
                        showSaveListDialog(llCartItemsContainer, tvEmptyHint)
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("SAVE_ERROR", "Exception: ${e.message}")
                    }
                }
            }
        }
    }

    // Function to ensure a user exists (create if not)
    private suspend fun ensureUserExists() {
        if (userId != null) return // Already have a user

        try {
            // Try to find existing user "user1" first
            val getUsersResponse = apiService.getUsers()

            if (getUsersResponse.success) {
                // Parse the response to find user with username "user1"
                val usersArray = JSONArray(getUsersResponse.body)
                for (i in 0 until usersArray.length()) {
                    val user = usersArray.getJSONObject(i)
                    if (user.getString("username") == "user1") {
                        userId = user.getString("_id")
                        Log.d("USER_FOUND", "Found existing user with ID: $userId")
                        return
                    }
                }
            }

            // If user not found, create new user
            Log.d("USER_CREATE", "Creating new user 'user1'")
            val createResponse = apiService.createUser("user1")

            if (createResponse.success) {
                val userObj = JSONObject(createResponse.body)
                userId = userObj.getString("_id")
                Log.d("USER_CREATED", "Created user with ID: $userId")
            } else {
                Log.e("USER_ERROR", "Failed to create user: ${createResponse.errorMessage}")
            }
        } catch (e: Exception) {
            Log.e("USER_ERROR", "Error in ensureUserExists: ${e.message}")
        }
    }

    // Function to save list to backend
    private suspend fun saveListToBackend(listName: String, items: List<CartItem>): Boolean {
        if (userId == null) return false

        try {
            // Create JSON for items
            val itemsArray = JSONArray()
            items.forEach { item ->
                val itemObj = JSONObject().apply {
                    put("name", "${item.name} at ${item.store}")
                    put("price", item.price)
                }
                itemsArray.put(itemObj)
            }

            // Create request body for shop list
            val requestBody = JSONObject().apply {
                put("topic", listName)
                put("items", itemsArray)
            }

            // Make API call to create shop list
            val response = apiService.createShopList(userId!!, requestBody)

            if (response.success) {
                Log.d("LIST_SAVED", "List saved successfully: ${response.body}")
                return true
            } else {
                Log.e("LIST_ERROR", "Failed to save list: ${response.errorMessage}")
                return false
            }
        } catch (e: Exception) {
            Log.e("LIST_ERROR", "Exception saving list: ${e.message}")
            return false
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
                    GlobalScope.launch(Dispatchers.IO) {
                        val success = saveListToBackend(listName, currentCartList)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                // Also save locally via ViewModel
                                saveListToLocal(listName, currentCartList)

                                Toast.makeText(requireContext(), "List '$listName' saved to cloud!", Toast.LENGTH_SHORT).show()
                                alertDialog.dismiss()

                                // Clear the current UI list after saving
                                currentCartList.clear()
                                llCartItemsContainer.removeAllViews()
                                tvEmptyHint.visibility = View.VISIBLE
                            } else {
                                Toast.makeText(requireContext(), "Failed to save to cloud. Please try again.", Toast.LENGTH_SHORT).show()
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

    // Highlight the selected store
    private fun highlightStore(selected: View, other1: View, other2: View) {
        selected.setBackgroundColor(Color.parseColor("#E0F7FA"))
        other1.setBackgroundColor(Color.TRANSPARENT)
        other2.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun resetStoreSelection(v1: View, v2: View, v3: View) {
        v1.setBackgroundColor(Color.TRANSPARENT)
        v2.setBackgroundColor(Color.TRANSPARENT)
        v3.setBackgroundColor(Color.TRANSPARENT)
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

        val newListEntry = SavedList(
            name = name,
            date = formattedDate,
            items = savedItems
        )

        viewModel.addList(newListEntry)
    }
}