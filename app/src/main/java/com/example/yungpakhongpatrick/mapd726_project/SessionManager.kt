package com.example.yungpakhongpatrick.mapd726_project

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    // Save user session
    fun saveUserSession(userId: String, userName: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    // Get user ID
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    // Get user name
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    // Check if user is logged in
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Clear session (logout)
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}