package com.example.yungpakhongpatrick.mapd726_project

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OpenFoodFactsService {

    fun searchProducts(query: String): String {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val urlString =
            "https://world.openfoodfacts.org/cgi/search.pl" +
                    "?search_terms=$encodedQuery" +
                    "&search_simple=1" +
                    "&action=process" +
                    "&json=1" +
                    "&page_size=25"

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "SmartCart/1.0 (Centennial College MAPD726 Android Project)"
            )
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    fun getProductByBarcode(barcode: String): String {
        val urlString = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "SmartCart/1.0 (Centennial College MAPD726 Android Project)"
            )
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}