package com.example.yungpakhongpatrick.mapd726_project

import android.util.Log
import org.json.JSONObject

class ProductRepository(private val apiService: ApiService) {

    companion object {
        private const val TAG = "ProductRepository"
    }

    suspend fun fetchCategorizedData(): Map<String, Map<String, List<Double>>>? {
        return try {
            val response = apiService.getCategorizedProducts()

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
                return result
            } else {
                Log.e(TAG, "Failed to fetch categorized data: ${response.errorMessage}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching categorized data: ${e.message}")
            null
        }
    }

    suspend fun searchProducts(query: String): List<Product>? {
        return try {
            val response = apiService.searchProducts(query)

            if (response.success && response.statusCode == 200) {
                val jsonArray = org.json.JSONArray(response.body)
                val products = mutableListOf<Product>()

                for (i in 0 until jsonArray.length()) {
                    val productJson = jsonArray.getJSONObject(i)
                    products.add(Product.fromJson(productJson))
                }
                return products
            } else {
                Log.e(TAG, "Failed to search products: ${response.errorMessage}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching products: ${e.message}")
            null
        }
    }
}

// Data class for Product
data class Product(
    val id: String,
    val name: String,
    val category: String,
    val prices: ProductPrices,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun fromJson(json: JSONObject): Product {
            val pricesJson = json.getJSONObject("prices")
            return Product(
                id = json.getString("_id"),
                name = json.getString("name"),
                category = json.getString("category"),
                prices = ProductPrices(
                    walmart = pricesJson.getDouble("walmart"),
                    costco = pricesJson.getDouble("costco"),
                    superstore = pricesJson.getDouble("superstore")
                ),
                createdAt = json.getString("createdAt"),
                updatedAt = json.getString("updatedAt")
            )
        }
    }
}

data class ProductPrices(
    val walmart: Double,
    val costco: Double,
    val superstore: Double
)