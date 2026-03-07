package com.example.yungpakhongpatrick.mapd726_project

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Log

class ListViewModel : ViewModel() {

    private val _allShoppingLists = MutableLiveData<List<SavedList>>(emptyList())
    val allShoppingLists: LiveData<List<SavedList>> = _allShoppingLists

    private val TAG = "ListViewModel"

    // Stores the current list after filtering
    private val _filteredLists = MutableLiveData<List<SavedList>>(emptyList())
    val filteredLists: LiveData<List<SavedList>> = _filteredLists

    // Holds the user's unsaved items if they navigate away
    val draftCartList = mutableListOf<CartItem>()

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

    // NEW: Update an existing list
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

    // Professional way to filter data in the ViewModel
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