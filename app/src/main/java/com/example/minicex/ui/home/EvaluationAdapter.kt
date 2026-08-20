package com.example.minicex.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.minicex.databinding.ItemEvaluationSummaryBinding

class EvaluationAdapter(
    private val evaluations: List<EvaluationSummary>,
    private val onItemClick: (EvaluationSummary) -> Unit
) :
    RecyclerView.Adapter<EvaluationAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemEvaluationSummaryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEvaluationSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val evaluation = evaluations[position]
        holder.binding.tvStudentName.text = evaluation.studentName
        holder.binding.tvDate.text = evaluation.date
        holder.binding.tvScore.text = String.format("%.1f", evaluation.score)
        holder.binding.tvInitial.text = evaluation.studentName.take(1).uppercase()

        holder.itemView.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onItemClick(evaluation)
        }

        // Staggered entry animation for items
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 50f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay(position * 60L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
            .start()
    }

    override fun getItemCount() = evaluations.size
}
