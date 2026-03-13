package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Find the Bottom Navigation Bar
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 2. ALWAYS load the Login Screen when the app opens
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LogInFragment())
                .commit()
        }
        // 3. The "Switching" Logic
        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment? = when (item.itemId) {
                // If user clicks "Home" icon -> Load HomeFragment
                R.id.nav_home -> HomeFragment()

                // If user clicks "Deals" icon -> Load DealsFragment
                R.id.nav_deals -> DealsFragment()

                // If user clicks "List" icon -> Load ListFragment
                R.id.nav_list -> ListFragment()

                // (Optional) If you have an "Add" button
                R.id.nav_add -> AddItemsFragment()
                
                else -> null
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
