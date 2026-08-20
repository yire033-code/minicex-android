package com.example.minicex.ui.reports

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.minicex.R
import com.example.minicex.data.remote.dto.ReportCompetencia
import com.example.minicex.data.remote.dto.ReportEvaluation
import com.example.minicex.data.remote.dto.StudentSummaryItem
import com.example.minicex.databinding.ItemStudentSummaryBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

class TeacherSummaryAdapter(
    private val items: List<StudentSummaryItem>,
    private val onItemClick: (StudentSummaryItem) -> Unit
) : RecyclerView.Adapter<TeacherSummaryAdapter.ViewHolder>() {

    private val expandedItems = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStudentSummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemStudentSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StudentSummaryItem, position: Int) {
            val ctx = binding.root.context
            val isExpanded = position in expandedItems

            binding.tvStudentName.text = item.nombreCompleto
            binding.tvMatricula.text = "MAT. ${item.matricula}"

            val idx = item.indices
            binding.tvScore.text = "${idx.promedioDisplay}/10"
            binding.tvEvalCount.text = "${idx.totalEvaluaciones} eval"

            // Trend text + color
            val trendColor = when {
                idx.trend > 0.5 -> ContextCompat.getColor(ctx, R.color.status_superior)
                idx.trend < -0.5 -> ContextCompat.getColor(ctx, R.color.status_unsatisfactory)
                else -> ContextCompat.getColor(ctx, R.color.status_satisfactory)
            }
            val trendArrow = when {
                idx.trend > 0.5 -> "\u2191 "
                idx.trend < -0.5 -> "\u2193 "
                else -> "\u2192 "
            }
            binding.tvTrend.text = "$trendArrow${idx.trendText ?: "Estable"}"
            binding.tvTrend.setTextColor(trendColor)
            binding.tvTrend.setTypeface(null, Typeface.BOLD)

            // Progress text + color
            val progressColor = when {
                idx.progreso > 2 -> ContextCompat.getColor(ctx, R.color.status_superior)
                idx.progreso < -2 -> ContextCompat.getColor(ctx, R.color.status_unsatisfactory)
                else -> ContextCompat.getColor(ctx, R.color.status_satisfactory)
            }
            val sign = if (idx.progreso > 0) "+" else ""
            binding.tvProgress.text = "Cambio desde el inicio: $sign${"%.1f".format(idx.progreso)}"
            binding.tvProgress.setTextColor(progressColor)
            binding.tvProgress.setTypeface(null, Typeface.BOLD)

            // Score card color
            val scoreColor = when {
                idx.promedioDisplay >= 8.0 -> ContextCompat.getColor(ctx, R.color.status_superior)
                idx.promedioDisplay >= 5.0 -> ContextCompat.getColor(ctx, R.color.status_satisfactory)
                else -> ContextCompat.getColor(ctx, R.color.status_unsatisfactory)
            }
            binding.cardScore.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.accent_soft)
            )
            binding.tvScore.setTextColor(scoreColor)

            // ── Expandable detail section ─────────────────────────────────
            val detailContainer = binding.detailContainer
            if (isExpanded) {
                detailContainer.visibility = View.VISIBLE
                buildDetail(detailContainer, item, ctx)
            } else {
                detailContainer.visibility = View.GONE
            }

            // Toggle expand on click (short vs long: short opens detail, long opens full report)
            binding.root.setOnClickListener {
                if (isExpanded) {
                    expandedItems.remove(position)
                    detailContainer.visibility = View.GONE
                } else {
                    expandedItems.add(position)
                    buildDetail(detailContainer, item, ctx)
                    detailContainer.visibility = View.VISIBLE
                }
                notifyItemChanged(position)
            }
            binding.root.setOnLongClickListener {
                onItemClick(item)
                true
            }
        }

        private fun buildDetail(container: LinearLayout, item: StudentSummaryItem, ctx: android.content.Context) {
            container.removeAllViews()
            val idx = item.indices

            // Strongest / weakest competency
            addDetailRow(container, "Principal fortaleza:", idx.competenciaFuerte ?: "—",
                ContextCompat.getColor(ctx, R.color.status_superior), ctx)
            addDetailRow(container, "Área prioritaria:", idx.competenciaDebil ?: "—",
                ContextCompat.getColor(ctx, R.color.status_unsatisfactory), ctx)
            addDetailRow(container, "Regularidad del desempeño:", idx.consistenciaText ?: "Sin datos",
                ContextCompat.getColor(ctx, R.color.on_surface), ctx)

            // Competency averages
            val comps = item.competencias ?: emptyList()
            if (comps.isNotEmpty()) {
                addSectionTitle(container, "COMPETENCIAS", ctx)
                for (c in comps) {
                    val compColor = when {
                        c.promedio >= 7.0 -> ContextCompat.getColor(ctx, R.color.status_superior)
                        c.promedio >= 4.0 -> ContextCompat.getColor(ctx, R.color.status_satisfactory)
                        else -> ContextCompat.getColor(ctx, R.color.status_unsatisfactory)
                    }
                    addDetailRow(container, c.competencia, "${c.promedio}/9 (${
                        if (c.count > 0) "${c.promedio * 10 / 9}/10" else "—"
                    })", compColor, ctx)
                }
            }

            // Evaluations list (compact)
            val evals = item.evaluaciones ?: emptyList()
            if (evals.isNotEmpty()) {
                addSectionTitle(container, "EVALUACIONES (${evals.size})", ctx)
                for (ev in evals) {
                    val evScore = (ev.calificacionTotal / 10.0).toFloat()
                    val evColor = when {
                        evScore >= 8f -> ContextCompat.getColor(ctx, R.color.status_superior)
                        evScore >= 5f -> ContextCompat.getColor(ctx, R.color.status_satisfactory)
                        else -> ContextCompat.getColor(ctx, R.color.status_unsatisfactory)
                    }
                    addDetailRow(container,
                        "${ev.fechaEvaluacion.take(10)} — ${ev.asuntoPrincipal.take(30)}",
                        "${evScore}/10", evColor, ctx)
                }
            }

            // Areas de mejora
            val areas = idx.topAreasMejora ?: emptyMap()
            if (areas.isNotEmpty()) {
                addSectionTitle(container, "ÁREAS DE MEJORA", ctx)
                val chipGroup = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                areas.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .forEach { (word, freq) ->
                        val chip = Chip(ctx).apply {
                            text = "$word ($freq)"
                            isClickable = false
                            isCheckable = false
                            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(ctx, R.color.accent_soft)
                            )
                            chipStrokeColor = android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(ctx, R.color.primary)
                            )
                            chipStrokeWidth = 1f
                            setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                            textSize = 12f
                            chipStartPadding = 10f
                            chipEndPadding = 10f
                            chipMinHeight = 28f
                        }
                        chipGroup.addView(chip)
                    }
                container.addView(chipGroup)
            }

            // Hint for full detail
            val hint = TextView(ctx).apply {
                text = "Mantén presionado para ver detalle completo"
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(8, ctx), 0, 0) }
            }
            container.addView(hint)
        }

        private fun addSectionTitle(container: LinearLayout, title: String, ctx: android.content.Context) {
            val tv = TextView(ctx).apply {
                text = title
                setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.05f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(8, ctx), 0, dp(2, ctx)) }
            }
            container.addView(tv)
        }

        private fun addDetailRow(container: LinearLayout, label: String, value: String, valueColor: Int, ctx: android.content.Context) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(1, ctx), 0, dp(1, ctx)) }
            }
            row.addView(TextView(ctx).apply {
                text = label
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f
                )
            })
            row.addView(TextView(ctx).apply {
                text = value
                setTextColor(valueColor)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f
                )
            })
            container.addView(row)
        }

        private fun dp(value: Int, ctx: android.content.Context): Int =
            (value * ctx.resources.displayMetrics.density).toInt()
    }
}
