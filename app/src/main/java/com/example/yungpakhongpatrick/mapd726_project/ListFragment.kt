package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListFragment : BaseFragment(R.layout.fragment_list) {
    private lateinit var listViewModel: ListViewModel
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private var currentUserId: String = ""
    private val TAG = "ListFragment"

    // Sort state: default is Newest first
    private var isNewestFirst = true

    // Views
    private lateinit var rvShoppingList: RecyclerView
    private lateinit var btnBackArrow: ImageView
    private lateinit var btnSort: ImageView // New Sort Button
    private lateinit var tvListTitle: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        currentUserId = sessionManager.getUserId() ?: ""

        // Initialize Views
        rvShoppingList = view.findViewById(R.id.rvShoppingList)
        btnBackArrow = view.findViewById(R.id.btnBackArrow)
        tvListTitle = view.findViewById(R.id.tvListTitle)
        btnSort = view.findViewById(R.id.btnSort)

        // Setup ViewModel & API
        listViewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]
        apiService = ApiService(getString(R.string.base_url))

        // Setup Buttons
        btnBackArrow.setOnClickListener { parentFragmentManager.popBackStack() }

        // Setup Sort Button Click
        btnSort.setOnClickListener { view ->
            showSortPopup(view)
        }

        rvShoppingList.layoutManager = LinearLayoutManager(requireContext())

        // Observer
        listViewModel.allShoppingLists.observe(viewLifecycleOwner) { updatedLists ->
            sortAndDisplayLists(updatedLists)
        }
        fetchListsFromBackend()
    }

    // --- SORTING LOGIC ---
    private fun showSortPopup(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add("Newest First")
        popup.menu.add("Oldest First")
        popup.menu.add("Month Wise")

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Newest First" -> {
                    isNewestFirst = true
                    listViewModel.allShoppingLists.value?.let { sortAndDisplayLists(it) }
                }
                "Oldest First" -> {
                    isNewestFirst = false
                    listViewModel.allShoppingLists.value?.let { sortAndDisplayLists(it) }
                }
                "Month Wise" -> {
                    // When clicked, show the months to choose from
                    listViewModel.allShoppingLists.value?.let { showMonthSelectionDialog(it) }
                }
            }
            true
        }
        popup.show()
    }

    private fun showMonthSelectionDialog(allLists: List<SavedList>) {
        val options = mutableListOf("All Months")
        allLists.forEach { list ->
            val monthName = list.date.split(" ").firstOrNull()
            if (monthName != null && !options.contains(monthName)) {
                options.add(monthName)
            }
        }


        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Month")
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which]
                if (selected == "All Months") {
                    sortAndDisplayLists(allLists)
                } else {
                    val filtered = allLists.filter { it.date.contains(selected) }
                    sortAndDisplayLists(filtered)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sortAndDisplayLists(lists: List<SavedList>) {
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        val sortedList = if (isNewestFirst) {
            lists.sortedByDescending { list ->
                try {
                    dateFormat.parse(list.date)
                } catch (e: Exception) {
                    Date(0)
                }
            }
        } else {
            lists.sortedBy { list ->
                try {
                    dateFormat.parse(list.date)
                } catch (e: Exception) {
                    Date(0)
                }
            }
        }
        rvShoppingList.adapter = ListAdapter(
            shoppingLists = sortedList,
            onDeleteClick = { selectedList ->
                showDeleteConfirmation(selectedList)
            },
            onListClicked = { selectedList ->
                openListDetails(selectedList)
            }
        )
    }
    // ---------------------

    private fun fetchListsFromBackend() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getShopLists(currentUserId)
                withContext(Dispatchers.Main) {
                    if (response.success && response.body.isNotEmpty() && response.body != "[]") {
                        parseAndAddLists(response.body)
                    } else if (!response.success && (response.statusCode == 401 || response.statusCode == 403)) {
                        sessionManager.clearSession()
                        navigateToLogin()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching lists", e)
            }
        }
    }

    private fun parseAndAddLists(jsonResponse: String) {
        try {
            val jsonArray = JSONArray(jsonResponse)
            for (i in 0 until jsonArray.length()) {
                val listObj = jsonArray.getJSONObject(i)
                val backendListId = listObj.getString("_id")
                val topic = listObj.getString("topic")
                val localId = backendListId.hashCode().toLong()

                val itemsArray = listObj.getJSONArray("items")
                val savedItems = mutableListOf<SavedItem>()

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val itemName = itemObj.getString("name")
                    val itemPrice = itemObj.getDouble("price")
                    val isChecked =
                        if (itemObj.has("isChecked")) itemObj.getBoolean("isChecked") else false

                    // Simple logic to extract store name if " at " exists
                    val store =
                        if (itemName.contains(" at ")) itemName.substringAfter(" at ") else "Unknown"
                    val name =
                        if (itemName.contains(" at ")) itemName.substringBefore(" at ") else itemName

                    savedItems.add(SavedItem(name, itemPrice, store, isChecked))
                }

                // Handle Date
                val formattedDate = if (listObj.has("createdDate")) {
                    formatDateFromBackend(listObj.getString("createdDate"))
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                }

                val savedList = SavedList(localId, backendListId, topic, formattedDate, savedItems)

                // Update ViewModel
                val existingLists = listViewModel.allShoppingLists.value ?: emptyList()
                if (!existingLists.any { it.id == localId }) {
                    listViewModel.addList(savedList)
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
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date ?: Date())
        } catch (e: Exception) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        }
    }

    private fun openListDetails(selectedList: SavedList) {
        val fragment = ListDetailsFragment.newInstance(selectedList.id)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("ListDetails")
            .commit()
    }

    private fun navigateToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LogInFragment())
            .commit()
    }

    private fun showDeleteConfirmation(list: SavedList) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete List")
            .setMessage("Are you sure you want to delete '${list.name}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                performDelete(list)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(list: SavedList) {
        Toast.makeText(requireContext(), "Deleting...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.deleteShopList(currentUserId, list.backendId!!)

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        Toast.makeText(
                            requireContext(),
                            "List deleted permanently",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Remove from screen instantly
                        listViewModel.removeList(list.id)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed to delete from server",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting list", e)
            }
        }
    }

    private fun setupMonthFilter(allLists: List<SavedList>, spinner: Spinner) {
        // Automatically extract ONLY the months you actually have lists for
        val options = mutableListOf("All Months")

        allLists.forEach { list ->
            // This takes "Mar 4, 2026" and pulls out just "Mar"
            val monthName = list.date.split(" ").firstOrNull()
            if (monthName != null && !options.contains(monthName)) {
                options.add(monthName)
            }
        }

        // Setup the Spinner Adapter
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Handle the filtering
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = options[position]

                if (selected == "All Months") {
                    sortAndDisplayLists(allLists) // Show everything
                } else {
                    // Only show lists that match the selected month
                    val filtered = allLists.filter { it.date.contains(selected) }
                    sortAndDisplayLists(filtered)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
