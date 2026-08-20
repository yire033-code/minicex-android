package com.example.minicex.ui.reports

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.minicex.ui.theme.MiniCexTheme
import com.example.minicex.ui.utils.showError
import com.example.minicex.ui.utils.showInfo
import com.example.minicex.ui.utils.showSuccess

class ReportsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MiniCexTheme {
                    ReportsScreen(
                        fragmentActivity = requireActivity(),
                        onShowError = { msg -> showError(msg) },
                        onShowInfo = { msg -> showInfo(msg) },
                        onShowSuccess = { msg -> showSuccess(msg) },
                    )
                }
            }
        }
    }
}
