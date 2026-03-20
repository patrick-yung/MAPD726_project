package com.example.yungpakhongpatrick.mapd726_project

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ListViewModel : ViewModel() {

    private val _allShoppingLists = MutableLiveData<List<SavedList>>(emptyList())
    val allShoppingLists: LiveData<List<SavedList>> = _allShoppingLists

    private val _categorizedData = MutableLiveData<Map<String, Map<String, List<Double>>>>()
    val categorizedData: LiveData<Map<String, Map<String, List<Double>>>> = _categorizedData

    private val _isLoadingProducts = MutableLiveData<Boolean>()
    val isLoadingProducts: LiveData<Boolean> = _isLoadingProducts

    private val _productError = MutableLiveData<String?>()
    val productError: LiveData<String?> = _productError

    private val TAG = "ListViewModel"

    // Stores the current list after filtering
    private val _filteredLists = MutableLiveData<List<SavedList>>(emptyList())
    val filteredLists: LiveData<List<SavedList>> = _filteredLists

    val draftCartList = mutableListOf<CartItem>()

    // Initialize with empty map - will be populated from backend
    private var _categorizedDataMap: Map<String, Map<String, List<Double>>> = emptyMap()

    // Function to fetch categorized data from backend
    fun fetchCategorizedData(apiService: ApiService) {
        viewModelScope.launch {
            _isLoadingProducts.value = true
            _productError.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getCategorizedProducts()
                }

                if (response.success && response.statusCode == 200) {
                    val jsonObject = JSONObject(response.body)
                    val result = mutableMapOf<String, MutableMap<String, List<Double>>>()

                    jsonObject.keys().forEach { category ->
                        val productsObject = jsonObject.getJSONObject(category)
                        val products = mutableMapOf<String, List<Double>>()

                        productsObject.keys().forEach { productName ->
                            val pricesArray = productsObject.getJSONArray(productName)
                            val prices = mutableListOf<Double>()
                            for (i in 0 until pricesArray.length()) {
                                prices.add(pricesArray.getDouble(i))
                            }
                            products[productName] = prices
                        }
                        result[category] = products
                    }

                    _categorizedDataMap = result
                    _categorizedData.value = result
                    Log.d(TAG, "Successfully loaded ${result.size} categories from backend")
                } else {
                    _productError.value = "Failed to load products: ${response.errorMessage ?: "Unknown error"}"
                    Log.e(TAG, "Failed to fetch categorized data: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _productError.value = "Error loading products: ${e.message}"
                Log.e(TAG, "Error fetching categorized data", e)
            } finally {
                _isLoadingProducts.value = false
            }
        }
    }

    // Get categorized data synchronously (for use in fragments)
    fun getCategorizedData(): Map<String, Map<String, List<Double>>> {
        return _categorizedDataMap
    }

    // Search products by name (returns list of products with their categories)
    fun searchProducts(apiService: ApiService, query: String, callback: (List<SearchResult>?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.searchProducts(query)
                }

                if (response.success && response.statusCode == 200) {
                    val jsonArray = org.json.JSONArray(response.body)
                    val results = mutableListOf<SearchResult>()

                    for (i in 0 until jsonArray.length()) {
                        val productJson = jsonArray.getJSONObject(i)
                        results.add(
                            SearchResult(
                                name = productJson.getString("name"),
                                category = productJson.getString("category"),
                                prices = listOf(
                                    productJson.getJSONObject("prices").getDouble("walmart"),
                                    productJson.getJSONObject("prices").getDouble("costco"),
                                    productJson.getJSONObject("prices").getDouble("superstore")
                                )
                            )
                        )
                    }
                    callback(results)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching products", e)
                callback(null)
            }
        }
    }

    // Add a new list
    fun addList(savedList: SavedList) {
        val currentList = _allShoppingLists.value?.toMutableList() ?: mutableListOf()

        // Check if list already exists
        val existingIndex = currentList.indexOfFirst { it.id == savedList.id }
        if (existingIndex >= 0) {
            // Replace existing list
            currentList[existingIndex] = savedList
            Log.d(TAG, "Replaced existing list: ${savedList.name}")
        } else {
            // Add new list
            currentList.add(savedList)
            Log.d(TAG, "Added new list: ${savedList.name}")
        }

        _allShoppingLists.value = currentList
        _filteredLists.value = currentList
    }

    // Update an existing list
    fun updateList(savedList: SavedList) {
        val currentList = _allShoppingLists.value?.toMutableList() ?: return

        val existingIndex = currentList.indexOfFirst { it.id == savedList.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = savedList
            _allShoppingLists.value = currentList
            Log.d(TAG, "Updated list: ${savedList.name} with ${savedList.items.size} items")
        } else {
            Log.w(TAG, "Attempted to update non-existent list: ${savedList.id}")
            // If list doesn't exist, add it
            currentList.add(savedList)
            _allShoppingLists.value = currentList
            Log.d(TAG, "Added list that was meant to be updated: ${savedList.name}")
        }
    }

    // Update an item's checked state in a specific list
    fun updateItemChecked(listId: Long, itemIndex: Int, isChecked: Boolean) {
        val currentList = _allShoppingLists.value?.toMutableList() ?: return

        val listIndex = currentList.indexOfFirst { it.id == listId }
        if (listIndex >= 0) {
            val list = currentList[listIndex]
            if (itemIndex < list.items.size) {
                val updatedItems = list.items.toMutableList()
                val item = updatedItems[itemIndex]
                updatedItems[itemIndex] = item.copy(isChecked = isChecked)

                val updatedList = list.copy(items = updatedItems)
                currentList[listIndex] = updatedList
                _allShoppingLists.value = currentList

                Log.d(TAG, "Updated item $itemIndex in list $listId to isChecked=$isChecked")
            }
        }
    }

    // Remove a list
    fun removeList(listId: Long) {
        val currentList = _allShoppingLists.value?.toMutableList() ?: return
        currentList.removeAll { it.id == listId }
        _allShoppingLists.value = currentList
        Log.d(TAG, "Removed list: $listId")
    }

    fun filterByMonth(month: String) {
        val all = _allShoppingLists.value ?: return

        if (month == "All Months") {
            _filteredLists.value = all
        } else {
            // Only keep lists where the date string contains the month name
            _filteredLists.value = all.filter { it.date.contains(month) }
        }
        Log.d(TAG, "Filtered lists for month: $month")
    }

    // Clear all lists
    fun clearAllLists() {
        _allShoppingLists.value = emptyList()
        Log.d(TAG, "Cleared all lists")
    }

    // Get a specific list by ID
    fun getListById(listId: Long): SavedList? {
        return _allShoppingLists.value?.firstOrNull { it.id == listId }
    }
}

// Data class for search results
data class SearchResult(
    val name: String,
    val category: String,
    val prices: List<Double>
)