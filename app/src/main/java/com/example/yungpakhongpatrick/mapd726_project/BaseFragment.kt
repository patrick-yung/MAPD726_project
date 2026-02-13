package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment

open class BaseFragment(layoutId: Int) : Fragment(layoutId) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the back arrow by the ID you used in your XML
        val backArrow = view.findViewById<ImageView>(R.id.btnBackArrow)

        // If the arrow exists on the screen, give it the back logic
        backArrow?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}