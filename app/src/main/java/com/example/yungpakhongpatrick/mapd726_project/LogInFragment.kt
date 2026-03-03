package com.example.yungpakhongpatrick.mapd726_project

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class LogInFragment : BaseFragment(R.layout.fragment_log_in) {

    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private val TAG = "LogInFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SessionManager
        sessionManager = SessionManager(requireContext())

        // REMOVED the auto-login check - user must always log in

        val baseUrl = getString(R.string.base_url)
        apiService = ApiService(baseUrl)

        val etUsername = view.findViewById<EditText>(R.id.etLoginUsername)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            if (username.isNotEmpty()) {
                loginOrRegisterUser(username, btnLogin)
            } else {
                etUsername.error = "Please enter a username"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        if (bottomNav != null) {
            bottomNav.visibility = View.GONE
        }
    }

    private fun loginOrRegisterUser(username: String, btnLogin: Button) {
        btnLogin.isEnabled = false
        btnLogin.text = "Connecting..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Check if user already exists
                val getResponse = apiService.getUsers()
                var userId: String? = null

                if (getResponse.success) {
                    userId = extractUserId(getResponse.body, username)
                }

                // Step 2: If user doesn't exist, create them
                if (userId == null) {
                    withContext(Dispatchers.Main) { btnLogin.text = "Creating account..." }
                    val createResponse = apiService.createUser(username)

                    if (createResponse.success) {
                        // Assuming backend returns the new user object with the _id
                        val newUserObj = JSONObject(createResponse.body)
                        userId = newUserObj.optString("_id")
                    }
                }

                // Step 3: Complete login and navigate
                withContext(Dispatchers.Main) {
                    if (userId != null && userId.isNotEmpty()) {
                        // Save user session using SessionManager (still save for this session)
                        sessionManager.saveUserSession(userId, username)

                        // Also save in SharedPreferences for backward compatibility
                        saveUserIdLocally(userId)

                        Log.d(TAG, "User logged in: $username with ID: $userId")
                        Toast.makeText(requireContext(), "Welcome, $username!", Toast.LENGTH_SHORT).show()

                        // Navigate to HomeFragment
                        navigateToHome()
                    } else {
                        Toast.makeText(requireContext(), "Failed to connect to server.", Toast.LENGTH_LONG).show()
                    }
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error during login", e)
                    Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"
                }
            }
        }
    }

    private fun extractUserId(jsonString: String, targetUsername: String): String? {
        return try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val userObj = jsonArray.getJSONObject(i)
                if (userObj.optString("username", "") == targetUsername) {
                    return userObj.optString("_id")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    private fun saveUserIdLocally(userId: String) {
        val sharedPrefs = requireActivity().getSharedPreferences("SmartCartPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("CURRENT_USER_ID", userId).apply()
    }

    private fun navigateToHome() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }
}