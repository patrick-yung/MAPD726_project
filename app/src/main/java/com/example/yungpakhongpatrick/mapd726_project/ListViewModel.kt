package com.example.yungpakhongpatrick.mapd726_project

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ListViewModel : ViewModel() {
    private val _allLists = MutableLiveData<List<SavedList>>(emptyList())
    val allShoppingLists: LiveData<List<SavedList>> = _allLists

    fun addList(newList: SavedList) {
        val current = _allLists.value.orEmpty()
        _allLists.value = current + newList
    }

    fun updateItemChecked(listId: Long, itemIndex: Int, isChecked: Boolean) {
        val current = _allLists.value.orEmpty()
        _allLists.value = current.map { savedList ->
            if (savedList.id != listId) {
                savedList
            } else {
                val updatedItems = savedList.items.mapIndexed { index, item ->
                    if (index == itemIndex) {
                        item.copy(isChecked = isChecked)
                    } else {
                        item
                    }
                }
                savedList.copy(items = updatedItems)
            }
        }
    }
}


