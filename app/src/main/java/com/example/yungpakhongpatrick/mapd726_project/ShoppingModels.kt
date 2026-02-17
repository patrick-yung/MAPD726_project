package com.example.yungpakhongpatrick.mapd726_project

data class SavedItem(
    val name: String,
    val price: Double,
    val store: String,
    val isChecked: Boolean = false
)

data class SavedList(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val date: String,
    val items: List<SavedItem>
) {
    val itemCount: Int
        get() = items.size

    val totalPrice: Double
        get() = items.sumOf { it.price }
}
