package com.example.minicex.ui.evaluation.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.minicex.R
import com.example.minicex.databinding.FragmentStep2RubricBinding
import com.example.minicex.ui.evaluation.EvaluationSharedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Step2RubricFragment : Fragment() {
    private var _binding: FragmentStep2RubricBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStep2RubricBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Add entry animation
        view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in_up))

        val competences = listOf(
            Competency(
                "Anamnesis",
                listOf(
                    "Facilita las explicaciones del paciente",
                    "Estructurada y exhaustiva",
                    "Hace preguntas adecuadas para obtener información del paciente",
                    "Responde adecuadamente a expresiones claves verbales y no verbales del paciente"
                )
            ),
            Competency(
                "Exploración Física",
                listOf(
                    "Exploración apropiada a la clínica",
                    "Sigue una secuencia lógica y es sistemática",
                    "Explicación al paciente del proceso de exploración",
                    "Sensible a la comodidad y privacidad del paciente"
                )
            ),
            Competency(
                "Profesionalismo",
                listOf(
                    "Presentación del alumno",
                    "Muestra respeto y crea un clima de confianza. Empático",
                    "Se comporta de forma ética y considera los aspectos legales relevantes al caso",
                    "Atento a las necesidades del paciente en términos de confort, confidencialidad y respeto"
                )
            ),
            Competency(
                "Juicio Clínico",
                listOf(
                    "Realiza una orientación diagnóstica adecuada con un diagnóstico diferencial",
                    "Formula un plan de manejo coherente con el diagnóstico",
                    "Hace/Indica los estudios diagnósticos considerando riesgos, beneficios y costes"
                )
            ),
            Competency(
                "Habilidades Comunicativas",
                listOf(
                    "Utiliza un lenguaje comprensible y empático para el paciente",
                    "Franco y honesto. Explora las perspectivas del paciente y la familia",
                    "Informa y consensua el plan de manejo/tratamiento con el paciente"
                )
            ),
            Competency(
                "Organización / Eficiencia",
                listOf(
                    "Prioriza los problemas",
                    "Buena gestión del tiempo y los recursos",
                    "Derivaciones adecuadas",
                    "Es concreto",
                    "Recapitula y hace un resumen final",
                    "Capacidad de trabajo en equipo"
                )
            ),
            Competency(
                "Valoración Global",
                listOf(
                    "Demuestra satisfactoriamente juicio clínico, capacidad de síntesis y de resolución",
                    "Tiene en cuenta los aspectos de eficiencia, valorando riesgos y beneficios"
                )
            )
        )

        val viewModel = ViewModelProvider(requireParentFragment()).get(EvaluationSharedViewModel::class.java)
        viewModel.scores.observe(viewLifecycleOwner) { scores ->
            val notes = viewModel.notes.value.orEmpty()
            val completed = competences.count { competency ->
                scores?.containsKey(competency.name) == true && !notes[competency.name].isNullOrBlank()
            }
            binding.tvRubricProgress.text =
                "$completed de ${competences.size} competencias completas"
            binding.rubricProgressIndicator.setProgressCompat(completed, true)
        }
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            val scores = viewModel.scores.value.orEmpty()
            val completed = competences.count { competency ->
                scores.containsKey(competency.name) && !notes[competency.name].isNullOrBlank()
            }
            binding.tvRubricProgress.text =
                "$completed de ${competences.size} competencias completas"
            binding.rubricProgressIndicator.setProgressCompat(completed, true)
        }

        binding.rvRubric.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRubric.adapter = RubricAdapter(
            competences,
            getCurrentScore = { name -> viewModel.scores.value?.get(name) },
            getCurrentNotes = { name -> viewModel.notes.value?.get(name) },
            onScoreChanged = { name, score -> viewModel.setScore(name, score) },
            onNotesChanged = { name, notes -> viewModel.setNotes(name, notes) }
        )
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({ checkAndShowRubricTutorial() }, 400)
    }

    private fun checkAndShowRubricTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx)
            != com.example.minicex.ui.utils.TutorialManager.PHASE_STEP2) return

        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())
        val firstItem = binding.rvRubric.findViewHolderForAdapterPosition(0)?.itemView
        val steps = listOf(
            Triple(
                firstItem?.findViewById<View>(R.id.btnToggleCriteria) ?: binding.rvRubric,
                "Consulta los criterios clínicos",
                "Cada competencia aparece resumida para que la captura sea ligera. Abre los criterios solo cuando necesites recordar qué aspectos debes observar."
            ),
            Triple(
                firstItem?.findViewById<View>(R.id.cgScores) ?: binding.rvRubric,
                "Elige la calificación",
                "Selecciona del 1 al 9 según lo observado: 1 a 3 requiere apoyo, 4 a 6 es satisfactorio y 7 a 9 es sobresaliente. Usa N/E si no fue posible evaluar esa competencia."
            ),
            Triple(
                firstItem?.findViewById<View>(R.id.btnUseSuggestedNote) ?: binding.rvRubric,
                "Ahorra tiempo con una sugerencia",
                "Este botón crea un comentario breve de acuerdo con la calificación. Es solo un punto de partida: puedes cambiarlo para reflejar lo que realmente observaste."
            ),
            Triple(
                firstItem?.findViewById<View>(R.id.tilNotes) ?: binding.rvRubric,
                "Deja una observación clara",
                "El comentario es obligatorio y siempre puede editarse. Cuando haya calificación y comentario, la tarjeta cambiará a 'Completa' y avanzará la barra superior."
            )
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                com.example.minicex.ui.utils.TutorialManager.advancePhase(ctx) // → PHASE_STEP3
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(400)
                    if (_binding != null && isAdded) {
                        val evalFragment = parentFragment as? com.example.minicex.ui.evaluation.EvaluationFragment
                        evalFragment?.advanceToNextStep()
                    }
                }
                return
            }
            val (target, title, description) = steps[index]
            overlay.show(
                targetView = target,
                stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_STEP2 + index,
                title = title,
                description = description,
                isLastStep = false,
                onNext = { showStep(index + 1) },
                onSkip = { com.example.minicex.ui.utils.TutorialManager.skipAll(ctx) }
            )
        }
        showStep(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

