package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class ListDetailsFragment : BaseFragment(R.layout.fragment_list_details) {

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private val FIXED_USER_ID = "699ca617078ad1971ca67c11" // Your user ID
    private var currentListId: Long = 0L
    private var fetchJob: Job? = null

    companion object {
        private const val TAG = "ListDetailsFragment"
        private const val ARG_LIST_ID = "arg_list_id"

        fun newInstance(listId: Long): ListDetailsFragment {
            return ListDetailsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_LIST_ID, listId)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "=================================")
        Log.d(TAG, "onViewCreated - Fragment Started")
        Log.d(TAG, "=================================")

        // Get the list ID from arguments
        currentListId = arguments?.getLong(ARG_LIST_ID) ?: run {
            Log.e(TAG, "ERROR: No list ID provided in arguments!")
            return
        }

        Log.d(TAG, "Current List ID (local): $currentListId")
        Log.d(TAG, "Converting backend ID to local ID: If backend ID was 'xyz', local ID would be: ${"xyz".hashCode().toLong()}")

        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]
        Log.d(TAG, "ViewModel initialized")

        // Initialize ApiService
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)
        Log.d(TAG, "ApiService initialized with baseUrl: $baseUrl")
        Log.d(TAG, "Fixed User ID: $FIXED_USER_ID")

        // Find views
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvListDate)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvListTotalAmount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyItems)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvListItems)

        Log.d(TAG, "Views found: tvHeaderTitle=${tvHeaderTitle != null}, tvDate=${tvDate != null}, tvTotalAmount=${tvTotalAmount != null}, tvEmpty=${tvEmpty != null}, rvItems=${rvItems != null}")

        // Setup adapter
        val checklistAdapter = ChecklistItemAdapter(emptyList()) { itemIndex, isChecked ->
            Log.d(TAG, "Item checked: index=$itemIndex, isChecked=$isChecked")
            viewModel.updateItemChecked(currentListId, itemIndex, isChecked)
        }

        rvItems.layoutManager = LinearLayoutManager(requireContext())
        rvItems.adapter = checklistAdapter
        Log.d(TAG, "RecyclerView setup complete")

        // Observe ViewModel for updates
        Log.d(TAG, "Setting up ViewModel observer...")
        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
            Log.d(TAG, "========== ViewModel Update ==========")
            Log.d(TAG, "ViewModel observer triggered")
            Log.d(TAG, "Total lists in ViewModel: ${allLists.size}")

            if (allLists.isEmpty()) {
                Log.d(TAG, "ViewModel has no lists yet")
            } else {
                Log.d(TAG, "Lists in ViewModel:")
                allLists.forEachIndexed { index, list ->
                    Log.d(TAG, "  [$index] ID: ${list.id}, Name: ${list.name}, Items: ${list.items.size}")
                }
            }

            val selectedList = allLists.firstOrNull { it.id == currentListId }

            if (selectedList != null) {
                Log.d(TAG, "✓ FOUND current list in ViewModel!")
                Log.d(TAG, "  List name: ${selectedList.name}")
                Log.d(TAG, "  List date: ${selectedList.date}")
                Log.d(TAG, "  Total price: ${selectedList.totalPrice}")
                Log.d(TAG, "  Number of items: ${selectedList.items.size}")

                // Log items
                selectedList.items.forEachIndexed { index, item ->
                    Log.d(TAG, "    Item $index: ${item.name} at ${item.store} - $${item.price} (checked: ${item.isChecked})")
                }

                // Update UI
                tvHeaderTitle.text = selectedList.name
                tvDate.text = selectedList.date
                tvTotalAmount.text = "$${String.format("%.2f", selectedList.totalPrice)}"
                checklistAdapter.submitItems(selectedList.items)
                tvEmpty.visibility = if (selectedList.items.isEmpty()) View.VISIBLE else View.GONE

                Log.d(TAG, "✓ UI updated successfully")
            } else {
                Log.w(TAG, "✗ Current list ID $currentListId NOT found in ViewModel")
                Log.d(TAG, "Available IDs: ${allLists.map { it.id }}")
            }
            Log.d(TAG, "=======================================")
        }

        // Fetch from backend
        Log.d(TAG, "Initiating backend fetch...")
        fetchListFromBackend()
    }

    private fun fetchListFromBackend() {
        Log.d(TAG, "========== fetchListFromBackend() ==========")

        // Cancel any existing fetch job
        fetchJob?.cancel()
        Log.d(TAG, "Previous fetch job cancelled (if any)")

        fetchJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API call to getShopLists() on background thread")
                Log.d(TAG, "  User ID: $FIXED_USER_ID")
                Log.d(TAG, "  Time: ${System.currentTimeMillis()}")

                // Get all shop lists
                val response = apiService.getShopLists(FIXED_USER_ID)

                Log.d(TAG, "API Response received:")
                Log.d(TAG, "  Success: ${response.success}")
                Log.d(TAG, "  Status Code: ${response.statusCode}")
                Log.d(TAG, "  Body length: ${response.body.length}")
                Log.d(TAG, "  Body preview: ${response.body.take(200)}")
                Log.d(TAG, "  Error message: ${response.errorMessage}")

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        Log.d(TAG, "✅ API call successful")

                        if (response.body.isNotEmpty() && response.body != "[]" && response.body != "null") {
                            Log.d(TAG, "Response has content, parsing...")
                            parseAndUpdateLists(response.body)
                        } else {
                            Log.w(TAG, "⚠ Response body is empty: '${response.body}'")
                            Toast.makeText(requireContext(), "No lists found in cloud", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "❌ API call failed")
                        Log.e(TAG, "  Status code: ${response.statusCode}")
                        Log.e(TAG, "  Error: ${response.errorMessage}")
                        Toast.makeText(requireContext(), "Failed to fetch from cloud: ${response.statusCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in fetchListFromBackend", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Log.d(TAG, "fetchListFromBackend() completed, job started")
        Log.d(TAG, "===========================================")
    }

    private fun parseAndUpdateLists(jsonResponse: String) {
        Log.d(TAG, "========== parseAndUpdateLists() ==========")
        Log.d(TAG, "Parsing JSON response of length: ${jsonResponse.length}")

        try {
            val jsonArray = JSONArray(jsonResponse)
            Log.d(TAG, "Successfully parsed JSON array")
            Log.d(TAG, "Number of lists in response: ${jsonArray.length()}")

            // Log all lists from backend
            for (i in 0 until jsonArray.length()) {
                val listObj = jsonArray.getJSONObject(i)
                val backendId = listObj.getString("_id")
                val topic = listObj.getString("topic")
                val itemsCount = listObj.getJSONArray("items").length()
                val localId = backendId.hashCode().toLong()

                Log.d(TAG, "Backend List $i:")
                Log.d(TAG, "  Backend ID: $backendId")
                Log.d(TAG, "  Local ID (hashCode): $localId")
                Log.d(TAG, "  Topic: $topic")
                Log.d(TAG, "  Items count: $itemsCount")

                // Check if this matches our current list
                if (localId == currentListId) {
                    Log.d(TAG, "  ✓ THIS IS OUR CURRENT LIST! (MATCH FOUND)")
                }
            }

            // Process each list
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

                // Check if list exists in ViewModel
                val existingLists = viewModel.allShoppingLists.value ?: emptyList()
                val existingList = existingLists.firstOrNull { it.id == localId }

                if (existingList == null) {
                    Log.d(TAG, "  ➕ List not in ViewModel, adding...")
                    viewModel.addList(savedList)
                    Log.d(TAG, "  ✓ Added to ViewModel: $topic")

                    if (localId == currentListId) {
                        Log.d(TAG, "  ⭐ This was our current list!")
                    }
                } else {
                    Log.d(TAG, "  ✓ List already in ViewModel, skipping")
                    Log.d(TAG, "    Existing items: ${existingList.items.size}, New items: ${savedItems.size}")
                }
            }

            Log.d(TAG, "=============================================")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing JSON", e)
            Log.e(TAG, "JSON that caused error: $jsonResponse")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: cancelling fetch job")
        fetchJob?.cancel()
    }
}