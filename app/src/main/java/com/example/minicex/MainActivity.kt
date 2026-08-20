package com.example.minicex

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.minicex.databinding.ActivityMainBinding

import androidx.lifecycle.lifecycleScope
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.repository.SyncRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.graphics.Color
import android.util.Log


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var connectivityManager: ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            triggerSync()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet && isValidated) {
                triggerSync()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread {
                updateNetworkStatusPill(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Registrar callback de conectividad
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Determinar estado de red inicial
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isInitiallyOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateNetworkStatusPill(isInitiallyOnline)
        
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // Iniciar Sincronización Automática al arrancar
        initAutoSync()

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.appBarMain.fab?.setOnClickListener { view ->
            navController.navigate(R.id.nav_evaluation)
        }

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home, R.id.nav_evaluation, R.id.nav_settings, R.id.nav_reports
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home, R.id.nav_evaluation, R.id.nav_reports
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLogin = destination.id == R.id.nav_login
            val isFocusedFlow = destination.id == R.id.nav_evaluation ||
                destination.id == R.id.nav_results ||
                destination.id == R.id.nav_evaluation_detail

            if (isLogin) {
                supportActionBar?.hide()
                binding.navView?.visibility = View.GONE
                binding.appBarMain.fab?.hide()
                binding.appBarMain.contentMain.bottomNavView?.visibility = View.GONE
                
                binding.mainOrb1?.visibility = View.GONE
                binding.mainOrb2?.visibility = View.GONE
                binding.mainOrb3?.visibility = View.GONE
                binding.activityContainer.background = null
                stopMainOrbAnimations()
            } else {
                if (isFocusedFlow) supportActionBar?.hide() else supportActionBar?.show()
                binding.navView?.visibility = View.VISIBLE
                if (destination.id == R.id.nav_home) {
                    binding.appBarMain.fab?.show()
                } else {
                    binding.appBarMain.fab?.hide()
                }
                binding.appBarMain.contentMain.bottomNavView?.visibility =
                    if (isFocusedFlow) View.GONE else View.VISIBLE
                
                binding.mainOrb1?.visibility = View.VISIBLE
                binding.mainOrb2?.visibility = View.VISIBLE
                binding.mainOrb3?.visibility = View.VISIBLE
                binding.activityContainer.setBackgroundResource(R.drawable.app_gradient_bg)
                if (orbAnimators.isEmpty()) {
                    startMainOrbAnimations()
                }
            }
        }
    }

    private val orbAnimators = mutableListOf<android.animation.AnimatorSet>()

    private fun startMainOrbAnimations() {
        animateOrb(binding.mainOrb1, 30f, -20f, 7000)
        animateOrb(binding.mainOrb2, -35f, 30f, 8500)
        animateOrb(binding.mainOrb3, 20f, 35f, 6500)
    }

    private fun animateOrb(orb: View?, dx: Float, dy: Float, duration: Long) {
        if (orb == null) return
        val animX = android.animation.ObjectAnimator.ofFloat(orb, "translationX", 0f, dx, -dx * 0.5f, 0f).apply {
            this.duration = duration
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        val animY = android.animation.ObjectAnimator.ofFloat(orb, "translationY", 0f, dy, -dy * 0.5f, 0f).apply {
            this.duration = (duration * 1.2).toLong()
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        val alphaAnim = android.animation.ObjectAnimator.ofFloat(orb, "alpha", 0.6f, 0.9f, 0.5f, 0.7f).apply {
            this.duration = (duration * 0.8).toLong()
            repeatCount = android.animation.ObjectAnimator.INFINITE
        }

        val animatorSet = android.animation.AnimatorSet().apply {
            playTogether(animX, animY, alphaAnim)
            start()
        }
        orbAnimators.add(animatorSet)
    }

    private fun stopMainOrbAnimations() {
        orbAnimators.forEach { it.cancel() }
        orbAnimators.clear()
    }

    fun triggerSync() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@MainActivity)
            val apiService = RetrofitClient.instance
            val repository = SyncRepository(database.evaluationDao(), apiService, this@MainActivity)
            
            val isReachable = isServerReachable()
            updateNetworkStatusPill(isReachable)
            
            if (isReachable) {
                try {
                    repository.autoSync()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Sync failed: ${e.message}")
                }
            }
        }
    }

    suspend fun isServerReachable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val address = java.net.InetAddress.getByName(BuildConfig.MINICEX_API_HOST)
                address.hostAddress != null && address.hostAddress.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun initAutoSync() {
        triggerSync()
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(300000) // 5 minutes
                val prefs = getSharedPreferences("minicex_prefs", MODE_PRIVATE)
                val evaluadorEmail = prefs.getString("evaluador_email", "")
                if (!evaluadorEmail.isNullOrEmpty()) {
                    Log.d("MainActivity", "Iniciando sincronización periódica automática...")
                    triggerSync()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        // Using findViewById because NavigationView exists in different layout files
        // between w600dp and w1240dp
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            // The navigation drawer already has the items including the items in the overflow menu
            // We only inflate the overflow menu if the navigation drawer isn't visible
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMainOrbAnimations()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateNetworkStatusPill(isOnline: Boolean) {
        if (isOnline) {
            binding.appBarMain.connectionIndicatorDot?.background?.setTint(Color.parseColor("#10B981"))
            binding.appBarMain.connectionIndicatorText?.text = "En Línea"
        } else {
            binding.appBarMain.connectionIndicatorDot?.background?.setTint(Color.parseColor("#EF4444"))
            binding.appBarMain.connectionIndicatorText?.text = "Sin Conexión"
        }
    }
}
