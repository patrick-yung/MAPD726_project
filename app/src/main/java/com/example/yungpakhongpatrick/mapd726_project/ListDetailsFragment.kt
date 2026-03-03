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

        // Initialize SessionManager and get user ID
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

        Log.d(TAG, "Current User ID: $currentUserId")

        // Get the list ID from arguments
        currentListId = arguments?.getLong(ARG_LIST_ID) ?: run {
            Log.e(TAG, "ERROR: No list ID provided in arguments!")
            return
        }

        Log.d(TAG, "Current List ID (local): $currentListId")

        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]
        Log.d(TAG, "ViewModel initialized")

        // Initialize ApiService
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)
        Log.d(TAG, "ApiService initialized with baseUrl: $baseUrl")

        // Find views
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvDate = view.findViewById<TextView>(R.id.tvListDate)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvListTotalAmount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyItems)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvListItems)

        Log.d(TAG, "Views found")

        // Setup adapter with save functionality
        checklistAdapter = ChecklistItemAdapter(emptyList()) { itemIndex, isChecked ->
            Log.d(TAG, "Item checked: index=$itemIndex, isChecked=$isChecked")

            // Update in ViewModel first (immediate UI feedback)
            viewModel.updateItemChecked(currentListId, itemIndex, isChecked)

            // Then save to backend
            saveItemCheckedState(itemIndex, isChecked)
        }

        rvItems.layoutManager = LinearLayoutManager(requireContext())
        rvItems.adapter = checklistAdapter
        Log.d(TAG, "RecyclerView setup complete")

        // Observe ViewModel for updates
        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
            Log.d(TAG, "========== ViewModel Update ==========")

            val selectedList = allLists.firstOrNull { it.id == currentListId }

            if (selectedList != null) {
                Log.d(TAG, "✓ FOUND current list in ViewModel!")

                // Update UI
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

        // Fetch from backend
        Log.d(TAG, "Initiating backend fetch...")
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
                            Log.d(TAG, "Response has content, parsing...")
                            parseAndUpdateLists(response.body)
                        } else {
                            Log.w(TAG, "⚠ Response body is empty")
                        }
                    } else {
                        Log.e(TAG, "❌ API call failed: ${response.statusCode}")
                        Toast.makeText(requireContext(), "Failed to fetch from cloud: ${response.statusCode}", Toast.LENGTH_SHORT).show()

                        // Handle unauthorized
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
            val jsonArray = JSONArray(jsonResponse)

            for (i in 0 until jsonArray.length()) {
                val listObj = jsonArray.getJSONObject(i)

                val backendListId = listObj.getString("_id")
                val topic = listObj.getString("topic")
                val localId = backendListId.hashCode().toLong()

                // Store the backend ID for our current list
                if (localId == currentListId) {
                    currentBackendListId = backendListId
                    Log.d(TAG, "Found current list! Backend ID: $backendListId")
                }

                // Parse items with checked state
                val itemsArray = listObj.getJSONArray("items")
                val savedItems = mutableListOf<SavedItem>()

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val itemName = itemObj.getString("name")
                    val itemPrice = itemObj.getDouble("price")

                    val isChecked = if (itemObj.has("isChecked")) {
                        itemObj.getBoolean("isChecked")
                    } else {
                        false
                    }

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

                // Parse the created date from backend
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
                    Log.d(TAG, "List already in ViewMmodels, skipping")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing JSON", e)
        }
    }

    private fun saveItemCheckedState(itemIndex: Int, isChecked: Boolean) {
        Log.d(TAG, "========== saveItemCheckedState() ==========")

        if (currentBackendListId == null) {
            Log.e(TAG, "Backend list ID not known yet!")
            return
        }

        val currentList = viewModel.allShoppingLists.value?.firstOrNull { it.id == currentListId } ?: return

        saveJob?.cancel()

        saveJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the current list from backend
                val getResponse = apiService.getShopLists(currentUserId)

                if (getResponse.success) {
                    val jsonArray = JSONArray(getResponse.body)

                    for (i in 0 until jsonArray.length()) {
                        val listObj = jsonArray.getJSONObject(i)
                        if (listObj.getString("_id") == currentBackendListId) {
                            val itemsArray = listObj.getJSONArray("items")

                            if (itemIndex < itemsArray.length()) {
                                val itemObj = itemsArray.getJSONObject(itemIndex)
                                itemObj.put("isChecked", isChecked)

                                val updatePayload = JSONObject().apply {
                                    put("topic", listObj.getString("topic"))
                                    put("items", itemsArray)
                                }

                                val updateResponse = apiService.updateShopList(
                                    currentUserId,
                                    currentBackendListId!!,
                                    updatePayload
                                )

                                withContext(Dispatchers.Main) {
                                    if (updateResponse.success) {
                                        Log.d(TAG, "✅ Successfully saved to backend")
                                    } else {
                                        Log.e(TAG, "❌ Failed to save to backend")
                                        Toast.makeText(requireContext(), "Failed to save to cloud", Toast.LENGTH_SHORT).show()
                                        viewModel.updateItemChecked(currentListId, itemIndex, !isChecked)
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception saving to backend", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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