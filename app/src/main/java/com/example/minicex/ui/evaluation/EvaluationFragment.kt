package com.example.minicex.ui.evaluation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.minicex.ui.utils.showError
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.minicex.databinding.FragmentEvaluationBinding

class EvaluationFragment : Fragment() {

    private var _binding: FragmentEvaluationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvaluationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = EvaluationPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false // Disable swiping to force button use

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateNavigationButtons(position)

                val viewModel = ViewModelProvider(this@EvaluationFragment).get(EvaluationSharedViewModel::class.java)

                if (position == 1) { // Entered Step 2 (Rubric)
                    if (viewModel.rubricStartTime == 0L) {
                        viewModel.rubricStartTime = System.currentTimeMillis()
                    }
                } else if (position == 2) { // Entered Step 3 (Feedback)
                    if (!viewModel.isFeedbackTimeManuallyEdited) {
                        viewModel.feedbackStartTime = System.currentTimeMillis()
                    }
                }
            }
        })

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            val viewModel = ViewModelProvider(this).get(EvaluationSharedViewModel::class.java)

            when (current) {
                0 -> { // Step 1: General
                    if (!viewModel.isStep1Complete()) {
                        binding.btnNext.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        val missing = viewModel.getStep1MissingFields().joinToString(", ")
                        showError("Falta completar: $missing")
                        return@setOnClickListener
                    }
                }
                1 -> { // Step 2: Rubric
                    // Automatically calculate observation time if not manually edited
                    if (!viewModel.isObservationTimeManuallyEdited && viewModel.rubricStartTime > 0L) {
                        val diffMs = System.currentTimeMillis() - viewModel.rubricStartTime
                        val diffMins = (diffMs / 60000).toInt().coerceAtLeast(1)
                        viewModel.observationTime.value = diffMins
                    }
                    if (!viewModel.isStep2Complete()) {
                        binding.btnNext.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        val missing = viewModel.getStep2MissingFields().joinToString(", ")
                        showError("Falta completar en la rúbrica: $missing")
                        return@setOnClickListener
                    }
                }
                2 -> { // Step 3: Feedback
                    // Automatically calculate feedback time if not manually edited
                    if (!viewModel.isFeedbackTimeManuallyEdited && viewModel.feedbackStartTime > 0L) {
                        val diffMs = System.currentTimeMillis() - viewModel.feedbackStartTime
                        val diffMins = (diffMs / 60000).toInt().coerceAtLeast(1)
                        viewModel.feedbackTime.value = diffMins
                    }
                    if (!viewModel.isStep3Complete()) {
                        binding.btnNext.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        val missing = viewModel.getStep3MissingFields().joinToString(", ")
                        showError("Falta completar: $missing")
                        return@setOnClickListener
                    }
                }
            }

            binding.btnNext.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (current < 3) {
                binding.viewPager.currentItem = current + 1
            }
        }

        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.currentItem = current - 1
            }
        }
    }

    private fun updateNavigationButtons(position: Int) {
        val stepNames = arrayOf("DATOS", "RÚBRICA", "FEEDBACK", "REVISIÓN")
        binding.tvStepLabel.text = "PASO ${position + 1} DE 4 · ${stepNames[position]}"
        binding.progressIndicator.setProgressCompat((position + 1) * 25, true)
        binding.btnBack.visibility = if (position == 0) View.GONE else View.VISIBLE
        binding.btnNext.visibility = if (position == 3) View.GONE else View.VISIBLE
    }

    fun advanceToNextStep() {
        val current = binding.viewPager.currentItem
        if (current < 3) {
            binding.viewPager.currentItem = current + 1
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
