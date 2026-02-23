package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.example.yungpakhongpatrick.mapd726_project.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class ListFragment : BaseFragment(R.layout.fragment_list) {
    private lateinit var listViewModel: ListViewModel
    private lateinit var apiService: ApiService
    private val FIXED_USER_ID = "699ca617078ad1971ca67c11" // Your user ID
    private val TAG = "ListFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "=== ListFragment onViewCreated ===")

        // Initialize ViewModel
        listViewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        // Initialize ApiService
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)
        Log.d(TAG, "ApiService initialized with baseUrl: $baseUrl")
        Log.d(TAG, "Fixed User ID: $FIXED_USER_ID")

        val rvShoppingList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvShoppingList)
        val tvTotalAmount = view.findViewById<android.widget.TextView>(R.id.tvTotalAmount)

        Log.d(TAG, "Views found: rvShoppingList=${rvShoppingList != null}, tvTotalAmount=${tvTotalAmount != null}")

        // Set up layout manager (this stays the same)
        rvShoppingList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        // Observe ViewModel for updates
        listViewModel.allShoppingLists.observe(viewLifecycleOwner) { updatedLists ->
            Log.d(TAG, "ViewModel observer triggered with ${updatedLists.size} lists")

            // Update total amount
            val grandTotal = updatedLists.sumOf { it.totalPrice }
            tvTotalAmount.text = "$${String.format("%.2f", grandTotal)}"

            // Log all lists
            updatedLists.forEachIndexed { index, list ->
                Log.d(TAG, "List $index: id=${list.id}, name=${list.name}, items=${list.items.size}")
            }

            // Create new adapter with updated lists
            rvShoppingList.adapter = ListAdapter(updatedLists) { selectedList ->
                Log.d(TAG, "List clicked: id=${selectedList.id}, name=${selectedList.name}")
                openListDetails(selectedList)
            }

            Log.d(TAG, "New adapter set with ${updatedLists.size} lists")
        }

        // Fetch lists from backend
        fetchListsFromBackend()
    }

    private fun fetchListsFromBackend() {
        Log.d(TAG, "=== fetchListsFromBackend() called ===")

        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API call to getShopLists()")
                val response = apiService.getShopLists(FIXED_USER_ID)

                Log.d(TAG, "API Response - Success: ${response.success}, Code: ${response.statusCode}")
                Log.d(TAG, "Response body: ${response.body}")

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        if (response.body.isNotEmpty() && response.body != "[]" && response.body != "null") {
                            Log.d(TAG, "Parsing backend lists...")
                            parseAndAddLists(response.body)
                            Toast.makeText(requireContext(), "Lists loaded from cloud", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d(TAG, "No lists found in backend")
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch lists: ${response.errorMessage}")
                        Toast.makeText(requireContext(), "Failed to load from cloud", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching lists", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseAndAddLists(jsonResponse: String) {
        try {
            val jsonArray = JSONArray(jsonResponse)
            Log.d(TAG, "Found ${jsonArray.length()} lists in backend")

            for (i in 0 until jsonArray.length()) {
                val listObj = jsonArray.getJSONObject(i)

                val backendListId = listObj.getString("_id")
                val topic = listObj.getString("topic")
                val localId = backendListId.hashCode().toLong()

                Log.d(TAG, "Processing list $i: $topic (backend: $backendListId, local: $localId)")

                // Parse items
                val itemsArray = listObj.getJSONArray("items")
                val savedItems = mutableListOf<SavedItem>()

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val itemName = itemObj.getString("name")
                    val itemPrice = itemObj.getDouble("price")

                    // Parse store from item name
                    val store = if (itemName.contains(" at ")) {
                        itemName.substringAfter(" at ")
                    } else {
                        "Unknown"
                    }

                    val name = if (itemName.contains(" at ")) {
                        itemName.substringBefore(" at ")
                    } else {
                        itemName
                    }

                    savedItems.add(
                        SavedItem(
                            name = name,
                            price = itemPrice,
                            store = store,
                            isChecked = false
                        )
                    )
                }

                // Format date
                val formattedDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val savedList = SavedList(
                    id = localId,
                    name = topic,
                    date = formattedDate,
                    items = savedItems
                )

                // Check if list already exists in ViewModel
                val existingLists = listViewModel.allShoppingLists.value ?: emptyList()
                val exists = existingLists.any { it.id == localId }

                if (!exists) {
                    Log.d(TAG, "Adding new list to ViewModel: $topic")
                    listViewModel.addList(savedList)
                } else {
                    Log.d(TAG, "List already exists in ViewModel: $topic")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lists", e)
        }
    }

    private fun openListDetails(selectedList: SavedList) {
        Log.d(TAG, "openListDetails called with list: id=${selectedList.id}, name=${selectedList.name}")

        try {
            val fragment = ListDetailsFragment.newInstance(selectedList.id)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack("ListDetails")
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening list details", e)
        }
    }
}