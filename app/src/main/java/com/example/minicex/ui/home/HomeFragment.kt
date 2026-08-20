package com.example.minicex.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import com.example.minicex.ui.utils.showInfo
import com.example.minicex.ui.utils.TutorialManager
import com.example.minicex.ui.utils.TutorialOverlay
import com.example.minicex.ui.tutorial.WelcomeTutorialDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.repository.SyncRepository
import com.example.minicex.databinding.FragmentHomeBinding
import com.example.minicex.ui.evaluation.EvaluationSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), WelcomeTutorialDialog.TutorialHost {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var tutorialOverlay: TutorialOverlay? = null
    private var homeTutorialStarted = false   // prevent re-entry from flow emissions

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Limpiar cualquier acción de prueba atorada en la cola (bug previo)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext().applicationContext)
                db.syncQueueDao().deleteDemoQueueActions()
                if (TutorialManager.isDone(requireContext().applicationContext)) {
                    TutorialManager.cleanupDemoData(requireContext().applicationContext)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error clearing demo queue: ${e.message}")
            }
        }

        val prefs = requireContext().getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("evaluador_nombre", "Docente")
        val evaluadorEmail = prefs.getString("evaluador_email", "")
        binding.tvWelcome.text = "Hola, $name"

        // Entrance animations
        listOf(
            binding.tvWelcome, binding.tvDashboardTitle, binding.btnRefreshSync, binding.btnRestartTutorial,
            binding.cardStatTotal, binding.cardStatAvg, binding.cardStatPending,
            binding.tvRecentLabel
        ).forEachIndexed { idx, v ->
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(idx * 100L).start()
        }

        binding.btnRestartTutorial.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val ctx = requireContext()
            TutorialManager.setPhase(ctx, TutorialManager.PHASE_NOT_STARTED)
            homeTutorialStarted = false
            checkAndShowTutorial()
        }

        binding.btnRefreshSync.setOnClickListener { btn ->
            btn.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val rotate = RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 1000; repeatCount = Animation.INFINITE; interpolator = LinearInterpolator()
            }
            btn.startAnimation(rotate); btn.isEnabled = false
            val appCtx = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                showInfo("Sincronizando...")
                try {
                    val db = AppDatabase.getDatabase(appCtx)
                    val repo = SyncRepository(db.evaluationDao(), RetrofitClient.instance, appCtx)
                    repo.autoSync(isManual = true)
                    if (_binding != null) showSuccess("Sincronización completada con éxito.")
                } catch (e: Exception) {
                    if (_binding != null) showError("Fallo al sincronizar: ${e.message}")
                } finally {
                    if (_binding != null) { btn.clearAnimation(); btn.isEnabled = true }
                }
            }
        }

        binding.rvEvaluations.layoutManager = LinearLayoutManager(requireContext())
        binding.btnEmptyCreate.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            findNavController().navigate(R.id.nav_evaluation)
        }
        
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(appCtx)
            val userLocal = db.userDao().getUserByEmail(evaluadorEmail ?: "")
            val resolvedId = userLocal?.idUsuario ?: prefs.getInt("evaluador_id", -1)
            if (_binding != null) {
                loadDashboardData(resolvedId)
            }
        }

        // Tutorial check — after views settle
        view.postDelayed({ checkAndShowTutorial() }, 900)
    }

    // ── Tutorial entry points ─────────────────────────────────────────────────

    override fun onTutorialStarted() {
        homeTutorialStarted = false
        view?.postDelayed({ showHomeTutorial() }, 400)
    }

    private fun checkAndShowTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        when {
            TutorialManager.isNotStarted(ctx) -> {
                if (childFragmentManager.findFragmentByTag(WelcomeTutorialDialog.TAG) == null) {
                    WelcomeTutorialDialog.newInstance()
                        .show(childFragmentManager, WelcomeTutorialDialog.TAG)
                }
            }
            TutorialManager.getCurrentPhase(ctx) == TutorialManager.PHASE_HOME
                && !homeTutorialStarted -> showHomeTutorial()
        }
    }

    private fun showHomeTutorial() {
        if (!isAdded || _binding == null || homeTutorialStarted) return
        homeTutorialStarted = true

        val ctx = requireContext()
        val fab = requireActivity().findViewById<View>(R.id.fab)
        tutorialOverlay?.dismiss(animated = false)
        tutorialOverlay = TutorialOverlay(requireActivity())
        val overlay = tutorialOverlay!!
        val recentActivityTarget = if ((binding.rvEvaluations.adapter?.itemCount ?: 0) > 0) {
            binding.rvEvaluations
        } else {
            binding.tvRecentLabel
        }

        val steps = listOf(
            Triple(binding.btnRefreshSync as View,
                "Trabaja con o sin internet",
                "Tus evaluaciones se guardan primero en el dispositivo. Toca aquí cuando quieras enviar los datos pendientes al sistema institucional."),
            Triple(binding.cardStatTotal as View,
                "Resumen de tu actividad",
                "Consulta cuántas evaluaciones has realizado, el promedio general y cuántos registros faltan por enviar."),
            Triple(recentActivityTarget,
                "Evaluaciones recientes",
                "Aquí aparecerán solamente tus evaluaciones reales. Toca cualquiera para revisar las competencias, los comentarios y los tiempos del encuentro clínico."),
            Triple(fab,
                "Crea una evaluación",
                "Usaremos un alumno de demostración para mostrarte el proceso completo sin afectar tus registros reales.")
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                TutorialManager.advancePhase(ctx) // → PHASE_SETTINGS
                // ── AUTO-NAVEGAR a Ajustes ──────────────────────────────────
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    if (_binding != null && isAdded) {
                        try { findNavController().navigate(R.id.nav_settings) }
                        catch (_: Exception) {}
                    }
                }
                return
            }
            val (view, title, desc) = steps[index]
            overlay.show(
                targetView = view,
                stepNum = TutorialManager.START_STEP_HOME + index,
                title = title, description = desc,
                isLastStep = false,
                onNext = { showStep(index + 1) },
                onSkip = { TutorialManager.skipAll(ctx) }
            )
        }
        showStep(0)
    }

    // ── Dashboard data ────────────────────────────────────────────────────────

    private fun loadDashboardData(evaluadorId: Int) {
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(appCtx)
            db.evaluationDao().getEvaluationsForEvaluatorFlow(evaluadorId).collect { evaluations ->
                val students = db.studentDao().getStudentsForDocente(evaluadorId)
                val studentMap = students.associateBy({ it.idAlumno }, { it.nombreCompleto })
                val demoStudentIds = students.filter { it.matricula == "DEMO001" }
                    .map { it.idAlumno }
                    .toSet()
                val realEvaluations = evaluations.filterNot { it.idAlumno in demoStudentIds }
                val summaryList = realEvaluations.map { eval ->
                    val name = studentMap[eval.idAlumno] ?: "Alumno Desconocido"
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(Date(eval.fechaEvaluacion))
                    EvaluationSummary(eval.idEvaluacion, name, date, eval.calificacionTotal / 10.0)
                }
                val total = realEvaluations.size
                val avg = if (realEvaluations.isEmpty()) 0.0
                          else realEvaluations.map { it.calificacionTotal }.average() / 10.0
                val pending = realEvaluations.count { !it.isSynced }

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.tvStatTotalCount.text = total.toString()
                        binding.tvStatAvgScore.text = String.format(Locale.US, "%.1f", avg)
                        binding.tvStatPendingCount.text = pending.toString()
                        val isTrulyEmpty = realEvaluations.isEmpty()
                        binding.emptyState.visibility = if (isTrulyEmpty) View.VISIBLE else View.GONE
                        binding.rvEvaluations.visibility = if (isTrulyEmpty) View.GONE else View.VISIBLE
                        binding.rvEvaluations.adapter = EvaluationAdapter(summaryList) { evaluation ->
                            if (_binding != null && isAdded) {
                                val bundle = Bundle().apply { putInt("evaluation_id", evaluation.id) }
                                try { findNavController().navigate(R.id.nav_evaluation_detail, bundle) }
                                catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tutorialOverlay?.dismiss(animated = false)
        tutorialOverlay = null
        _binding = null
    }
}
