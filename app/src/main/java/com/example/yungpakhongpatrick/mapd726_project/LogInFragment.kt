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
        val etPassword = view.findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                loginOrRegisterUser(username, password, btnLogin)
            } else {
                etUsername.error = "Please enter a username/password"
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

    private fun loginOrRegisterUser(username: String, password: String, btnLogin: Button) {
        btnLogin.isEnabled = false
        btnLogin.text = "Connecting..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Check if user exists by getting all users and filtering
                withContext(Dispatchers.Main) { btnLogin.text = "Checking user..." }
                val getResponse = apiService.getUsers()

                var userId: String? = null
                var userExists = false
                var storedPassword: String? = null

                if (getResponse.success) {
                    // Parse the response to find if user exists and get their password
                    try {
                        val usersArray = JSONArray(getResponse.body)
                        for (i in 0 until usersArray.length()) {
                            val userObj = usersArray.getJSONObject(i)
                            val existingUsername = userObj.optString("username")

                            if (existingUsername.equals(username, ignoreCase = true)) {
                                userExists = true
                                userId = userObj.optString("_id")
                                storedPassword = userObj.optString("password")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing users response: ${e.message}")
                    }
                }

                // Step 2: Handle based on whether user exists
                if (userExists) {
                    // User exists - verify password
                    withContext(Dispatchers.Main) { btnLogin.text = "Verifying password..." }

                    if (storedPassword == password) {
                        // Password is correct - login successful
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "User logged in: $username with ID: $userId")

                            // Save user session
                            sessionManager.saveUserSession(userId!!, username)
                            saveUserIdLocally(userId!!)

                            Toast.makeText(requireContext(), "Welcome back, $username!", Toast.LENGTH_SHORT).show()
                            navigateToHome()
                        }
                    } else {
                        // Password is incorrect
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Incorrect password. Please try again.", Toast.LENGTH_LONG).show()
                            btnLogin.isEnabled = true
                            btnLogin.text = "Login"
                        }
                    }
                } else {
                    // User doesn't exist - create new account
                    withContext(Dispatchers.Main) { btnLogin.text = "Creating new account..." }
                    val createResponse = apiService.createUser(username, password)

                    if (createResponse.success) {
                        // Parse the new user object to get the ID
                        try {
                            val newUserObj = JSONObject(createResponse.body)
                            userId = newUserObj.optString("_id")

                            withContext(Dispatchers.Main) {
                                if (userId != null && userId!!.isNotEmpty()) {
                                    // Save user session
                                    sessionManager.saveUserSession(userId!!, username)
                                    saveUserIdLocally(userId!!)

                                    Log.d(TAG, "New user created: $username with ID: $userId")
                                    Toast.makeText(requireContext(), "Account created! Welcome, $username!", Toast.LENGTH_SHORT).show()
                                    navigateToHome()
                                } else {
                                    Toast.makeText(requireContext(), "Account created but failed to get user ID.", Toast.LENGTH_LONG).show()
                                    btnLogin.isEnabled = true
                                    btnLogin.text = "Login"
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Log.e(TAG, "Error parsing new user response", e)
                                Toast.makeText(requireContext(), "Account created but error parsing response.", Toast.LENGTH_LONG).show()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Login"
                            }
                        }
                    } else {
                        // Failed to create user
                        withContext(Dispatchers.Main) {
                            val errorMsg = try {
                                val errorObj = JSONObject(createResponse.body)
                                errorObj.optString("error", "Unknown error")
                            } catch (e: Exception) {
                                "Failed to create account"
                            }
                            Toast.makeText(requireContext(), "Error: $errorMsg", Toast.LENGTH_LONG).show()
                            btnLogin.isEnabled = true
                            btnLogin.text = "Login"
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error during login/registration", e)
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