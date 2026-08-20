package com.example.minicex.ui.utils

import android.app.Activity
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.minicex.R
import com.google.android.material.button.MaterialButton

/**
 * Window-level tutorial overlay — rectangular highlight card floating over the Activity.
 * Safe to dismiss multiple times. Only one instance visible at a time.
 */
class TutorialOverlay(private val activity: Activity) {

    private var rootView: View? = null

    companion object {
        const val TOTAL_STEPS = 23
    }

    fun show(
        targetView: View?,
        stepNum: Int,
        title: String,
        description: String,
        isLastStep: Boolean = false,
        onNext: () -> Unit,
        onSkip: () -> Unit
    ) {
        // Always remove any previous overlay first (prevents stacking)
        dismiss(animated = false)

        val inflater = LayoutInflater.from(activity)
        val overlay = try {
            inflater.inflate(R.layout.view_tutorial_overlay, null)
        } catch (e: Exception) {
            onNext(); return
        }

        // Setup dim + cutout
        val dim = overlay.findViewById<TutorialDimView>(R.id.tutorialDimView)
        if (targetView != null) {
            targetView.post {
                // Pedimos un rectángulo más grande (con "margen" arriba y abajo)
                // para forzar al ScrollView a centrar más el elemento y que no quede
                // escondido bajo un AppBar (header) pegajoso.
                targetView.requestRectangleOnScreen(
                    android.graphics.Rect(0, -300, targetView.width, targetView.height + 300),
                    false // true = scroll instantáneo, false = suave
                )
            }
            
            // Post after overlay is added to window so coordinates are correct
            // Añadimos un pequeño delay para dar tiempo a que termine el scroll
            overlay.postDelayed({
                try {
                    val card = overlay.findViewById<View>(R.id.tutorialCard)
                    val updateCardPosition = {
                        dim.setTargetView(targetView)
                        
                        // Avoid overlaying target view
                        val loc = IntArray(2)
                        targetView.getLocationOnScreen(loc)
                        val targetCenterY = loc[1] + (targetView.height / 2)
                        val screenHeight = activity.resources.displayMetrics.heightPixels
                        
                        // If target is in the bottom half, show card at top; otherwise show at bottom
                        val params = card.layoutParams as FrameLayout.LayoutParams
                        if (targetCenterY > screenHeight / 2) {
                            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            params.topMargin = dp(72)
                            params.bottomMargin = 0
                        } else {
                            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            params.topMargin = 0
                            params.bottomMargin = dp(28)
                        }
                        card.layoutParams = params
                    }
                    
                    if (targetView.isAttachedToWindow && targetView.width > 0) {
                        updateCardPosition()
                    } else {
                        targetView.post {
                            if (targetView.isAttachedToWindow && targetView.width > 0) {
                                updateCardPosition()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }, 250L)
        }

        // Step label
        overlay.findViewById<TextView>(R.id.tvTutorialStep).text =
            "${sectionName(stepNum)}  •  $stepNum/$TOTAL_STEPS"

        // Progress bar
        val pb = overlay.findViewById<ProgressBar>(R.id.pbTutorialProgress)
        pb.max = TOTAL_STEPS
        pb.progress = stepNum

        // Text content
        overlay.findViewById<TextView>(R.id.tvTutorialTitle).text = title
        overlay.findViewById<TextView>(R.id.tvTutorialDesc).text = description

        // Buttons
        val btnNext = overlay.findViewById<MaterialButton>(R.id.btnTutorialNext)
        btnNext.text = if (isLastStep) "Finalizar recorrido" else "Continuar"
        btnNext.setOnClickListener {
            it.isEnabled = false   // prevent double-tap
            dismiss()
            onNext()
        }
        overlay.findViewById<MaterialButton>(R.id.btnTutorialSkip).setOnClickListener {
            dismiss()
            onSkip()
        }

        // Add to Activity window so it floats above everything
        val decorView = activity.window.decorView as FrameLayout
        decorView.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootView = overlay

        // Slide-up card entrance
        val card = overlay.findViewById<View>(R.id.tutorialCard)
        (card.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = minOf(activity.resources.displayMetrics.widthPixels - dp(32), dp(560))
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            card.layoutParams = params
        }
        card.translationY = 600f
        card.alpha = 0f
        card.animate()
            .translationY(0f).alpha(1f)
            .setDuration(350).setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    fun dismiss(animated: Boolean = true) {
        val view = rootView ?: return
        rootView = null
        if (animated) {
            view.animate().alpha(0f).setDuration(160).withEndAction {
                (view.parent as? ViewGroup)?.removeView(view)
            }.start()
        } else {
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    val isShowing get() = rootView != null

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun sectionName(stepNum: Int): String = when (stepNum) {
        in 1..4 -> "INICIO"
        in 5..6 -> "PERFIL Y DATOS"
        in 7..12 -> "DATOS GENERALES"
        in 13..16 -> "RÚBRICA CLÍNICA"
        in 17..18 -> "RETROALIMENTACIÓN"
        19 -> "REVISIÓN"
        else -> "REPORTE FINAL"
    }
}
