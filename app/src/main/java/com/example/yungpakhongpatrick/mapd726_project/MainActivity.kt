package com.example.yungpakhongpatrick.mapd726_project

import DealsFragment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.yungpakhongpatrick.mapd726_project.R.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

       // 1. Find the Bottom Navigation Bar
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 2. Load HomeFragment AUTOMATICALLY when the app starts
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
        // 3. The "Switching" Logic
        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null

            when (item.itemId) {
                // If user clicks "Home" icon -> Load HomeFragment
                R.id.nav_home -> selectedFragment = HomeFragment()

                // If user clicks "Deals" icon -> Load DealsFragment
                  R.id.nav_deals -> selectedFragment = DealsFragment()

                // If user clicks "List" icon -> Load ListFragment
                 R.id.nav_list -> selectedFragment = ListFragment()

                // (Optional) If you have an "Add" button
                R.id.nav_add -> selectedFragment = AddItemsFragment()
            }
            // 4. Perform the actual switch
            if (selectedFragment != null) {
                loadFragment(selectedFragment)
                true
            } else {
                false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    }

