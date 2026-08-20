package com.example.minicex.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.UserEntity
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.remote.dto.LoginRequest
import com.example.minicex.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Orb floating animators (kept as references to cancel on destroy)
    private val orbAnimators = mutableListOf<AnimatorSet>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if user session is already active (Auto-Login / Session Persistence)
        val prefs = requireContext().getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
        val evaluadorEmail = prefs.getString("evaluador_email", "")
        if (!evaluadorEmail.isNullOrEmpty()) {
            findNavController().navigate(R.id.action_login_to_home)
            return
        }

        setupEdgeToEdge()
        setupConnectionStatus()
        if (isFirstLaunch) {
            runSplashEntranceAnimation()
            isFirstLaunch = false
        } else {
            runStandardEntranceAnimation()
        }
        startOrbAnimations()
        setupLoginButton()
    }

    // ─────────────────────────────────────────────────────────────
    //  Edge-to-edge & System Bars
    // ─────────────────────────────────────────────────────────────

    private fun setupEdgeToEdge() {
        activity?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }

        // Apply insets to the status chip so it doesn't overlap with the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.statusChip) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top + 12
            v.layoutParams = params
            insets
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Connection Status Indicator
    // ─────────────────────────────────────────────────────────────

    private fun setupConnectionStatus() {
        val isOnline = checkConnectivity()
        updateStatusChip(isOnline)
    }

    private fun updateStatusChip(isOnline: Boolean) {
        val dotDrawable = binding.statusDot.background
        if (dotDrawable is GradientDrawable) {
            val color = if (isOnline) {
                resources.getColor(R.color.login_online, context?.theme)
            } else {
                resources.getColor(R.color.login_offline, context?.theme)
            }
            dotDrawable.setColor(color)
        }
        binding.tvStatusText.text = if (isOnline) "En línea" else "Sin conexión"

        // Pulse the dot
        val pulseAnimator = ObjectAnimator.ofFloat(binding.statusDot, "alpha", 1f, 0.3f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
        }
        pulseAnimator.start()
    }

    private fun checkConnectivity(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ─────────────────────────────────────────────────────────────
    //  Orchestrated Entrance Animations
    // ─────────────────────────────────────────────────────────────

    private fun runSplashEntranceAnimation() {
        // Initially hide all text boxes, form container and status chip
        binding.formContainer.alpha = 0f
        binding.formContainer.translationY = 200f
        binding.statusChip.alpha = 0f
        binding.statusChip.translationX = 80f

        // Hide app name and tagline
        binding.tvAppName.alpha = 0f
        binding.tvAppName.translationY = 40f
        binding.tvTagline.alpha = 0f
        binding.tvTagline.translationY = 30f

        // Hide logo initially for scaling in
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.5f
        binding.ivLogo.scaleY = 0.5f

        // Wait for layout to measure parent height to center the branding container
        binding.brandingContainer.post {
            if (_binding == null) return@post
            val parentHeight = binding.loginRoot.height
            val brandingHeight = binding.brandingContainer.height
            val currentTop = binding.brandingContainer.top
            
            // Calculate translationY needed to center the branding container
            val targetTop = (parentHeight - brandingHeight) / 2
            val startTranslationY = (targetTop - currentTop).toFloat()
            
            binding.brandingContainer.translationY = startTranslationY

            // 1) Animate logo scaling and fading in (Splash entrance)
            binding.ivLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setInterpolator(OvershootInterpolator(1.2f))
                .withEndAction {
                    // 2) Wait 1 second (Splash loading simulation)
                    binding.loginRoot.postDelayed({
                        if (_binding == null) return@postDelayed
                        // 3) Slide branding container to the top
                        binding.brandingContainer.animate()
                            .translationY(0f)
                            .setDuration(900)
                            .setInterpolator(DecelerateInterpolator(1.5f))
                            .withStartAction {
                                // Fade in App name and tagline as it moves
                                binding.tvAppName.animate().alpha(1f).translationY(0f).setDuration(800).start()
                                binding.tvTagline.animate().alpha(1f).translationY(0f).setDuration(800).start()
                            }
                            .withEndAction labelFormReveal@{
                                if (_binding == null) return@labelFormReveal
                                // 4) Slide up the login form card
                                binding.formContainer.animate()
                                    .alpha(1f)
                                    .translationY(0f)
                                    .setDuration(700)
                                    .setInterpolator(DecelerateInterpolator())
                                    .start()

                                // Fade in connection status chip
                                binding.statusChip.animate()
                                    .alpha(1f)
                                    .translationX(0f)
                                    .setDuration(500)
                                    .start()
                            }
                            .start()
                    }, 1000)
                }
                .start()
        }
    }

    private fun runStandardEntranceAnimation() {
        // Initially hide everything
        val views = listOf(
            binding.brandingContainer,
            binding.formContainer,
            binding.statusChip
        )
        views.forEach { it.alpha = 0f }

        // 1) Logo entrance - scale + overshoot (0ms)
        binding.ivLogo.scaleX = 0f
        binding.ivLogo.scaleY = 0f
        binding.ivLogo.alpha = 0f
        binding.ivLogo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

        // 2) App name - slide from bottom + fade (300ms delay)
        binding.tvAppName.alpha = 0f
        binding.tvAppName.translationY = 40f
        binding.tvAppName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 3) Tagline - slide from bottom + fade (500ms delay)
        binding.tvTagline.alpha = 0f
        binding.tvTagline.translationY = 30f
        binding.tvTagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Make branding container visible (children handle their own animations)
        binding.brandingContainer.animate()
            .alpha(1f)
            .setDuration(100)
            .start()

        // 4) Form card - slide from bottom + fade (400ms delay)
        binding.formContainer.translationY = 300f
        binding.formContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(400)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        // 5) Staggered form elements inside the card
        val formElements = listOf(
            binding.tvLoginTitle,
            binding.tvLoginSubtitle,
            binding.tilEmail,
            binding.tilPassword,
            binding.btnLoginContainer,
            binding.tvHelpLink
        )

        formElements.forEachIndexed { index, element ->
            element.alpha = 0f
            element.translationX = 60f
            element.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(450)
                .setStartDelay(700 + (index * 80L))
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        // 6) Status chip - fade in from right
        binding.statusChip.translationX = 80f
        binding.statusChip.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(500)
            .setStartDelay(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 7) Pulse animation for help link (starts after entrance completes)
        binding.tvHelpLink.postDelayed({
            if (_binding != null) {
                val pulseAnim = ObjectAnimator.ofFloat(binding.tvHelpLink, "alpha", 1f, 0.5f, 1f).apply {
                    duration = 2500
                    repeatCount = ObjectAnimator.INFINITE
                }
                pulseAnim.start()
            }
        }, 2000)
    }

    // ─────────────────────────────────────────────────────────────
    //  Floating Orb Animations (infinite ambient motion)
    // ─────────────────────────────────────────────────────────────

    private fun startOrbAnimations() {
        animateOrb(binding.orb1, 20f, -15f, 6000)
        animateOrb(binding.orb2, -25f, 20f, 7500)
        animateOrb(binding.orb3, 15f, 25f, 5500)
    }

    private fun animateOrb(orb: View, dx: Float, dy: Float, duration: Long) {
        val animX = ObjectAnimator.ofFloat(orb, "translationX", 0f, dx, -dx * 0.5f, 0f).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        val animY = ObjectAnimator.ofFloat(orb, "translationY", 0f, dy, -dy * 0.5f, 0f).apply {
            this.duration = (duration * 1.2).toLong()
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        val alphaAnim = ObjectAnimator.ofFloat(orb, "alpha", 0.7f, 1f, 0.6f, 0.8f).apply {
            this.duration = (duration * 0.8).toLong()
            repeatCount = ObjectAnimator.INFINITE
        }

        val animatorSet = AnimatorSet().apply {
            playTogether(animX, animY, alphaAnim)
            start()
        }
        orbAnimators.add(animatorSet)
    }

    // ─────────────────────────────────────────────────────────────
    //  Login Button & Authentication Logic
    // ─────────────────────────────────────────────────────────────

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener { btn ->
            val email = binding.tilEmail.editText?.text?.toString()?.trim() ?: ""
            val password = binding.tilPassword.editText?.text?.toString()?.trim() ?: ""

            // Clear previous errors
            binding.tilEmail.error = null
            binding.tilPassword.error = null

            // Validate with animations
            var hasError = false
            if (email.isEmpty()) {
                binding.tilEmail.error = "Ingresa tu correo"
                shakeView(binding.tilEmail)
                hasError = true
            }
            if (password.isEmpty()) {
                binding.tilPassword.error = "Ingresa tu contraseña"
                shakeView(binding.tilPassword)
                hasError = true
            }
            if (hasError) {
                // Haptic feedback for error
                btn.performHapticFeedback(HapticFeedbackConstants.REJECT)
                return@setOnClickListener
            }

            // Show loading state
            setLoadingState(true)

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())

                // Check connection availability
                val isOnline = checkConnectivity()

                if (isOnline) {
                    try {
                        val prefs = requireContext().getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
                        val response = RetrofitClient.instance.login(LoginRequest(email = email, password = password))
                        if (response.isSuccessful && response.body()?.success == true) {
                            val loginResponse = response.body()!!
                            val serverUser = loginResponse.user!!

                            // Update/Insert local user with fetched details and password
                            val existingUser = db.userDao().getUserByEmail(serverUser.email)
                            if (existingUser != null && existingUser.idUsuario != serverUser.id_usuario) {
                                db.userDao().updateUserIdByEmail(serverUser.email, serverUser.id_usuario)
                            }
                            
                            val localUser = UserEntity(
                                idUsuario = serverUser.id_usuario,
                                nombreCompleto = serverUser.nombre_completo,
                                email = serverUser.email,
                                passwordHash = password, // Stored to allow offline login with correct password next time
                                rol = serverUser.rol
                            )
                            db.userDao().insertUser(localUser)

                            // Save credentials in SharedPreferences
                            prefs.edit()
                                .putInt("evaluador_id", serverUser.id_usuario)
                                .putString("evaluador_nombre", serverUser.nombre_completo)
                                .putString("evaluador_email", serverUser.email)
                                .apply()

                            // Auto-trigger synchronisation immediately
                            try {
                                val repository = com.example.minicex.data.repository.SyncRepository(
                                    db.evaluationDao(),
                                    RetrofitClient.instance,
                                    requireContext()
                                )
                                repository.autoSync()
                            } catch (syncEx: Exception) {
                                android.util.Log.e("LoginFragment", "Error running autoSync during login: ${syncEx.message}")
                            }

                            onLoginSuccess("En línea")
                        } else {
                            val errorMsg = response.body()?.message ?: "Credenciales incorrectas en el servidor."
                            onLoginError(errorMsg)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LoginFragment", "Online login exception: ${e.message}", e)
                        performOfflineLogin(email, password, db)
                    }
                } else {
                    performOfflineLogin(email, password, db)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Loading, Success & Error States
    // ─────────────────────────────────────────────────────────────

    private fun setLoadingState(loading: Boolean) {
        if (loading) {
            binding.btnLogin.text = ""
            binding.btnLogin.isEnabled = false
            binding.loginProgress.visibility = View.VISIBLE

            // Subtle scale-down on the button
            binding.btnLoginContainer.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(200)
                .start()
        } else {
            binding.loginProgress.visibility = View.GONE
            binding.btnLogin.text = "Iniciar Sesión"
            binding.btnLogin.isEnabled = true

            binding.btnLoginContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun onLoginSuccess(mode: String) {
        // Haptic confirmation
        binding.btnLogin.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

        // Success exit animation: fade out everything then navigate
        binding.formContainer.animate()
            .translationY(100f)
            .alpha(0f)
            .setDuration(300)
            .start()

        binding.brandingContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .start()

        binding.loginRoot.postDelayed({
            if (_binding != null) {
                showSuccess("Sesión iniciada ($mode)")
                findNavController().navigate(R.id.action_login_to_home)
            }
        }, 350)
    }

    private fun onLoginError(message: String) {
        setLoadingState(false)
        showError(message)

        // Shake the form card to indicate error
        shakeView(binding.formContainer)

        // Haptic for error
        binding.btnLogin.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    private suspend fun performOfflineLogin(email: String, password: String, db: AppDatabase) {
        val user = db.userDao().getUserByEmail(email)
        if (user != null && user.passwordHash == password) {
            val prefs = requireContext().getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("evaluador_id", user.idUsuario)
                .putString("evaluador_nombre", user.nombreCompleto)
                .putString("evaluador_email", user.email)
                .apply()

            onLoginSuccess("Modo sin conexión")
        } else {
            onLoginError("Credenciales incorrectas o usuario no registrado localmente.")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Utility Animations
    // ─────────────────────────────────────────────────────────────

    private fun shakeView(view: View) {
        val shakeAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.login_shake)
        view.startAnimation(shakeAnim)
    }

    // ─────────────────────────────────────────────────────────────
    //  Lifecycle cleanup
    // ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel all infinite orb animations
        orbAnimators.forEach { it.cancel() }
        orbAnimators.clear()
        _binding = null
    }

    companion object {
        private var isFirstLaunch = true
    }
}
