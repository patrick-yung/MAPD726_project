package com.example.yungpakhongpatrick.mapd726_project

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Data class for API responses
data class ApiResponse(
    val success: Boolean,
    val statusCode: Int,
    val body: String,
    val errorMessage: String? = null
)

class ApiService(private val baseUrl: String) {

    companion object {
        private const val TAG = "ApiService"
    }

    // Generic method for making HTTP requests
    fun makeRequest(
        endpoint: String,
        method: String,
        jsonBody: JSONObject? = null
    ): ApiResponse {
        return try {
            val url = URL("$baseUrl$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000 // 10 seconds timeout
            conn.readTimeout = 10000

            // Handle request body for POST/PUT methods
            if (jsonBody != null && (method == "POST" || method == "PUT")) {
                conn.doOutput = true
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()
            }

            val code = conn.responseCode
            val response = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            conn.disconnect()

            ApiResponse(
                success = code in 200..299,
                statusCode = code,
                body = response
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in makeRequest: ${e.message}")
            ApiResponse(
                success = false,
                statusCode = -1,
                body = "",
                errorMessage = e.message ?: "Unknown error occurred"
            )
        }
    }

    // Specific API methods
    fun getUsers(): ApiResponse {
        return makeRequest("/users", "GET")
    }

    fun createUser(username: String): ApiResponse {
        val jsonBody = JSONObject().apply {
            put("username", username)
        }
        return makeRequest("/users", "POST", jsonBody)
    }
    // Add this method to ApiService.kt
    fun createShopList(userId: String, shopListJson: JSONObject): ApiResponse {
        return makeRequest("/users/$userId/shoplists", "POST", shopListJson)
    }

    // You can add more methods as needed
    fun getUserById(userId: String): ApiResponse {
        return makeRequest("/users/$userId", "GET")
    }

    fun updateUser(userId: String, username: String): ApiResponse {
        val jsonBody = JSONObject().apply {
            put("username", username)
        }
        return makeRequest("/users/$userId", "PUT", jsonBody)
    }

    fun deleteUser(userId: String): ApiResponse {
        return makeRequest("/users/$userId", "DELETE")
    }
}