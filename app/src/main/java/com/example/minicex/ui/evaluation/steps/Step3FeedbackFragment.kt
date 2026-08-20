package com.example.minicex.ui.evaluation.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.minicex.R
import com.example.minicex.databinding.FragmentStep3FeedbackBinding
import com.example.minicex.ui.evaluation.EvaluationSharedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Step3FeedbackFragment : Fragment() {
    private var _binding: FragmentStep3FeedbackBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStep3FeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Add entry animation
        view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in_up))

        val viewModel = ViewModelProvider(requireParentFragment()).get(EvaluationSharedViewModel::class.java)

        // Prepopulate if exists
        binding.etStrengths.setText(viewModel.strengths.value ?: "")
        binding.etToImprove.setText(viewModel.toImprove.value ?: "")

        // Observe times reactively so they update immediately when entering the step or when updated
        viewModel.observationTime.observe(viewLifecycleOwner) { time ->
            if (!binding.etObservationTime.hasFocus()) {
                val currentText = binding.etObservationTime.text.toString()
                val newText = if (time != null && time > 0) time.toString() else ""
                if (currentText != newText) {
                    binding.etObservationTime.setText(newText)
                }
            }
        }

        viewModel.feedbackTime.observe(viewLifecycleOwner) { time ->
            if (!binding.etFeedbackTime.hasFocus()) {
                val currentText = binding.etFeedbackTime.text.toString()
                val newText = if (time != null && time > 0) time.toString() else ""
                if (currentText != newText) {
                    binding.etFeedbackTime.setText(newText)
                }
            }
        }

        binding.etStrengths.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.strengths.value = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etToImprove.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.toImprove.value = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etObservationTime.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newVal = s?.toString()?.toIntOrNull() ?: 0
                viewModel.observationTime.value = newVal
                if (binding.etObservationTime.hasFocus()) {
                    viewModel.isObservationTimeManuallyEdited = true
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etFeedbackTime.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newVal = s?.toString()?.toIntOrNull() ?: 0
                viewModel.feedbackTime.value = newVal
                if (binding.etFeedbackTime.hasFocus()) {
                    viewModel.isFeedbackTimeManuallyEdited = true
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({ checkAndShowFeedbackTutorial() }, 400)
    }

    private fun checkAndShowFeedbackTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx)
            != com.example.minicex.ui.utils.TutorialManager.PHASE_STEP3) return

        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())
        val steps = listOf(
            Triple(binding.etStrengths as android.view.View,
                "Registra fortalezas y mejoras",
                "Escribe qué hizo bien el alumno y qué necesita seguir desarrollando. Estos comentarios aparecerán en su reporte."),
            Triple(binding.etObservationTime as android.view.View,
                "Registra los tiempos",
                "Indica cuántos minutos duraron la observación clínica y la retroalimentación posterior.")
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                com.example.minicex.ui.utils.TutorialManager.advancePhase(ctx) // → PHASE_STEP4
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(400)
                    if (_binding != null && isAdded) {
                        val evalFragment = parentFragment as? com.example.minicex.ui.evaluation.EvaluationFragment
                        evalFragment?.advanceToNextStep()
                    }
                }
                return
            }
            val (view, title, desc) = steps[index]
            overlay.show(
                targetView = view,
                stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_STEP3 + index,
                title = title, description = desc,
                isLastStep = false,
                onNext = { showStep(index + 1) },
                onSkip = { com.example.minicex.ui.utils.TutorialManager.skipAll(ctx) }
            )
        }
        showStep(0)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

