package com.example.minicex.ui.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {
    fun show(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        backgroundColor: Int = Color.parseColor("#4F46E5"), // primary
        textColor: Int = Color.WHITE
    ) {
        val snackbar = Snackbar.make(view, message, duration)
        val snackbarView = snackbar.view
        val context = view.context

        // Crear background redondeado programáticamente para estilo flotante premium
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * context.resources.displayMetrics.density // Esquinas redondeadas
            setColor(backgroundColor)
        }
        snackbarView.background = backgroundDrawable

        // Personalizar el texto interno
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.apply {
            setTextColor(textColor)
            textSize = 14f
            maxLines = 3
        }

        // Agregar márgenes para despegarlo de los bordes y hacerlo flotar
        val params = snackbarView.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            val margin = (16 * context.resources.displayMetrics.density).toInt() // 16dp
            params.setMargins(margin, margin, margin, margin)
            snackbarView.layoutParams = params
        }
        
        snackbarView.elevation = 6f
        snackbar.show()
    }
}

fun Fragment.showInfo(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let {
        SnackbarHelper.show(it, message, duration, Color.parseColor("#4F46E5"))
    }
}

fun Fragment.showSuccess(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let {
        SnackbarHelper.show(it, message, duration, Color.parseColor("#10B981"))
    }
}

fun Fragment.showWarning(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let {
        SnackbarHelper.show(it, message, duration, Color.parseColor("#F59E0B"))
    }
}

fun Fragment.showError(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    view?.let {
        SnackbarHelper.show(it, message, duration, Color.parseColor("#EF4444"))
    }
}

fun View.showSuccess(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    SnackbarHelper.show(this, message, duration, Color.parseColor("#10B981"))
}

fun View.showError(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    SnackbarHelper.show(this, message, duration, Color.parseColor("#EF4444"))
}
