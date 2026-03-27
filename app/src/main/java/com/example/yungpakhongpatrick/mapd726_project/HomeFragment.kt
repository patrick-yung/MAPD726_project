package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private val tagName = "HomeFragment"
    private var tvFavoriteStoresSummary: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(requireActivity())[ListViewModel::class.java]

        val sessionManager = SessionManager(requireContext())
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        val userName = sessionManager.getUserName() ?: "User"
        tvWelcome.text = "Welcome back, $userName"

        tvFavoriteStoresSummary = view.findViewById(R.id.tvFavoriteStoresSummary)
        updateFavoriteStoresSummary()

        val ivHomeProfileAvatar = view.findViewById<ImageView>(R.id.ivHomeProfileAvatar)
        ivHomeProfileAvatar?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UserProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        val etSearchHome = view.findViewById<EditText>(R.id.etSearchHome)
        etSearchHome.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                openComparisonWithQuery(etSearchHome.text.toString().trim())
                true
            } else {
                false
            }
        }

        val llRecentListsContainer = view.findViewById<LinearLayout>(R.id.llRecentListsContainer)

        listViewModel.allShoppingLists.observe(viewLifecycleOwner) { allLists ->
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

            val recentLists = allLists.takeLast(3).reversed()

            for (savedList in recentLists) {
                val cardView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_home_list_card, llRecentListsContainer, false)

                val tvListName = cardView.findViewById<TextView>(R.id.tvListName)
                val tvListMetadata = cardView.findViewById<TextView>(R.id.tvListMetadata)
                val tvListPrice = cardView.findViewById<TextView>(R.id.tvListPrice)

                tvListName.text = savedList.name
                tvListMetadata.text = "${savedList.items.size} items • ${savedList.date}"

                val totalPrice = savedList.items.sumOf { it.price }
                tvListPrice.text = "$${String.format(Locale.getDefault(), "%.2f", totalPrice)}"

                llRecentListsContainer.addView(cardView)
            }
        }

        val btnCompare = view.findViewById<Button>(R.id.btnStartComparing)
        btnCompare.setOnClickListener {
            openComparisonWithQuery(etSearchHome.text.toString().trim())
        }

        val btnOpenMap = view.findViewById<Button>(R.id.btnOpenMap)
        btnOpenMap.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, StoresMapFragment())
                .addToBackStack(null)
                .commit()
        }

        fetchListsFromBackend()
    }

    private fun openComparisonWithQuery(query: String) {
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
                Log.e(tagName, "Error fetching lists", e)
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

                    val store =
                        if (itemName.contains(" at ")) itemName.substringAfter(" at ") else "Unknown"
                    val name =
                        if (itemName.contains(" at ")) itemName.substringBefore(" at ") else itemName

                    savedItems.add(SavedItem(name, itemPrice, store, isChecked))
                }

                val formattedDate = if (listObj.has("createdDate")) {
                    formatDateFromBackend(listObj.getString("createdDate"))
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                }

                val savedList = SavedList(localId, backendListId, topic, formattedDate, savedItems)

                val existingLists = listViewModel.allShoppingLists.value ?: emptyList()
                if (!existingLists.any { it.id == localId }) {
                    listViewModel.addList(savedList)
                }
            }
        } catch (e: Exception) {
            Log.e(tagName, "Error parsing lists", e)
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

    private fun updateFavoriteStoresSummary() {
        val favoriteStores = StoreRepository.stores
            .filter { it.isFavorite && it.isEnabled }
            .take(3)

        tvFavoriteStoresSummary?.text = if (favoriteStores.isEmpty()) {
            "You have not favorited any stores yet."
        } else {
            favoriteStores.joinToString("\n") { store ->
                "★ ${store.name} • ${String.format(Locale.getDefault(), "%.1f", store.rating)}★"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        bottomNav?.visibility = View.VISIBLE
        updateFavoriteStoresSummary()
    }
}