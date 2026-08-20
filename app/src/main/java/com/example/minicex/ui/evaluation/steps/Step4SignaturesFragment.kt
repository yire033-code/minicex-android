package com.example.minicex.ui.evaluation.steps

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import com.example.minicex.ui.utils.showError
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.minicex.R
import com.example.minicex.databinding.FragmentStep4SignaturesBinding
import com.example.minicex.ui.evaluation.EvaluationSharedViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.repository.SyncRepository

class Step4SignaturesFragment : Fragment() {
    private var _binding: FragmentStep4SignaturesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStep4SignaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Add entry animation
        view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in_up))

        val viewModel = ViewModelProvider(requireParentFragment()).get(EvaluationSharedViewModel::class.java)
        fun updateReview() {
            binding.tvReviewStudent.text =
                viewModel.selectedStudent.value?.nombreCompleto ?: "Alumno sin seleccionar"
            binding.tvReviewContext.text = listOf(
                viewModel.clinicalSetting.value.orEmpty(),
                viewModel.complexity.value.orEmpty()
            ).filter { it.isNotBlank() }.joinToString(" · ")
            val scores = viewModel.scores.value.orEmpty()
            val numericScores = scores.values.filter { it > 0 }
            binding.tvReviewCompleted.text = "${scores.size} de 7"
            binding.tvReviewScore.text = if (numericScores.isEmpty()) {
                "— / 9"
            } else {
                String.format(java.util.Locale.US, "%.1f / 9", numericScores.average())
            }
        }
        viewModel.selectedStudent.observe(viewLifecycleOwner) { updateReview() }
        viewModel.clinicalSetting.observe(viewLifecycleOwner) { updateReview() }
        viewModel.complexity.observe(viewLifecycleOwner) { updateReview() }
        viewModel.scores.observe(viewLifecycleOwner) { updateReview() }
        updateReview()

        binding.btnSubmit.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            binding.btnSubmit.isEnabled = false
            binding.btnSubmit.text = ""
            binding.submitProgress.visibility = View.VISIBLE
            val evalSig = ""
            val studSig = ""

            val appContext = requireContext().applicationContext
            val navController = parentFragment?.findNavController() ?: findNavController()
            val prefs = appContext.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
            val evaluatorEmail = prefs.getString("evaluador_email", "")

            if (evaluatorEmail.isNullOrEmpty()) {
                resetSubmitState()
                showError("Error: Sesión de evaluador no encontrada. Inicie sesión nuevamente.")
                return@setOnClickListener
            }
 
            if (viewModel.selectedStudent.value == null || viewModel.selectedStudent.value?.idAlumno == 0) {
                resetSubmitState()
                showError("Por favor, seleccione un alumno en el Paso 1 antes de finalizar la evaluación")
                return@setOnClickListener
            }

            viewModel.setSignatures(evalSig, studSig)

            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(appContext)
                val userLocal = db.userDao().getUserByEmail(evaluatorEmail)
                val evaluatorId = userLocal?.idUsuario ?: prefs.getInt("evaluador_id", -1)

                if (evaluatorId == -1) {
                    resetSubmitState()
                    showError("Error: Sesión de evaluador no encontrada. Inicie sesión nuevamente.")
                    return@launch
                }

                viewModel.saveEvaluation(appContext, evaluatorId) { evaluationId, errorMsg ->
                    if (errorMsg != null || evaluationId == null) {
                        resetSubmitState()
                        showError(errorMsg ?: "Error desconocido al guardar la evaluación.")
                        return@saveEvaluation
                    }

                    val mainActivity = activity as? com.example.minicex.MainActivity
                    if (mainActivity != null) {
                        mainActivity.triggerSync()
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val dbSync = AppDatabase.getDatabase(appContext)
                                val apiService = RetrofitClient.instance
                                val repo = SyncRepository(dbSync.evaluationDao(), apiService, appContext)
                                repo.autoSync()
                            } catch (e: Exception) {
                                Log.e("Sync", "Error en autoSync automatico al finalizar: ${e.message}")
                            }
                        }
                    }

                    val bundle = Bundle().apply { putInt("evaluation_id", evaluationId) }
                    try {
                        navController.navigate(R.id.action_evaluation_to_results, bundle)
                    } catch (e: Exception) {
                        Log.e("Navigation", "Error navigating to results from child fragment: ${e.message}")
                        try {
                            navController.navigate(R.id.nav_results, bundle)
                        } catch (ex: Exception) {
                            Log.e("Navigation", "Fatal fallback navigation error: ${ex.message}")
                        }
                    }
                }
            }
        }

    }

    private fun resetSubmitState() {
        if (_binding == null) return
        binding.submitProgress.visibility = View.GONE
        binding.btnSubmit.isEnabled = true
        binding.btnSubmit.text = "Guardar evaluación"
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({ checkAndShowConfirmTutorial() }, 400)
    }

    private fun checkAndShowConfirmTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx)
            != com.example.minicex.ui.utils.TutorialManager.PHASE_STEP4) return

        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())
        overlay.show(
            targetView = binding.btnSubmit,
            stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_STEP4,
            title = "Revisa y guarda",
            description = "Confirma el alumno, el contexto y el promedio. Al guardar, la evaluación quedará segura en el dispositivo y se enviará cuando haya conexión.",
            isLastStep = false,
            onNext = {
                com.example.minicex.ui.utils.TutorialManager.advancePhase(ctx) // -> PHASE_DETAIL
                binding.btnSubmit.performClick()
            },
            onSkip = { com.example.minicex.ui.utils.TutorialManager.skipAll(ctx) }
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
