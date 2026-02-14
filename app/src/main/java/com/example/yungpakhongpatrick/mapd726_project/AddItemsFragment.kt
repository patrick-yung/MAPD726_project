package com.example.yungpakhongpatrick.mapd726_project

import android.graphics.Color
import android.os.Bundle
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

class AddItemsFragment : BaseFragment(R.layout.fragment_add_items) {

    // --- 1. DEFINE VARIABLES AT CLASS LEVEL (NOT INSIDE FUNCTIONS) ---

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

    // --- 2. MAIN LOGIC FUNCTION ---
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find Views
        val etProductName = view.findViewById<AutoCompleteTextView>(R.id.etProductName)
        val tvPriceWalmart = view.findViewById<TextView>(R.id.tvPriceWalmart)
        val tvPriceCostco = view.findViewById<TextView>(R.id.tvPriceCostco)
        val tvPriceSuperstore = view.findViewById<TextView>(R.id.tvPriceSuperstore)

        // Store Layout Containers (The colored/clickable areas)
        // NOTE: Ensure your XML IDs match these exactly
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
            android.R.layout.simple_dropdown_item_1line, // Use this standard Android layout
            productNames                                // This is your list of names
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

        // B. Handle Store Clicks (Walmart)
        containerWalmart.setOnClickListener {
            highlightStore(containerWalmart, containerCostco, containerSuperstore)
            selectedStoreName = "Walmart"
            val priceText = tvPriceWalmart.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // C. Handle Store Clicks (Costco)
        containerCostco.setOnClickListener {
            highlightStore(containerCostco, containerWalmart, containerSuperstore)
            selectedStoreName = "Costco"
            val priceText = tvPriceCostco.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // D. Handle Store Clicks (Superstore)
        containerSuperstore.setOnClickListener {
            highlightStore(containerSuperstore, containerWalmart, containerCostco)
            selectedStoreName = "Superstore"
            val priceText = tvPriceSuperstore.text.toString().replace("$", "")
            if(priceText != "--") selectedPrice = priceText.toDouble()
        }

        // E. Add Item Button Logic
        view.findViewById<Button>(R.id.btnAddItem).setOnClickListener {
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
                itemView.findViewById<TextView>(R.id.item_name).text = itemText
                itemView.findViewById<TextView>(R.id.item_details).visibility =
                    View.GONE // Hide detail line as it's now in title

                val tvName = itemView.findViewById<TextView>(R.id.item_name)
                tvName.text = itemText

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

        // Inside onViewCreated...

        btnSaveList.setOnClickListener {
            if (currentCartList.isEmpty()) {
                Toast.makeText(requireContext(), "Your list is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Inflate the custom dialog layout
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_list, null)
            val etListName = dialogView.findViewById<EditText>(R.id.etListName)

            // 2. Create the AlertDialog
            val alertDialog = android.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton("Save", null) // Set to null first to handle validation
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .create()

            // 3. Handle the "Save" click with validation
            alertDialog.setOnShowListener {
                val saveButton = alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                saveButton.setOnClickListener {
                    val listName = etListName.text.toString().trim()

                    if (listName.isEmpty()) {
                        etListName.error = "Please enter a name"
                    } else {
                        // SUCCESS: Save the list to your backend/database
                        saveListToBackend(listName, currentCartList)

                        Toast.makeText(requireContext(), "List '$listName' saved!", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss()

                        // Optional: Clear the current UI list after saving
                        currentCartList.clear()
                        llCartItemsContainer.removeAllViews()
                        tvEmptyHint.visibility = View.VISIBLE
                    }
                }
            }

            alertDialog.show()
        }
    }

    // --- 3. HELPER FUNCTIONS (OUTSIDE OF ONVIEWCREATED) ---

    // Highlight the selected store (e.g. add a border or change background color)
    private fun highlightStore(selected: View, other1: View, other2: View) {
        // Change background to indicate selection (Light Cyan)
        selected.setBackgroundColor(Color.parseColor("#E0F7FA"))

        // Reset others to transparent or default white
        other1.setBackgroundColor(Color.TRANSPARENT)
        other2.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun resetStoreSelection(v1: View, v2: View, v3: View) {
        v1.setBackgroundColor(Color.TRANSPARENT)
        v2.setBackgroundColor(Color.TRANSPARENT)
        v3.setBackgroundColor(Color.TRANSPARENT)
    }
    private fun saveListToBackend(name: String, items: List<CartItem>) {
        // TODO: Connect to Firebase or Room Database here
        // Example object to save:
        // val listToSave = mapOf("name" to name, "items" to items, "date" to System.currentTimeMillis())

        println("Saving list $name with ${items.size} items to database...")
    }
}