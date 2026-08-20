package com.example.minicex.ui.reports

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.minicex.R
import com.example.minicex.data.remote.dto.StudentReportResponse
import com.example.minicex.databinding.BottomSheetReportDetailBinding
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.gson.Gson

/**
 * Bottom sheet that shows a section of the report in full-screen with better detail.
 * Receives the section type and the full report JSON via arguments.
 */
class ReportDetailSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportDetailBinding? = null
    private val binding get() = _binding!!

    private val gson = Gson()
    private var report: StudentReportResponse? = null
    private var sectionType: String = ""

    companion object {
        private const val ARG_SECTION = "section_type"
        private const val ARG_REPORT = "report_json"

        fun newInstance(sectionType: String, reportJson: String): ReportDetailSheet {
            return ReportDetailSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SECTION, sectionType)
                    putString(ARG_REPORT, reportJson)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReportDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read arguments
        sectionType = arguments?.getString(ARG_SECTION) ?: ""
        val json = arguments?.getString(ARG_REPORT) ?: ""
        report = gson.fromJson(json, StudentReportResponse::class.java)

        // Configure dialog to be full-height
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheet = dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog
            val sheet = bottomSheet?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        binding.btnSheetClose.setOnClickListener { dismiss() }

        renderContent()
    }

    private fun renderContent() {
        val r = report ?: return

        when (sectionType) {
            "stats" -> renderStats(r)
            "indices" -> renderIndices(r)
            "evolution" -> renderEvolutionChart(r)
            "radar" -> renderRadarChart(r)
            "complexity" -> renderPieChart(r)
            "times" -> renderBarChart(r)
            "areas" -> renderAreasMejora(r)
            "table" -> renderTable(r)
        }
    }

    // ── Color helper ────────────────────────────────────────────────────────

    private fun color(res: Int): Int = ContextCompat.getColor(requireContext(), res)
    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    // ═══════════════════════════════════════════════════════════════════════
    //  1. STATS (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderStats(report: StudentReportResponse) {
        binding.sheetTitle.text = "Resumen General"
        val idx = report.indices ?: return

        val grid = binding.gridSheetStats
        grid.visibility = View.VISIBLE
        grid.removeAllViews()

        val trendColor = when {
            idx.trend > 0.5 -> color(R.color.status_superior)
            idx.trend < -0.5 -> color(R.color.status_unsatisfactory)
            else -> color(R.color.status_satisfactory)
        }

        val stats = listOf(
            Triple("EVALUACIONES", idx.totalEvaluaciones.toString(), color(R.color.primary)),
            Triple("PROMEDIO GENERAL", "${idx.promedioDisplay}/10", color(R.color.secondary)),
            Triple("EVOLUCIÓN", idx.trendText ?: "—", trendColor),
            Triple("REGULARIDAD", idx.consistenciaText ?: "Sin datos", color(R.color.on_surface)),
        )

        for ((label, value, valueColor) in stats) {
            val card = createDetailCard(label, value, valueColor)
            val lp = card.layoutParams as? GridLayout.LayoutParams
            if (lp != null) {
                lp.width = 0
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card)
        }

        // Additional insight card
        val insight = "Este alumno ha completado ${idx.totalEvaluaciones} evaluaciones " +
                "con un promedio de ${idx.promedioDisplay}/10. " +
                if (idx.trend > 0.5) "Sus calificaciones muestran una mejora constante." else
                    if (idx.trend < -0.5) "Sus calificaciones recientes requieren seguimiento." else
                        "Sus calificaciones se mantienen estables."

        addDetailText(insight)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. INDICES (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderIndices(report: StudentReportResponse) {
        binding.sheetTitle.text = "Resumen del Desempeño"
        val idx = report.indices ?: return

        val grid = binding.gridSheetStats
        grid.visibility = View.VISIBLE
        grid.removeAllViews()

        val progresoColor = when {
            idx.progreso > 2 -> color(R.color.status_superior)
            idx.progreso < -2 -> color(R.color.status_unsatisfactory)
            else -> color(R.color.status_satisfactory)
        }
        val signo = if (idx.progreso > 0) "+" else ""

        val items = listOf(
            Triple("Principal fortaleza", idx.competenciaFuerte ?: "—", color(R.color.status_superior)),
            Triple("Área prioritaria", idx.competenciaDebil ?: "—", color(R.color.status_unsatisfactory)),
            Triple("Cambio desde el inicio", "${idx.progresoText} ($signo${"%.1f".format(idx.progreso)})", progresoColor),
            Triple("Evaluaciones realizadas", idx.totalEvaluaciones.toString(), color(R.color.primary)),
        )

        for ((label, value, valueColor) in items) {
            val card = createDetailCard(label, value, valueColor)
            val lp = card.layoutParams as? GridLayout.LayoutParams
            if (lp != null) {
                lp.width = 0
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card)
        }

        // Trend detail
        val trendDetail = "Evolución a lo largo de las evaluaciones: ${idx.trendText ?: "Estable"}."
        addDetailText(trendDetail)

        // Regularidad del desempeño
        val consisDetail = "Regularidad entre evaluaciones: ${idx.consistenciaText ?: "Sin datos"}. " +
                "Indica si sus calificaciones se mantienen parecidas o presentan cambios importantes."
        addDetailText(consisDetail)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. EVOLUTION CHART (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderEvolutionChart(report: StudentReportResponse) {
        binding.sheetTitle.text = "Evolución de Calificaciones"
        val evals = report.evaluaciones ?: return
        if (evals.isEmpty()) {
            addDetailText("No hay evaluaciones registradas.")
            return
        }

        // Show chart card
        binding.cardSheetChart.visibility = View.VISIBLE
        binding.tvSheetChartLabel.visibility = View.VISIBLE
        binding.tvSheetChartLabel.text = "CAMBIO DE LAS CALIFICACIONES CON EL TIEMPO"

        val chart = LineChart(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.frameSheetChart.removeAllViews()
        binding.frameSheetChart.addView(chart)

        val entries = evals.mapIndexed { i, e ->
            Entry(i.toFloat(), (e.calificacionTotal / 10.0).toFloat())
        }
        val labels = evals.mapIndexed { i, e ->
            if (e.fechaEvaluacion.length >= 10) e.fechaEvaluacion.substring(5, 10) else "#${i + 1}"
        }

        val dataSet = LineDataSet(entries, "Calificación /10").apply {
            color = color(R.color.primary)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 10f
            lineWidth = 3f
            setCircleColor(color(R.color.secondary))
            circleRadius = 6f
            setCircleHoleColor(color(R.color.secondary))
            setDrawFilled(true)
            fillColor = color(R.color.primary)
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(true)
        }

        chart.data = LineData(dataSet).apply { setValueTextColor(color(R.color.text_secondary)) }
        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.textColor = color(R.color.text_secondary)
            setTouchEnabled(true)
            setPinchZoom(true)
            setScaleEnabled(true)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = color(R.color.text_secondary)
                gridColor = color(R.color.divider)
                granularity = 1f
                setLabelRotationAngle(45f)
            }
            axisLeft.apply {
                textColor = color(R.color.text_secondary)
                gridColor = color(R.color.divider)
                axisMinimum = 0f
                axisMaximum = 10f
            }
            axisRight.isEnabled = false
            animateX(800)
            invalidate()
        }

        // Add scores table below chart
        addDetailText("Puntajes por evaluación:")
        evals.forEachIndexed { i, e ->
            val score = (e.calificacionTotal / 10.0).toFloat()
            addDetailRow(
                "#${i + 1} — ${e.fechaEvaluacion.take(10)}",
                "${score}/10",
                when {
                    score >= 8f -> color(R.color.status_superior)
                    score >= 5f -> color(R.color.status_satisfactory)
                    else -> color(R.color.status_unsatisfactory)
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. RADAR CHART (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderRadarChart(report: StudentReportResponse) {
        binding.sheetTitle.text = "Competencias — Radar Completo"
        val comps = report.competencias ?: return
        if (comps.isEmpty()) {
            addDetailText("No hay datos de competencias.")
            return
        }

        binding.cardSheetChart.visibility = View.VISIBLE
        binding.tvSheetChartLabel.visibility = View.VISIBLE
        binding.tvSheetChartLabel.text = "RADAR DE COMPETENCIAS"

        val chart = RadarChart(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.frameSheetChart.removeAllViews()
        binding.frameSheetChart.addView(chart)

        val entries = comps.map { RadarEntry(it.promedio.toFloat()) }
        val labels = comps.map { it.competencia }

        val dataSet = RadarDataSet(entries, "Promedio por competencia").apply {
            color = color(R.color.primary)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 10f
            setDrawFilled(true)
            fillColor = color(R.color.primary)
            fillAlpha = 60
            lineWidth = 2f
            setDrawValues(true)
        }

        chart.data = RadarData(dataSet).apply { setValueTextColor(color(R.color.text_secondary)) }
        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.textColor = color(R.color.text_secondary)
            setTouchEnabled(true)
            webColor = color(R.color.divider)
            webColorInner = color(R.color.divider)
            webLineWidth = 1f
            webLineWidthInner = 1f
            yAxis.apply {
                textColor = color(R.color.text_secondary)
                axisMinimum = 0f
                axisMaximum = 9f
                setLabelCount(4, false)
            }
            xAxis.apply {
                textColor = color(R.color.on_surface)
                valueFormatter = IndexAxisValueFormatter(labels)
                textSize = 10f
            }
            animateXY(600, 600)
            invalidate()
        }

        // Detail: each competency score
        addDetailText("Puntajes por competencia:")
        comps.sortedByDescending { it.promedio }.forEach { c ->
            val avgColor = when {
                c.promedio >= 7f -> color(R.color.status_superior)
                c.promedio >= 4f -> color(R.color.status_satisfactory)
                else -> color(R.color.status_unsatisfactory)
            }
            addDetailRow("${c.competencia} (${c.count} eval.)", "${c.promedio}/9", avgColor)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. PIE CHART — Complexity (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderPieChart(report: StudentReportResponse) {
        binding.sheetTitle.text = "Complejidad de Casos"
        val complejidad = report.complejidad ?: return
        if (complejidad.isEmpty()) {
            addDetailText("No hay datos de complejidad.")
            return
        }

        binding.cardSheetChart.visibility = View.VISIBLE
        binding.tvSheetChartLabel.visibility = View.VISIBLE
        binding.tvSheetChartLabel.text = "DISTRIBUCIÓN POR COMPLEJIDAD"

        val chart = PieChart(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.frameSheetChart.removeAllViews()
        binding.frameSheetChart.addView(chart)

        val map = mapOf("Baja" to 0, "Media" to 0, "Alta" to 0).toMutableMap()
        complejidad.forEach { map[it.complejidad] = (map[it.complejidad] ?: 0) + it.count }

        val pieEntries = map.entries.filter { it.value > 0 }.map { PieEntry(it.value.toFloat(), it.key) }
        val pieColors = listOf(
            color(R.color.status_superior),
            color(R.color.status_satisfactory),
            color(R.color.status_unsatisfactory),
        )

        val dataSet = PieDataSet(pieEntries, "").apply {
            colors = pieColors
            valueTextColor = color(R.color.on_surface)
            valueTextSize = 13f
            valueFormatter = PercentFormatter(chart)
            sliceSpace = 3f
            selectionShift = 8f
        }

        chart.data = PieData(dataSet).apply {
            setValueTextColor(color(R.color.on_surface))
            setValueTextSize(13f)
        }
        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.textColor = color(R.color.text_secondary)
            legend.textSize = 12f
            isDrawHoleEnabled = true
            holeRadius = 45f
            setHoleColor(color(R.color.card_bg))
            setCenterText("Complejidad")
            setCenterTextColor(color(R.color.on_surface))
            setCenterTextSize(13f)
            setUsePercentValues(true)
            setTouchEnabled(true)
            animateX(800)
            invalidate()
        }

        // Detail breakdown
        val total = map.values.sum()
        addDetailText("Distribución detallada:")
        listOf("Baja" to color(R.color.status_superior),
            "Media" to color(R.color.status_satisfactory),
            "Alta" to color(R.color.status_unsatisfactory))
            .forEach { (tipo, col) ->
                val count = map[tipo] ?: 0
                val pct = if (total > 0) (count * 100.0 / total) else 0.0
                addDetailRow(tipo, "$count casos (${"%.1f".format(pct)}%)", col)
            }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. BAR CHART — Times (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderBarChart(report: StudentReportResponse) {
        binding.sheetTitle.text = "Tiempos de Evaluación"
        val evals = report.evaluaciones ?: return
        if (evals.isEmpty()) {
            addDetailText("No hay evaluaciones registradas.")
            return
        }

        binding.cardSheetChart.visibility = View.VISIBLE
        binding.tvSheetChartLabel.visibility = View.VISIBLE
        binding.tvSheetChartLabel.text = "OBSERVACIÓN vs FEEDBACK (minutos)"

        val chart = BarChart(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.frameSheetChart.removeAllViews()
        binding.frameSheetChart.addView(chart)

        val obsEntries = evals.mapIndexed { i, e -> BarEntry(i.toFloat(), e.tiempoObservacion.toFloat()) }
        val fbkEntries = evals.mapIndexed { i, e -> BarEntry(i.toFloat(), e.tiempoFeedback.toFloat()) }
        val labels = evals.mapIndexed { i, _ -> "#${i + 1}" }

        val obsSet = BarDataSet(obsEntries, "Observación").apply {
            color = color(R.color.primary)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 9f
        }
        val fbkSet = BarDataSet(fbkEntries, "Feedback").apply {
            color = color(R.color.secondary)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 9f
        }

        chart.data = BarData(obsSet, fbkSet).apply {
            barWidth = 0.3f
            setValueTextColor(color(R.color.text_secondary))
            setValueTextSize(9f)
        }
        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.textColor = color(R.color.text_secondary)
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = color(R.color.text_secondary)
                gridColor = color(R.color.divider)
                granularity = 1f
            }
            axisLeft.apply {
                textColor = color(R.color.text_secondary)
                gridColor = color(R.color.divider)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }

        // Average times
        val avgObs = evals.map { it.tiempoObservacion }.average()
        val avgFbk = evals.map { it.tiempoFeedback }.average()
        addDetailText("Promedios generales:")
        addDetailRow("Observación", "${"%.1f".format(avgObs)} min", color(R.color.primary))
        addDetailRow("Feedback", "${"%.1f".format(avgFbk)} min", color(R.color.secondary))
        addDetailRow("Total por eval.", "${"%.1f".format(avgObs + avgFbk)} min", color(R.color.on_surface))
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  7. AREAS DE MEJORA (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderAreasMejora(report: StudentReportResponse) {
        binding.sheetTitle.text = "Áreas de Mejora — Detalle"
        val areas = report.indices?.topAreasMejora ?: emptyMap()

        if (areas.isEmpty()) {
            addDetailText("Sin áreas de mejora registradas.")
            return
        }

        // Show as chips in the chip group
        val chipGroup = binding.chipGroupSheet
        chipGroup.visibility = View.VISIBLE
        chipGroup.removeAllViews()

        val sortedAreas = areas.entries.sortedByDescending { it.value }
        val maxFreq = sortedAreas.first().value

        sortedAreas.forEach { (word, freq) ->
            val chip = Chip(requireContext()).apply {
                text = "$word ($freq)"
                isClickable = false
                isCheckable = false
                val isTop = freq >= maxFreq * 0.7
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    color(if (isTop) R.color.accent_soft else R.color.card_bg)
                )
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    color(if (isTop) R.color.primary else R.color.card_border)
                )
                chipStrokeWidth = 1f
                setTextColor(color(R.color.primary))
                textSize = 14f
                chipStartPadding = dp(16).toFloat()
                chipEndPadding = dp(16).toFloat()
                chipMinHeight = dp(38).toFloat()
            }
            chipGroup.addView(chip)
        }

        // Frequency detail
        addDetailText("Veces que aparece cada tema en la retroalimentación:")
        sortedAreas.forEach { (word, freq) ->
            val barContainer = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }

            val label = TextView(requireContext()).apply {
                text = word
                setTextColor(color(R.color.on_surface))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val freqText = TextView(requireContext()).apply {
                text = "$freq×"
                setTextColor(color(R.color.primary))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            }

            barContainer.addView(label)
            barContainer.addView(freqText)
            binding.layoutSheetDetails.addView(barContainer)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  8. TABLE (expanded)
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderTable(report: StudentReportResponse) {
        binding.sheetTitle.text = "Detalle de Evaluaciones"
        val evals = report.evaluaciones ?: return
        if (evals.isEmpty()) {
            addDetailText("No hay evaluaciones registradas.")
            return
        }

        evals.forEachIndexed { i, ev ->
            val score = (ev.calificacionTotal / 10.0).toFloat()
            val scoreColor = when {
                score >= 8f -> color(R.color.status_superior)
                score >= 5f -> color(R.color.status_satisfactory)
                else -> color(R.color.status_unsatisfactory)
            }

            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(8)) }
                radius = 14f
                cardElevation = 1f
                setCardBackgroundColor(color(R.color.card_bg))
                strokeColor = color(R.color.card_border)
                strokeWidth = 1
                setContentPadding(dp(16), dp(14), dp(16), dp(14))
            }

            val ll = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Header row
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            headerRow.addView(TextView(requireContext()).apply {
                text = "#${i + 1} — ${ev.fechaEvaluacion.take(10)}"
                setTextColor(color(R.color.on_surface))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            headerRow.addView(TextView(requireContext()).apply {
                text = "${score}/10"
                setTextColor(scoreColor)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            })
            ll.addView(headerRow)

            // Divider
            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, dp(8), 0, dp(8)) }
                setBackgroundColor(color(R.color.divider))
            }
            ll.addView(divider)

            // Details grid
            val detailItems = listOf(
                "Evaluador" to (ev.evaluadorNombre ?: "—"),
                "Entorno" to ev.entornoClinico,
                "Paciente" to ev.tipoPaciente,
                "Asunto" to ev.asuntoPrincipal,
                "Complejidad" to ev.complejidad,
                "T.Observación" to "${ev.tiempoObservacion} min",
                "T.Feedback" to "${ev.tiempoFeedback} min",
            )

            val grid = GridLayout(requireContext()).apply {
                columnCount = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            detailItems.forEach { (label, value) ->
                val labelTv = TextView(requireContext()).apply {
                    text = label
                    setTextColor(color(R.color.text_secondary))
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(2), dp(2), dp(8), dp(2))
                }
                val valueTv = TextView(requireContext()).apply {
                    text = value
                    setTextColor(color(R.color.on_surface))
                    textSize = 13f
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                }
                grid.addView(labelTv)
                grid.addView(valueTv)
            }

            ll.addView(grid)

            // Rubric details
            if (ev.detalles.isNotEmpty()) {
                val rubDivider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(0, dp(8), 0, dp(8)) }
                    setBackgroundColor(color(R.color.divider))
                }
                ll.addView(rubDivider)

                val rubricTitle = TextView(requireContext()).apply {
                    text = "Rúbricas"
                    setTextColor(color(R.color.primary))
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    letterSpacing = 0.05f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, dp(4)) }
                }
                ll.addView(rubricTitle)

                ev.detalles.forEach { d ->
                    val rubRow = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(2), 0, dp(2))
                    }
                    rubRow.addView(TextView(requireContext()).apply {
                        text = "• ${d.competencia}: "
                        setTextColor(color(R.color.text_secondary))
                        textSize = 12f
                    })
                    rubRow.addView(TextView(requireContext()).apply {
                        text = "${d.puntaje}/9"
                        setTextColor(color(R.color.status_superior))
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                    })
                    ll.addView(rubRow)
                }
            }

            card.addView(ll)
            binding.layoutSheetDetails.addView(card)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createDetailCard(label: String, value: String, valueColor: Int): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            radius = 14f
            cardElevation = 1f
            setCardBackgroundColor(color(R.color.card_bg))
            strokeColor = color(R.color.card_border)
            strokeWidth = 1
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(requireContext()).apply {
                    text = label
                    setTextColor(color(R.color.text_secondary))
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    letterSpacing = 0.05f
                })
                addView(TextView(requireContext()).apply {
                    text = value
                    textSize = 24f
                    setTextColor(valueColor)
                    setTypeface(null, Typeface.BOLD)
                })
            })
        }
    }

    private fun addDetailText(text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(color(R.color.text_secondary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(4)) }
        }
        binding.layoutSheetDetails.addView(tv)
    }

    private fun addDetailRow(label: String, value: String, valueColor: Int) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(2)) }
        }

        row.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(color(R.color.text_secondary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(valueColor)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        })

        binding.layoutSheetDetails.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
