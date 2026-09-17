package com.example.minicex.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object BiometricHelper {

    private const val PREFS_NAME = "minicex_prefs"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    /**
     * Comprueba el estado de disponibilidad biométrica en el dispositivo.
     */
    fun canAuthenticate(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }

    /**
     * Retorna true si el dispositivo tiene hardware biométrico y el usuario tiene
     * al menos una huella o rostro registrado.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Retorna true si el usuario tiene activado el desbloqueo biométrico en ajustes (por defecto true).
     */
    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    /**
     * Guarda la preferencia del usuario sobre el uso de biometría.
     */
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Muestra el diálogo nativo de BiometricPrompt para autenticar con huella o rostro.
     */
    fun showBiometricPrompt(
        fragment: Fragment,
        title: String = "Desbloquear Mini-CEX",
        subtitle: String = "Confirma tu identidad con tu huella digital o rostro",
        negativeButtonText: String = "Usar contraseña",
        onSuccess: () -> Unit,
        onCancel: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val context = fragment.context ?: return
        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // BiometricPrompt maneja visualmente el intento fallido en el modal
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .build()

        val biometricPrompt = BiometricPrompt(fragment, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
