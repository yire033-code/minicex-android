package com.example.minicex.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.databinding.FragmentResultsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val evaluationId = arguments?.getInt("evaluation_id", -1) ?: -1
        if (evaluationId != -1) {
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(appContext)
                val eval = db.evaluationDao().getEvaluationById(evaluationId)
                eval?.let {
                    withContext(Dispatchers.Main) {
                        if (_binding != null) {
                            // The calificacionTotal is in the 0-100 range.
                            // We scale it back out of 10 for the display e.g. 8.5
                            val displayScore = it.calificacionTotal / 10.0
                            binding.tvFinalScore.text = String.format(Locale.US, "%.1f", displayScore)
                            
                            // Overshoot reveal animation
                            binding.tvFinalScore.alpha = 0f
                            binding.tvFinalScore.scaleX = 0.5f
                            binding.tvFinalScore.scaleY = 0.5f
                            binding.tvFinalScore.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(700)
                                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                                .start()
                        }
                    }
                }
            }
        }

        binding.btnFinish.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            findNavController().navigate(R.id.action_results_to_home)
        }
        binding.btnViewReport.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            val bundle = Bundle().apply { putInt("evaluation_id", evaluationId) }
            findNavController().navigate(R.id.nav_evaluation_detail, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
