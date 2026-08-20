package com.example.minicex.ui.evaluation.steps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.minicex.R
import com.example.minicex.databinding.ItemRubricBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class RubricAdapter(
    private val competences: List<Competency>,
    private val getCurrentScore: (String) -> Int?,
    private val getCurrentNotes: (String) -> String?,
    private val onScoreChanged: (String, Int) -> Unit,
    private val onNotesChanged: (String, String) -> Unit
) :
    RecyclerView.Adapter<RubricAdapter.ViewHolder>() {

    private val expandedCriteria = mutableSetOf<String>()

    class ViewHolder(val binding: ItemRubricBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRubricBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val competence = competences[position]
        holder.binding.tvCompetencyNumber.text = (position + 1).toString()
        holder.binding.tvCompetence.text = competence.name
        holder.binding.tvDescriptors.text = competence.descriptors.joinToString("\n") { "• $it" }

        val criteriaExpanded = competence.name in expandedCriteria
        holder.binding.tvDescriptors.visibility = if (criteriaExpanded) View.VISIBLE else View.GONE
        holder.binding.btnToggleCriteria.text = if (criteriaExpanded) "Ocultar criterios" else "Ver criterios clínicos"
        holder.binding.btnToggleCriteria.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            if (competence.name in expandedCriteria) expandedCriteria.remove(competence.name)
            else expandedCriteria.add(competence.name)
            val expanded = competence.name in expandedCriteria
            holder.binding.tvDescriptors.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.binding.btnToggleCriteria.text = if (expanded) "Ocultar criterios" else "Ver criterios clínicos"
        }

        // Remove old text watcher to avoid duplicate callbacks
        val oldWatcher = holder.binding.etNotes.tag as? android.text.TextWatcher
        if (oldWatcher != null) {
            holder.binding.etNotes.removeTextChangedListener(oldWatcher)
        }

        // Set initial note
        val initialNote = getCurrentNotes(competence.name) ?: ""
        holder.binding.etNotes.setText(initialNote)
        holder.binding.tilNotes.error = null

        var currentScore = getCurrentScore(competence.name)
        var currentNote = initialNote

        fun refreshCardState() {
            updateScoreState(holder, currentScore)
            updateCompletionState(holder, currentScore, currentNote)
            holder.binding.btnUseSuggestedNote.visibility =
                if (currentScore != null) View.VISIBLE else View.GONE
        }

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentNote = s?.toString() ?: ""
                onNotesChanged(competence.name, currentNote)
                updateCompletionState(holder, currentScore, currentNote)
                holder.binding.tilNotes.error =
                    if (holder.binding.etNotes.hasFocus() && currentNote.isBlank()) "Agrega un comentario" else null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        holder.binding.etNotes.addTextChangedListener(textWatcher)
        holder.binding.etNotes.tag = textWatcher

        holder.binding.cgScores.removeAllViews()

        addScoreChip(holder.binding.cgScores, "N/E", R.color.chip_score_nv_bg, 0, currentScore == 0) { score ->
            currentScore = score
            onScoreChanged(competence.name, score)
            refreshCardState()
        }

        for (i in 1..9) {
            val bgColorRes = when (i) {
                in 1..3 -> R.color.chip_score_insatisfactorio_bg
                in 4..6 -> R.color.chip_score_satisfactorio_bg
                else -> R.color.chip_score_superior_bg
            }
            addScoreChip(holder.binding.cgScores, i.toString(), bgColorRes, i, currentScore == i) { score ->
                currentScore = score
                onScoreChanged(competence.name, score)
                refreshCardState()
            }
        }

        holder.binding.btnUseSuggestedNote.setOnClickListener { button ->
            val score = currentScore ?: return@setOnClickListener
            button.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            val suggestion = suggestedNote(competence.name, score)
            holder.binding.etNotes.setText(suggestion)
            holder.binding.etNotes.setSelection(suggestion.length)
            holder.binding.etNotes.requestFocus()
        }

        refreshCardState()
    }

    private fun addScoreChip(
        chipGroup: ChipGroup,
        text: String,
        bgColorRes: Int,
        score: Int,
        isSelected: Boolean,
        onSelected: (Int) -> Unit,
    ) {
        val context = chipGroup.context
        val chip = Chip(context).apply {
            this.text = text
            this.isCheckable = true
            this.minWidth = (48 * context.resources.displayMetrics.density).toInt()
            this.minHeight = (44 * context.resources.displayMetrics.density).toInt()
            this.gravity = android.view.Gravity.CENTER
            this.setChipBackgroundColorResource(bgColorRes)
            this.setTextColor(ContextCompat.getColorStateList(context, R.color.chip_score_text))
            this.chipStrokeWidth = 0f
            this.chipStartPadding = 0f
            this.chipEndPadding = 0f
            this.textStartPadding = 0f
            this.textEndPadding = 0f
            this.textAlignment = View.TEXT_ALIGNMENT_CENTER
            
            // Set checked state before listener to avoid trigger
            this.isChecked = isSelected

            this.setOnCheckedChangeListener { view, isChecked ->
                if (isChecked) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelected(score)
                }
            }
        }
        chipGroup.addView(chip)
    }

    private fun updateScoreState(holder: ViewHolder, score: Int?) {
        val context = holder.binding.root.context
        val (label, colorRes) = when (score) {
            null -> "Sin calificar" to R.color.text_secondary
            0 -> "No evaluado" to R.color.text_secondary
            in 1..3 -> "$score • Requiere apoyo" to R.color.status_unsatisfactory
            in 4..6 -> "$score • Satisfactorio" to R.color.status_satisfactory
            else -> "$score • Sobresaliente" to R.color.status_superior
        }
        holder.binding.tvSelectedScore.text = label
        holder.binding.tvSelectedScore.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    private fun updateCompletionState(holder: ViewHolder, score: Int?, note: String) {
        val context = holder.binding.root.context
        val isComplete = score != null && note.isNotBlank()
        holder.binding.tvCompletionStatus.text = if (isComplete) "Completa ✓" else "Pendiente"
        holder.binding.tvCompletionStatus.setTextColor(
            ContextCompat.getColor(context, if (isComplete) R.color.status_superior else R.color.text_secondary)
        )
        holder.binding.cardRubric.strokeColor = ContextCompat.getColor(
            context, if (isComplete) R.color.status_superior else R.color.card_border
        )
        holder.binding.cardRubric.strokeWidth =
            ((if (isComplete) 2 else 1) * context.resources.displayMetrics.density).toInt()
    }

    private fun suggestedNote(competencyName: String, score: Int): String = when (score) {
        0 -> "Esta competencia no pudo observarse durante el encuentro clínico."
        in 1..3 -> "Requiere reforzar $competencyName mediante práctica supervisada y seguimiento."
        in 4..6 -> "Muestra un desempeño adecuado en $competencyName para su nivel de formación."
        else -> "Muestra un desempeño seguro y destacado en $competencyName."
    }

    override fun getItemCount() = competences.size
}

