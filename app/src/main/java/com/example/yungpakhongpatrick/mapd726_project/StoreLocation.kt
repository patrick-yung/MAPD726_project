package com.example.yungpakhongpatrick.mapd726_project

data class StoreLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val rating: Double,
    var isEnabled: Boolean = true,
    var isFavorite: Boolean = false
)