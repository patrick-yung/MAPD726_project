package com.example.yungpakhongpatrick.mapd726_project

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class UserProfileFragment : BaseFragment(R.layout.fragment_user_profile) {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvProfileName: TextView
    private lateinit var tvPhoneNumber: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        // 1. Find UI components
        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvPhoneNumber = view.findViewById(R.id.tvPhoneNumber)
        val tvEditProfile = view.findViewById<TextView>(R.id.tvEditProfile)
        val rowPhoneNumber = view.findViewById<LinearLayout>(R.id.rowPhoneNumber)
        val rowSettings = view.findViewById<LinearLayout>(R.id.rowSettings)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        // 2. Load dynamically saved Name from login
        val loggedInUsername = sessionManager.getUserName()
        if (!loggedInUsername.isNullOrEmpty()) {
            tvProfileName.text = loggedInUsername
        } else {
            tvProfileName.text = "SmartCart User"
        }

        // Load dynamically saved Phone Number
        val sharedPrefs = requireActivity().getSharedPreferences("SmartCartPrefs", Context.MODE_PRIVATE)
        val savedPhone = sharedPrefs.getString("USER_PHONE", "Add Phone Number")
        tvPhoneNumber.text = savedPhone

        // 3. Set Click Listeners for interactions
        tvEditProfile.setOnClickListener {
            showEditNameDialog()
        }

        rowPhoneNumber.setOnClickListener {
            showEditPhoneDialog()
        }

        rowSettings.setOnClickListener {
            Toast.makeText(requireContext(), "App Settings coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            performLogout()
        }
    }

    private fun showEditNameDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Edit Profile Name")

        val input = EditText(requireContext())
        input.setText(tvProfileName.text)
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                tvProfileName.text = newName

                val userId = sessionManager.getUserId() ?: ""
                sessionManager.saveUserSession(userId, newName)

                Toast.makeText(requireContext(), "Name updated successfully", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun showEditPhoneDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Update Phone Number")

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_PHONE

        val currentText = tvPhoneNumber.text.toString()
        if (currentText != "Add Phone Number") {
            input.setText(currentText)
        }
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, _ ->
            val newPhone = input.text.toString().trim()
            if (newPhone.isNotEmpty()) {
                tvPhoneNumber.text = newPhone

                val sharedPrefs = requireActivity().getSharedPreferences("SmartCartPrefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("USER_PHONE", newPhone).apply()

                Toast.makeText(requireContext(), "Phone number updated", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun performLogout() {
        sessionManager.clearSession()

        val sharedPrefs = requireActivity().getSharedPreferences("SmartCartPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("CURRENT_USER_ID").apply()

        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LogInFragment())
            .commit()
    }
}