package com.example.yungpakhongpatrick.mapd726_project

import com.example.yungpakhongpatrick.mapd726_project.ComparisonFragment
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeFragment : Fragment() {
    private lateinit var listViewModel: ListViewModel
    private val TAG = "HomeFragment"

    // 1. Load the XML Layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    // 2. Set up the Logic (Button Clicks)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        val sessionManager = SessionManager(requireContext())
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        val userName = sessionManager.getUserName() ?: "Iffat"

        tvWelcome.text = "Welcome back, $userName"

        val etSearchHome = view.findViewById<EditText>(R.id.etSearchHome)

        etSearchHome.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearchHome.text.toString().trim()
                if (query.isNotEmpty()) {
                    val fragment = ComparisonFragment().apply {
                        arguments = Bundle().apply {
                            putString("PRODUCT_QUERY", query)
                        }
                    }
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                true
            } else {
                false
            }
        }

        // 1. Find the container created in XML
        val llRecentListsContainer = view.findViewById<LinearLayout>(R.id.llRecentListsContainer)

        // 2. Initialize ListViewModel
        val viewModel = androidx.lifecycle.ViewModelProvider(requireActivity()).get(ListViewModel::class.java)

        // 3. OBSERVE the LiveData
        viewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
            llRecentListsContainer.removeAllViews()

            if (allLists.isNullOrEmpty()) {
                val tvLoading = TextView(requireContext()).apply {
                    text = "Loading recent lists..."
                    textSize = 16f
                    setTextColor(android.graphics.Color.parseColor("#888888"))
                    setPadding(24, 24, 24, 24)
                }
                llRecentListsContainer.addView(tvLoading)
                return@observe
            }

            // Grab the last 3 lists added, and reverse them so the absolute newest is at the very top!
            val recentLists = allLists.takeLast(3).reversed()


            for (savedList in recentLists) {
                val cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_home_list_card, llRecentListsContainer, false)
                val tvListName = cardView.findViewById<TextView>(R.id.tvListName)
                val tvListMetadata = cardView.findViewById<TextView>(R.id.tvListMetadata)
                val tvListPrice = cardView.findViewById<TextView>(R.id.tvListPrice)

                // Set the Name and Date
                tvListName.text = savedList.name
                tvListMetadata.text = "${savedList.items.size} items • ${savedList.date}"

                // Calculate the Total Price
                val totalPrice = savedList.items.sumOf { it.price }
                tvListPrice.text = "$${String.format(java.util.Locale.getDefault(), "%.2f", totalPrice)}"


                // Add the fully built card to the screen
                llRecentListsContainer.addView(cardView)
            }
        }

        val btnCompare = view.findViewById<Button>(R.id.btnStartComparing)
        btnCompare.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ComparisonFragment())
                .addToBackStack(null)
                .commit()
        }
        fetchListsFromBackend()

        val btnOpenMap = view.findViewById<Button>(R.id.btnOpenMap)

        btnOpenMap.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, StoresMapFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun fetchListsFromBackend() {
        val apiService = ApiService(getString(R.string.base_url))
        val sessionManager = SessionManager(requireContext())
        val currentUserId = sessionManager.getUserId() ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getShopLists(currentUserId)
                withContext(Dispatchers.Main) {
                    if (response.success && response.body.isNotEmpty() && response.body != "[]") {
                        parseAndAddLists(response.body)
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
                    val isChecked = if (itemObj.has("isChecked")) itemObj.getBoolean("isChecked") else false

                    val store = if (itemName.contains(" at ")) itemName.substringAfter(" at ") else "Unknown"
                    val name = if (itemName.contains(" at ")) itemName.substringBefore(" at ") else itemName

                    savedItems.add(SavedItem(name, itemPrice, store, isChecked))
                }

                val formattedDate = if (listObj.has("createdDate")) {
                    formatDateFromBackend(listObj.getString("createdDate"))
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                }

                val savedList = SavedList(localId, backendListId, topic, formattedDate, savedItems)

                // This updates the shared ViewModel and triggers the observer above
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
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = isoFormat.parse(dateString)
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date ?: Date())
        } catch (e: Exception) {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        }
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        bottomNav?.visibility = View.VISIBLE
    }
}