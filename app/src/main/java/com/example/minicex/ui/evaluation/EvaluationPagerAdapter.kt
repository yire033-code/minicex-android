package com.example.minicex.ui.evaluation

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.minicex.ui.evaluation.steps.*

class EvaluationPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Step1GeneralFragment()
            1 -> Step2RubricFragment()
            2 -> Step3FeedbackFragment()
            3 -> Step4SignaturesFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
