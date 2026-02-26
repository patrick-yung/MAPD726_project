package com.example.yungpakhongpatrick.mapd726_project

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment

open class BaseFragment(layoutId: Int) : Fragment(layoutId) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backArrow = view.findViewById<ImageView>(R.id.btnBackArrow)
        backArrow?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    override fun onResume() {
        super.onResume()
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        if (bottomNav != null) {
            bottomNav.visibility = View.VISIBLE
        }
    }
}