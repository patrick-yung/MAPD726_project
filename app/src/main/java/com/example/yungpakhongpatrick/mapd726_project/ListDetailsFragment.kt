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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListDetailsFragment : BaseFragment(R.layout.fragment_list_details) {

    private lateinit var viewModel: ListViewModel
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private var currentUserId: String = ""
    private var currentListId: Long = 0L
    private var currentBackendListId: String? = null
    private var fetchJob: Job? = null
    private var saveJob: Job? = null
    private lateinit var checklistAdapter: ChecklistItemAdapter
    private var isUpdatingFromBackend = false
    private var isDataReady = false  // Flag to track if backend ID is ready

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

        sessionManager = SessionManager(requireContext())

        if (!sessionManager.isLoggedIn()) {
            Log.e(TAG, "User not logged in!")
            navigateToLogin()
            return
        }

        currentUserId = sessionManager.getUserId() ?: run {
            Log.e(TAG, "No user ID found!")
            navigateToLogin()
            return
        }

        currentListId = arguments?.getLong(ARG_LIST_ID) ?: run {
            Log.e(TAG, "ERROR: No list ID provided in arguments!")
            return
        }

        Log.d(TAG, "Current List ID (local): $currentListId")

        viewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]

        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)

        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvListDate)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvListTotalAmount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyItems)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvListItems)

        checklistAdapter = ChecklistItemAdapter(emptyList()) { itemIndex, isChecked ->
            if (isUpdatingFromBackend) {
                Log.d(TAG, "Skipping update - currently updating from backend")
                return@ChecklistItemAdapter
            }

            if (!isDataReady) {
                Log.d(TAG, "Data not ready yet, cannot save")
                Toast.makeText(requireContext(), "Please wait, loading list...", Toast.LENGTH_SHORT).show()
                return@ChecklistItemAdapter
            }

            Log.d(TAG, "Item checked: index=$itemIndex, isChecked=$isChecked")

            val currentList = viewModel.allShoppingLists.value?.firstOrNull { it.id == currentListId }

            if (currentList != null && itemIndex < currentList.items.size) {
                val item = currentList.items[itemIndex]

                // Update local state immediately
                viewModel.updateItemChecked(currentListId, itemIndex, isChecked)

                // Save to backend
                saveItemCheckedState(itemIndex, isChecked, item)
            }
        }

        rvItems.layoutManager = LinearLayoutManager(requireContext())
        rvItems.adapter = checklistAdapter

        // Observe ViewModel for updates
        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
            if (isUpdatingFromBackend) {
                Log.d(TAG, "Skipping observer update - currently updating from backend")
                return@observe
            }

            Log.d(TAG, "========== ViewModel Update ==========")

            val selectedList = allLists.firstOrNull { it.id == currentListId }

            if (selectedList != null) {
                Log.d(TAG, "✓ FOUND current list in ViewModel!")
                Log.d(TAG, "List name: ${selectedList.name}")
                Log.d(TAG, "Number of items: ${selectedList.items.size}")

                // Check if we have the backend ID
                if (selectedList.backendId != null && currentBackendListId == null) {
                    currentBackendListId = selectedList.backendId
                    isDataReady = true
                    Log.d(TAG, "Backend ID set from ViewModel: $currentBackendListId")
                }

                selectedList.items.forEachIndexed { idx, item ->
                    Log.d(TAG, "Item $idx: ${item.name}, isChecked=${item.isChecked}")
                }

                tvHeaderTitle.text = selectedList.name
                tvDate.text = selectedList.date
                tvTotalAmount.text = "$${String.format("%.2f", selectedList.totalPrice)}"
                checklistAdapter.submitItems(selectedList.items)
                tvEmpty.visibility = if (selectedList.items.isEmpty()) View.VISIBLE else View.GONE
            } else {
                Log.w(TAG, "✗ Current list ID $currentListId NOT found in ViewModel")
            }
            Log.d(TAG, "=======================================")
        }

        // Fetch from backend to get the backend ID
        fetchListFromBackend()
    }

    private fun fetchListFromBackend() {
        Log.d(TAG, "========== fetchListFromBackend() ==========")

        fetchJob?.cancel()

        fetchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Making API call to getShopLists() for user: $currentUserId")
                val response = apiService.getShopLists(currentUserId)

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        if (response.body.isNotEmpty() && response.body != "[]" && response.body != "null") {
                            Log.d(TAG, "Response received, parsing...")
                            parseAndUpdateLists(response.body)
                        } else {
                            Log.w(TAG, "⚠ Response body is empty")
                        }
                    } else {
                        Log.e(TAG, "❌ API call failed: ${response.statusCode}")
                        Toast.makeText(requireContext(), "Failed to fetch: ${response.statusCode}", Toast.LENGTH_SHORT).show()
                        if (response.statusCode == 401 || response.statusCode == 403) {
                            sessionManager.clearSession()
                            navigateToLogin()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in fetchListFromBackend", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseAndUpdateLists(jsonResponse: String) {
        try {
            isUpdatingFromBackend = true

            val jsonArray = JSONArray(jsonResponse)
            Log.d(TAG, "Parsing ${jsonArray.length()} lists from backend")

            for (i in 0 until jsonArray.length()) {
                val listObj = jsonArray.getJSONObject(i)

                val backendListId = listObj.getString("_id")
                val topic = listObj.getString("topic")
                val localId = backendListId.hashCode().toLong()

                Log.d(TAG, "Processing list: $topic (backend ID: $backendListId, local ID: $localId)")

                // If this is our current list, save the backend ID
                if (localId == currentListId) {
                    currentBackendListId = backendListId
                    isDataReady = true
                    Log.d(TAG, "✓ SET BACKEND ID: $backendListId")
                }

                val itemsArray = listObj.getJSONArray("items")
                val savedItems = mutableListOf<SavedItem>()

                Log.d(TAG, "  Found ${itemsArray.length()} items in this list")

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val itemName = itemObj.getString("name")
                    val itemPrice = itemObj.getDouble("price")

                    val isChecked = if (itemObj.has("isChecked")) {
                        itemObj.getBoolean("isChecked")
                    } else {
                        false
                    }

                    Log.d(TAG, "  Item $j: $itemName, price: $itemPrice, isChecked: $isChecked")

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
                            isChecked = isChecked
                        )
                    )
                }

                var formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                if (listObj.has("createdDate")) {
                    try {
                        val createdDateStr = listObj.getString("createdDate")
                        formattedDate = formatDateFromBackend(createdDateStr)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing date", e)
                    }
                }

                val savedList = SavedList(
                    id = localId,
                    backendId = backendListId,
                    name = topic,
                    date = formattedDate,
                    items = savedItems
                )

                // Check if list exists in ViewModel
                val existingLists = viewModel.allShoppingLists.value ?: emptyList()
                val existingList = existingLists.firstOrNull { it.id == localId }

                if (existingList == null) {
                    Log.d(TAG, "Adding new list to ViewModel: $topic")
                    viewModel.addList(savedList)
                } else {
                    Log.d(TAG, "Updating existing list in ViewModel: $topic")
                    viewModel.updateList(savedList)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing JSON", e)
        } finally {
            isUpdatingFromBackend = false
        }
    }

    private fun saveItemCheckedState(itemIndex: Int, isChecked: Boolean, item: SavedItem) {
        Log.d(TAG, "========== saveItemCheckedState() ==========")
        Log.d(TAG, "Item: ${item.name}, Index: $itemIndex, IsChecked: $isChecked")
        Log.d(TAG, "Current Backend List ID: $currentBackendListId")
        Log.d(TAG, "Is Data Ready: $isDataReady")

        if (!isDataReady || currentBackendListId == null) {
            Log.e(TAG, "Backend list ID not known yet! Waiting...")
            // Wait a bit and retry
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                if (currentBackendListId != null) {
                    Log.d(TAG, "Retrying save...")
                    saveItemCheckedState(itemIndex, isChecked, item)
                } else {
                    Log.e(TAG, "Still no backend ID, giving up")
                    Toast.makeText(requireContext(), "Cannot save: List not ready", Toast.LENGTH_SHORT).show()
                    // Revert the checkbox
                    viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                }
            }
            return
        }

        saveJob?.cancel()

        saveJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the current list from backend
                val getResponse = apiService.getShopLists(currentUserId)

                if (!getResponse.success) {
                    Log.e(TAG, "Failed to get current list")
                    withContext(Dispatchers.Main) {
                        viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                    }
                    return@launch
                }

                val jsonArray = JSONArray(getResponse.body)
                var found = false

                for (i in 0 until jsonArray.length()) {
                    val listObj = jsonArray.getJSONObject(i)
                    if (listObj.getString("_id") == currentBackendListId) {
                        Log.d(TAG, "Found list in backend response")
                        val itemsArray = listObj.getJSONArray("items")

                        // Create a new items array with updated checked state
                        val updatedItemsArray = JSONArray()
                        for (j in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(j)
                            if (j == itemIndex) {
                                itemObj.put("isChecked", isChecked)
                                Log.d(TAG, "Updated item $j to isChecked=$isChecked")
                            }
                            updatedItemsArray.put(itemObj)
                        }

                        val updatePayload = JSONObject().apply {
                            put("topic", listObj.getString("topic"))
                            put("items", updatedItemsArray)
                        }

                        Log.d(TAG, "Sending update to backend...")
                        val updateResponse = apiService.updateShopList(
                            currentUserId,
                            currentBackendListId!!,
                            updatePayload
                        )

                        withContext(Dispatchers.Main) {
                            if (updateResponse.success) {
                                Log.d(TAG, "✅ Successfully saved checked state to backend")
                            } else {
                                Log.e(TAG, "❌ Failed to save to backend: ${updateResponse.statusCode}")
                                viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                            }
                        }
                        found = true
                        break
                    }
                }

                if (!found) {
                    Log.e(TAG, "Could not find list with ID: $currentBackendListId")
                    withContext(Dispatchers.Main) {
                        viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception saving to backend", e)
                withContext(Dispatchers.Main) {
                    viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                }
            }
        }
    }

    private fun formatDateFromBackend(dateString: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = isoFormat.parse(dateString)
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        }
    }

    private fun navigateToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LogInFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchJob?.cancel()
        saveJob?.cancel()
    }
}