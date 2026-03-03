package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yungpakhongpatrick.mapd726_project.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListFragment : BaseFragment(R.layout.fragment_list) {
    private lateinit var listViewModel: ListViewModel
    private lateinit var apiService: ApiService
    private val FIXED_USER_ID = "69a73ea3f2f9ecd01c7bcbf2" // Your user ID
    private val TAG = "ListFragment"

    // Views
    private lateinit var rvShoppingList: RecyclerView
    private lateinit var tvTotalAmount: TextView
    private lateinit var btnBackArrow: ImageView
    private lateinit var tvListTitle: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "=== ListFragment onViewCreated ===")

        // Initialize views
        initViews(view)

        // Initialize ViewModel
        listViewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]

        // Initialize ApiService
        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)
        Log.d(TAG, "ApiService initialized with baseUrl: $baseUrl")
        Log.d(TAG, "Fixed User ID: $FIXED_USER_ID")

        // Set up back button
        btnBackArrow.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Set up layout manager
        rvShoppingList.layoutManager = LinearLayoutManager(requireContext())

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

    private fun initViews(view: View) {
        rvShoppingList = view.findViewById(R.id.rvShoppingList)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        btnBackArrow = view.findViewById(R.id.btnBackArrow)
        tvListTitle = view.findViewById(R.id.tvListTitle)

        // Set the title
        tvListTitle.text = "My Shopping Lists"
    }

    private fun fetchListsFromBackend() {
        Log.d(TAG, "=== fetchListsFromBackend() called ===")

        CoroutineScope(Dispatchers.IO).launch {
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
                        Toast.makeText(requireContext(), "Failed to load from cloud: ${response.statusCode}", Toast.LENGTH_SHORT).show()
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

                    // Get checked state if it exists
                    val isChecked = if (itemObj.has("isChecked")) {
                        itemObj.getBoolean("isChecked")
                    } else {
                        false
                    }

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
                            isChecked = isChecked
                        )
                    )
                }

                // Get date from backend if available
                val formattedDate = if (listObj.has("createdDate")) {
                    try {
                        val dateStr = listObj.getString("createdDate")
                        formatDateFromBackend(dateStr)
                    } catch (e: Exception) {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                    }
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                }

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
                    // Optionally update if needed
                    val existingList = existingLists.first { it.id == localId }
                    if (existingList.items.size != savedItems.size) {
                        Log.d(TAG, "Item count differs, updating list")
                        listViewModel.updateList(savedList)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lists", e)
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
            try {
                val altFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                altFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = altFormat.parse(dateString)
                val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                outputFormat.format(date ?: Date())
            } catch (e2: Exception) {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up if needed
    }
}