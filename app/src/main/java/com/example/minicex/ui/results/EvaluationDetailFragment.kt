package com.example.minicex.ui.results

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.remote.dto.ResendEmailRequest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import com.example.minicex.ui.utils.showInfo
import com.example.minicex.ui.utils.showWarning
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.local.entity.RubricDetailEntity
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.local.entity.UserEntity
import com.example.minicex.databinding.FragmentEvaluationDetailBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EvaluationDetailFragment : Fragment() {

    private var _binding: FragmentEvaluationDetailBinding? = null
    private val binding get() = _binding!!

    private var activeEvaluation: EvaluationEntity? = null
    private var activeStudent: StudentEntity? = null
    private var activeEvaluator: UserEntity? = null
    private var activeRubricDetails: List<RubricDetailEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvaluationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDetailRubric.layoutManager = LinearLayoutManager(requireContext())

        val evaluationId = arguments?.getInt("evaluation_id", -1) ?: -1
        if (evaluationId != -1) {
            loadEvaluationDetails(evaluationId)
        } else {
            showError("Error: No se recibió el ID de la evaluación")
        }
 
        binding.btnExportPdf.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            if (activeEvaluation != null && activeStudent != null) {
                val originalText = binding.btnExportPdf.text
                binding.btnExportPdf.isEnabled = false
                binding.btnExportPdf.text = "Descargando PDF..."
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadEvaluationPdf()
                    }
                    binding.btnExportPdf.isEnabled = true
                    binding.btnExportPdf.text = originalText
                }
            } else {
                showInfo("Cargando datos, por favor espere...")
            }
        }
 
        binding.btnExportExcel.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            if (activeEvaluation != null && activeStudent != null) {
                val originalText = binding.btnExportExcel.text
                binding.btnExportExcel.isEnabled = false
                binding.btnExportExcel.text = "Descargando Excel..."
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        downloadStudentExcel()
                    }
                    binding.btnExportExcel.isEnabled = true
                    binding.btnExportExcel.text = originalText
                }
            } else {
                showInfo("Cargando datos, por favor espere...")
            }
        }
 
        binding.btnResendEmail.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            val eval = activeEvaluation
            if (eval != null) {
                resendEvaluationEmail(eval)
            } else {
                showInfo("Cargando datos, por favor espere...")
            }
        }

        // Entrance animation
        val animatedViews = listOf(
            binding.tvDetailStudentName,
            binding.tvDetailDate,
            binding.tvDetailScore,
            binding.btnExportPdf,
            binding.btnExportExcel,
            binding.btnResendEmail
        )
        animatedViews.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 30f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(index * 70L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun resendEvaluationEmail(eval: EvaluationEntity) {
        val student = activeStudent
        if (student == null) {
            showInfo("Cargando datos, por favor espere...")
            return
        }
 
        if (student.correo.isNullOrBlank()) {
            showError("Error: El alumno no tiene un correo electrónico registrado.")
            return
        }
 
        val appContext = requireContext().applicationContext
        
        // Check connection availability
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
 
        if (isOnline) {
            showInfo("Enviando correo...")
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.instance.resendEmail(ResendEmailRequest(eval.uuid))
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            showSuccess("Reporte reenviado al correo del alumno con éxito.")
                        } else {
                            val errMsg = response.body()?.message ?: "Error al reenviar el correo."
                            showError("Error: $errMsg")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showError("Error de red: ${e.message}")
                    }
                }
            }
        } else {
            // Offline: mark email as pending, do not crash, show toast
            showWarning("Sin conexión. El envío del correo quedará pendiente y se enviará automáticamente al recuperar la conexión.")
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(appContext)
                db.evaluationDao().updateEmailPendingStatus(eval.idEvaluacion, true)
            }
        }
    }
 
 
    private fun loadEvaluationDetails(evaluationId: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(appContext)
            val eval = db.evaluationDao().getEvaluationById(evaluationId)
 
            if (eval != null) {
                activeEvaluation = eval
                activeStudent = db.studentDao().getStudentById(eval.idAlumno)
                activeEvaluator = db.userDao().getUserById(eval.idEvaluador)
                activeRubricDetails = db.evaluationDao().getRubricDetailsForEvaluation(evaluationId)
 
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        bindUI()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        showError("Evaluación no encontrada en base de datos")
                    }
                }
            }
        }
    }

    private fun bindUI() {
        val eval = activeEvaluation ?: return
        val student = activeStudent ?: return

        // 1. Header Card
        binding.tvDetailStudentName.text = student.nombreCompleto
        val sdf = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale.getDefault())
        binding.tvDetailDate.text = sdf.format(Date(eval.fechaEvaluacion))
        binding.tvDetailScore.text = String.format(Locale.US, "%.1f / 10", eval.calificacionTotal / 10.0)

        // 2. Info rows
        binding.rowSetting.tvLabel.text = "Entorno:"
        binding.rowSetting.tvValue.text = eval.entornoClinico

        binding.rowPatient.tvLabel.text = "Paciente:"
        binding.rowPatient.tvValue.text = eval.tipoPaciente

        binding.rowComplexity.tvLabel.text = "Complejidad:"
        binding.rowComplexity.tvValue.text = eval.complejidad

        binding.rowIssues.tvLabel.text = "Asunto:"
        binding.rowIssues.tvValue.text = eval.asuntoPrincipal

        // 3. Feedback Comments
        val globalDetail = activeRubricDetails.firstOrNull { it.competencia == "Valoración Global" }
        binding.tvDetailStrengths.text = if (globalDetail?.aDestacar.isNullOrBlank()) "Sin comentarios específicos." else globalDetail?.aDestacar
        binding.tvDetailToImprove.text = if (globalDetail?.aMejorar.isNullOrBlank()) "Sin comentarios específicos." else globalDetail?.aMejorar

        // 4. Rubric Recycler
        // Exclude global feedback comment row from the breakdown list if desired, or keep it. Let's keep all 7.
        binding.rvDetailRubric.adapter = DetailRubricAdapter(activeRubricDetails)
        
        view?.postDelayed({ checkAndShowDetailTutorial() }, 600)
    }

    private fun checkAndShowDetailTutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()
        if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx)
            != com.example.minicex.ui.utils.TutorialManager.PHASE_DETAIL) return

        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())
        
        val steps = listOf(
            Triple(binding.tvDetailScore as View,
                "Resultado general",
                "Esta es la calificación final obtenida a partir de las competencias evaluadas."),
            Triple(binding.rvDetailRubric as View,
                "Resultado por competencia",
                "Revisa la calificación y el comentario de cada competencia. Los colores ayudan a distinguir el nivel de desempeño."),
            Triple(binding.btnExportPdf as View,
                "Guardar reporte en PDF",
                "Descarga el documento institucional oficial con el resultado, las competencias y la retroalimentación."),
            Triple(binding.btnExportExcel as View,
                "Guardar reporte en Excel",
                "Descarga el historial del alumno en Excel (.xlsx) para consultarlo o compartirlo.")
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                com.example.minicex.ui.utils.TutorialManager.setPhase(ctx, com.example.minicex.ui.utils.TutorialManager.PHASE_DONE)
                viewLifecycleOwner.lifecycleScope.launch {
                    val cleaned = com.example.minicex.ui.utils.TutorialManager.cleanupDemoData(ctx)
                    if (!isAdded) return@launch

                    val message = if (cleaned) {
                        "Tutorial completado. Los datos de demostración fueron eliminados."
                    } else {
                        "Tutorial completado. No fue posible limpiar los datos de demostración."
                    }
                    android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show()

                    val navController = findNavController()
                    if (!navController.popBackStack(R.id.nav_home, false)) {
                        navController.navigate(R.id.nav_home)
                    }
                }
                return
            }
            val (view, title, desc) = steps[index]
            overlay.show(
                targetView = view,
                stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_DETAIL + index,
                title = title, description = desc,
                isLastStep = index == steps.size - 1,
                onNext = { showStep(index + 1) },
                onSkip = { com.example.minicex.ui.utils.TutorialManager.skipAll(ctx) }
            )
        }
        showStep(0)
    }

    private suspend fun downloadEvaluationPdf() {
        val eval = activeEvaluation ?: return
        val student = activeStudent ?: return

        val fileName = "Formato_Oficial_MiniCEX_${student.matricula}_${System.currentTimeMillis()}.pdf"
        try {
            val response = RetrofitClient.instance.downloadEvaluationPdf(
                uuid = eval.uuid,
                download = 1
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val saved = com.example.minicex.utils.FileExporter.saveAndOpen(
                        requireContext(), body.bytes(), fileName,
                        com.example.minicex.utils.FileExporter.MIME_PDF
                    )
                    withContext(Dispatchers.Main) {
                        if (saved != null) showSuccess("Reporte PDF descargado con éxito")
                    }
                } else {
                    withContext(Dispatchers.Main) { showError("El servidor no devolvió el PDF") }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showError("Error: " + com.example.minicex.utils.FileExporter.serverMessage(
                        response.errorBody(), "No se pudo descargar el PDF")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showError("Error de red: ${e.message}")
            }
        }
    }

    private suspend fun downloadStudentExcel() {
        val student = activeStudent ?: return

        val fileName = "ReporteAlumno_${student.matricula}_${System.currentTimeMillis()}.xlsx"
        try {
            val studentIdOrUuid = student.uuid.ifBlank { student.idAlumno.toString() }
            val response = RetrofitClient.instance.downloadStudentXlsx(studentIdOrUuid)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val saved = com.example.minicex.utils.FileExporter.saveAndOpen(
                        requireContext(), body.bytes(), fileName,
                        com.example.minicex.utils.FileExporter.MIME_XLSX
                    )
                    withContext(Dispatchers.Main) {
                        if (saved != null) showSuccess("Reporte en Excel descargado con éxito")
                    }
                } else {
                    withContext(Dispatchers.Main) { showError("El servidor no devolvió el archivo Excel") }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showError("Error: " + com.example.minicex.utils.FileExporter.serverMessage(
                        response.errorBody(), "No se pudo descargar el Excel")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showError("Error de red: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- RecyclerView Components ---

    private class DetailRubricAdapter(private val details: List<RubricDetailEntity>) :
        RecyclerView.Adapter<DetailRubricAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvDetailCompName)
            val tvScore: TextView = view.findViewById(R.id.tvDetailCompScore)
            val tvNote: TextView = view.findViewById(R.id.tvDetailCompNote)
            val cardBadge: MaterialCardView = view.findViewById(R.id.cardScoreBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_rubric, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = details[position]
            holder.tvName.text = item.competencia

            val scoreText = if (item.puntaje == 0) "N/V" else item.puntaje.toString()
            holder.tvScore.text = scoreText

            val context = holder.itemView.context
            val textColor = androidx.core.content.ContextCompat.getColor(context, when (item.puntaje) {
                0 -> R.color.text_secondary
                in 1..3 -> R.color.status_unsatisfactory
                in 4..6 -> R.color.status_satisfactory
                else -> R.color.status_superior
            })
            
            val bgColor = Color.argb(
                (0.15f * 255).toInt(),
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor)
            )
            holder.cardBadge.setCardBackgroundColor(bgColor)
            holder.tvScore.setTextColor(textColor)

            if (!item.notas.isNullOrBlank()) {
                holder.tvNote.text = item.notas
                holder.tvNote.visibility = View.VISIBLE
            } else {
                holder.tvNote.visibility = View.GONE
            }
        }

        override fun getItemCount() = details.size
    }
}
