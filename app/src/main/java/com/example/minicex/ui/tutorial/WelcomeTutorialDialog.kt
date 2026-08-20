package com.example.minicex.ui.tutorial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.DialogFragment
import com.example.minicex.R
import com.example.minicex.ui.utils.TutorialManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.TextView

/**
 * WelcomeTutorialDialog — Premium welcome dialog shown on first login.
 * Gives the user the choice to start or skip the interactive tutorial.
 */
class WelcomeTutorialDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_tutorial_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Transparent background so the rounded card shows correctly
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Entrance animation for the emoji icon
        val tvEmoji = view.findViewById<TextView>(R.id.tvTutorialEmoji)
        tvEmoji?.apply {
            scaleX = 0.2f
            scaleY = 0.2f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }

        val btnStart = view.findViewById<Button>(R.id.btnStartTutorial)
        val btnSkip = view.findViewById<Button>(R.id.btnSkipTutorial)

        btnStart?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            TutorialManager.startTutorial(requireContext())
            dismiss()
            // Notify the parent fragment to start Phase 1 now
            (parentFragment as? TutorialHost)?.onTutorialStarted()
                ?: (activity as? TutorialHost)?.onTutorialStarted()
        }

        btnSkip?.setOnClickListener {
            TutorialManager.skipAll(requireContext())
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Make the dialog full-width with rounded corners
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Interface implemented by the host Fragment that wants to be notified when the tutorial starts. */
    interface TutorialHost {
        fun onTutorialStarted()
    }

    companion object {
        const val TAG = "WelcomeTutorialDialog"
        fun newInstance() = WelcomeTutorialDialog()
    }
}
