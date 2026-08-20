package com.example.minicex.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import com.example.minicex.ui.utils.showInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.repository.SyncRepository
import com.example.minicex.databinding.FragmentSettingsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read active session info from SharedPreferences
        val prefs = requireContext().getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("evaluador_nombre", "Docente") ?: "Docente"
        val email = prefs.getString("evaluador_email", "correo@upe.edu.mx") ?: "correo@upe.edu.mx"
        
        // Fill profile details
        binding.tvUserName.text = name
        binding.tvUserEmail.text = email
        binding.tvAvatarText.text = if (name.isNotEmpty()) name.take(1).uppercase(Locale.getDefault()) else "D"
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        binding.textSettings.text =
            "MINI-CEX v$versionName • Offline-first\n\nGrupo Educativo Siglo XXI\n© 2026"

        // Manual sync click handler
        binding.btnManualSync.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            val button = binding.btnManualSync
            button.isEnabled = false
            button.text = "Sincronizando..."
            val appContext = requireContext().applicationContext
            
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val api = RetrofitClient.instance
                    val repository = SyncRepository(db.evaluationDao(), api, appContext)
                    repository.autoSync(isManual = true)
                    
                    if (_binding != null) {
                        showSuccess("Sincronización completada con éxito.")
                    }
                } catch (e: Exception) {
                    if (_binding != null) {
                        showError("Fallo al sincronizar: ${e.message}")
                    }
                } finally {
                    if (_binding != null) {
                        button.isEnabled = true
                        button.text = "Sincronizar Datos Ahora"
                    }
                }
            }
        }
 
        // Logout click handler
        binding.btnLogout.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            prefs.edit().clear().apply()

            val appContext = requireContext().applicationContext
            // NOT clearing the database anymore so offline data (and users) persists for other sessions.
            // If they want to wipe, they can uninstall the app.

            showInfo("Sesión cerrada")
            // Navigate back to login screen, popping the backstack
            findNavController().navigate(R.id.nav_login)
        }

        // Entrance animation
        val profileCard = binding.tvUserName.parent.parent as? View
        val animatedViews = listOfNotNull(
            binding.tvSettingsTitle, profileCard,
            binding.btnManualSync, binding.btnLogout, binding.textSettings
        )
        animatedViews.forEachIndexed { index, v ->
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f)
                .setDuration(450).setStartDelay(index * 80L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }

        // ── Tutorial Phase 2: Settings ─────────────────────────────────────────
        view.postDelayed({ checkAndShowSettingsTutorial() }, 700)
    }

    private fun checkAndShowSettingsTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx)
            != com.example.minicex.ui.utils.TutorialManager.PHASE_SETTINGS) return

        val profileCard = binding.tvUserName.parent.parent as? View ?: return
        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())

        val steps = listOf(
            Triple(profileCard,
                "Tu perfil",
                "Aquí puedes confirmar el nombre, correo y tipo de usuario con el que estás realizando las evaluaciones."),
            Triple(binding.btnManualSync as android.view.View,
                "Enviar datos pendientes",
                "Toca este botón para enviar las evaluaciones guardadas cuando tengas conexión a internet.")
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                com.example.minicex.ui.utils.TutorialManager.advancePhase(ctx) // → PHASE_STEP1
                // ── AUTO-NAVEGAR al formulario de evaluación ───────────────────────────
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    if (_binding != null && isAdded) {
                        try { findNavController().navigate(R.id.nav_evaluation) }
                        catch (_: Exception) {}
                    }
                }
                return
            }
            val (view, title, desc) = steps[index]
            overlay.show(
                targetView = view,
                stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_SETTINGS + index,
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
