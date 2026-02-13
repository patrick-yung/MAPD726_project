package com.example.yungpakhongpatrick.mapd726_project

import ComparisonFragment
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

class HomeFragment : Fragment() {

    // 1. Load the XML Layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This tells Android which XML file acts as the "face" of this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    // 2. Set up the Logic (Button Clicks)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCompare = view.findViewById<Button>(R.id.btnStartComparing)

        // Set the click listener to open the new screen
        btnCompare.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ComparisonFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}