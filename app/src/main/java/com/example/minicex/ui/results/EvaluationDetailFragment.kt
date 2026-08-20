package com.example.minicex.ui.results

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.data.remote.dto.ResendEmailRequest

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import com.example.minicex.ui.utils.showInfo
import com.example.minicex.ui.utils.showWarning
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.minicex.R
import android.util.Base64
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
import java.io.File
import java.io.FileOutputStream
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
            if (activeEvaluation != null && activeStudent != null && activeEvaluator != null) {
                val originalText = binding.btnExportPdf.text
                binding.btnExportPdf.isEnabled = false
                binding.btnExportPdf.text = "Generando PDF..."
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        exportToPdf()
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
            if (activeEvaluation != null && activeStudent != null && activeEvaluator != null) {
                val originalText = binding.btnExportExcel.text
                binding.btnExportExcel.isEnabled = false
                binding.btnExportExcel.text = "Generando Excel..."
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        exportToExcel()
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
                "Genera un documento institucional con el resultado, las competencias y la retroalimentación."),
            Triple(binding.btnExportExcel as View,
                "Guardar reporte en Excel",
                "Descarga una tabla con la información de la evaluación para consultarla o compartirla.")
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

    private fun decodeBase64ToBitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun exportToPdf() {
        val eval = activeEvaluation ?: return
        val student = activeStudent ?: return
        val evaluator = activeEvaluator ?: return

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42.5f
        val rightMargin = pageWidth - margin

        var currentPageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageIndex).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas

        // Colors
        val institutionalBlue = Color.parseColor("#1B5E96")
        val institutionalGold = Color.parseColor("#B8860B")
        val slateDark = Color.parseColor("#1E293B")
        val slateBorder = Color.parseColor("#DCE1E8")
        val slateLight = Color.parseColor("#475569")
        val cardBg = Color.parseColor("#F7F8FA")
        val footerColor = Color.parseColor("#94A3B8")

        // Paints
        val blueFillPaint = Paint().apply {
            color = institutionalBlue
            style = Paint.Style.FILL
        }
        val goldFillPaint = Paint().apply {
            color = institutionalGold
            style = Paint.Style.FILL
        }
        val cardFillPaint = Paint().apply {
            color = cardBg
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = slateBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titlePaint = Paint().apply {
            color = institutionalBlue
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = institutionalGold
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10.5f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = slateLight
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 8.5f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = institutionalBlue
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            isAntiAlias = true
        }
        val textBoldPaint = Paint().apply {
            color = slateDark
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            isAntiAlias = true
        }
        val textRegularPaint = Paint().apply {
            color = slateDark
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            isAntiAlias = true
        }
        val textCenterPaint = Paint().apply {
            color = slateDark
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textWhitePaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            isAntiAlias = true
        }
        val textWhiteCenterPaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Draw initial top blue banner and gold line
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 22f, blueFillPaint)
        canvas.drawRect(0f, 22f, pageWidth.toFloat(), 28f, goldFillPaint)

        var y = 42.5f

        fun drawFooter(canvas: Canvas) {
            val footerTextPaint = Paint().apply {
                color = footerColor
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Reporte digital autogenerado por la plataforma institucional MINI-CEX.", pageWidth / 2f, 810f, footerTextPaint)
            val year = Calendar.getInstance().get(Calendar.YEAR)
            canvas.drawText("Facultad de Terapia Física y Rehabilitación - $year", pageWidth / 2f, 822f, footerTextPaint)
        }

        fun checkPageBreak(requiredHeight: Float) {
            if (y + requiredHeight > 780f) {
                drawFooter(canvas)
                pdfDocument.finishPage(currentPage)

                currentPageIndex++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageIndex).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas

                // Draw top banners on new page
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 22f, blueFillPaint)
                canvas.drawRect(0f, 22f, pageWidth.toFloat(), 28f, goldFillPaint)

                y = 42.5f
            }
        }

        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return emptyList()
            val paragraphs = text.split("\n")
            val lines = mutableListOf<String>()
            
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    lines.add("")
                    continue
                }
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                    val width = paint.measureText(testLine)
                    if (width <= maxWidth) {
                        currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine.toString())
                        }
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
            }
            return lines
        }

        // 1. Branding Header with logo.png
        var textStartX = margin
        try {
            val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logo)
            if (logoBitmap != null) {
                val targetWidth = 62f
                val aspectRatio = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
                val targetHeight = targetWidth / aspectRatio
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, targetWidth.toInt(), targetHeight.toInt(), true)
                canvas.drawBitmap(scaledLogo, margin, y, null)
                textStartX = margin + targetWidth + 15f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        canvas.drawText("TERAPIA FÍSICA Y REHABILITACIÓN", textStartX, y + 18f, titlePaint)
        canvas.drawText("REPORTE DE EVALUACIÓN CLÍNICA (MINI-CEX)", textStartX, y + 34f, subtitlePaint)
        canvas.drawText("Sistema de Gestión de Rúbricas Clínicas", textStartX, y + 48f, labelPaint)

        y += 65f
        canvas.drawLine(margin, y, rightMargin, y, linePaint)
        y += 15f

        // 2. Metadata block (DATOS GENERALES)
        canvas.drawText("DATOS GENERALES", margin, y, headerPaint)
        y += 18f

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date(eval.fechaEvaluacion))

        val infoRows = listOf(
            Pair(Pair("Alumno:", student.nombreCompleto), Pair("Evaluador:", evaluator.nombreCompleto)),
            Pair(Pair("Matrícula:", student.matricula), Pair("Fecha:", dateStr)),
            Pair(Pair("Semestre/Grupo:", student.semestreGrupo), Pair("Entorno Clínico:", eval.entornoClinico)),
            Pair(Pair("Tipo de Paciente:", eval.tipoPaciente), Pair("Complejidad:", eval.complejidad)),
            Pair(Pair("Asunto Principal:", eval.asuntoPrincipal), Pair("Tiempos:", "Obs: ${eval.tiempoObservacion} min | Feedback: ${eval.tiempoFeedback} min"))
        )

        val keyCol1 = margin
        val valCol1 = margin + 95f
        val keyCol2 = margin + 270f
        val valCol2 = margin + 355f

        infoRows.forEach { row ->
            canvas.drawText(row.first.first, keyCol1, y, textBoldPaint)
            canvas.drawText(row.first.second, valCol1, y, textRegularPaint)
            canvas.drawText(row.second.first, keyCol2, y, textBoldPaint)
            canvas.drawText(row.second.second, valCol2, y, textRegularPaint)
            y += 16f
        }
        y += 8f

        // 3. Global Score Card Box
        canvas.drawRect(margin, y, rightMargin, y + 25f, cardFillPaint)
        canvas.drawRect(margin, y, rightMargin, y + 25f, linePaint)

        val scoreCardTitlePaint = Paint().apply {
            color = institutionalGold
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            isAntiAlias = true
        }
        val scoreCardValPaint = Paint().apply {
            color = institutionalGold
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("CALIFICACIÓN GLOBAL OBTENIDA:", margin + 15f, y + 17f, scoreCardTitlePaint)
        val finalScoreStr = String.format(Locale.US, "%.1f / 10", eval.calificacionTotal / 10.0)
        canvas.drawText(finalScoreStr, rightMargin - 15f, y + 18f, scoreCardValPaint)

        y += 40f

        // 4. Competencies Section (DESGLOSE DE COMPETENCIAS)
        canvas.drawText("DESGLOSE DE COMPETENCIAS", margin, y, headerPaint)
        y += 12f

        // Draw Table Header
        canvas.drawRect(margin, y, rightMargin, y + 22f, blueFillPaint)
        canvas.drawText(" Competencia", margin + 8f, y + 15f, textWhitePaint)
        canvas.drawText("Puntaje (1-9)", margin + 275f, y + 15f, textWhiteCenterPaint)
        canvas.drawText(" Nivel Desempeño", margin + 318f, y + 15f, textWhitePaint)

        // Draw white dividers in header
        canvas.drawLine(margin + 240f, y, margin + 240f, y + 22f, Paint().apply { color = Color.WHITE; strokeWidth = 1f })
        canvas.drawLine(margin + 310f, y, margin + 310f, y + 22f, Paint().apply { color = Color.WHITE; strokeWidth = 1f })

        y += 22f

        activeRubricDetails.forEach { compDetail ->
            checkPageBreak(22f)
            canvas.drawRect(margin, y, rightMargin, y + 22f, linePaint)

            val pt = compDetail.puntaje
            val levelStr = when {
                pt in 1..3 -> "Insatisfactorio"
                pt in 4..6 -> "Satisfactorio"
                pt in 7..9 -> "Sobresaliente"
                else -> "No Evaluado"
            }
            val scoreStr = if (pt in 1..9) pt.toString() else "-"
            val displayName = if (compDetail.competencia.length > 35) compDetail.competencia.take(33) + "..." else compDetail.competencia

            canvas.drawText(displayName, margin + 8f, y + 15f, textRegularPaint)
            canvas.drawText(scoreStr, margin + 275f, y + 15f, textCenterPaint)
            canvas.drawText(" " + levelStr, margin + 318f, y + 15f, textRegularPaint)

            canvas.drawLine(margin + 240f, y, margin + 240f, y + 22f, linePaint)
            canvas.drawLine(margin + 310f, y, margin + 310f, y + 22f, linePaint)

            y += 22f
        }

        y += 15f

        // 5. Feedback Section (RETROALIMENTACIÓN Y PLAN DE MEJORA)
        checkPageBreak(25f)
        canvas.drawText("RETROALIMENTACIÓN Y PLAN DE MEJORA", margin, y, headerPaint)
        y += 18f

        val prefixPaint = Paint().apply {
            color = slateDark
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            isAntiAlias = true
        }
        val contentPaint = Paint().apply {
            color = slateDark
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            isAntiAlias = true
        }

        activeRubricDetails.forEach { compDetail ->
            val hasNotas = !compDetail.notas.isNullOrBlank()
            val hasDestacar = !compDetail.aDestacar.isNullOrBlank()
            val hasMejorar = !compDetail.aMejorar.isNullOrBlank()

            if (hasNotas || hasDestacar || hasMejorar) {
                val compTitleHeight = 16f
                checkPageBreak(compTitleHeight)
                
                val compTitlePaint = Paint().apply {
                    color = institutionalGold
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 9f
                    isAntiAlias = true
                }
                canvas.drawText(compDetail.competencia, margin, y + 10f, compTitlePaint)
                y += compTitleHeight

                fun drawFeedbackField(prefix: String, contentText: String) {
                    val contentWidth = 455f
                    val wrappedLines = wrapText(contentText, contentPaint, contentWidth)
                    if (wrappedLines.isEmpty()) return
                    
                    wrappedLines.forEachIndexed { index, line ->
                        checkPageBreak(14f)
                        if (index == 0) {
                            canvas.drawText(prefix, margin, y + 10f, prefixPaint)
                        }
                        canvas.drawText(line, margin + 55f, y + 10f, contentPaint)
                        y += 14f
                    }
                    y += 4f
                }

                if (hasNotas) {
                    drawFeedbackField("Notas: ", compDetail.notas!!)
                }
                if (hasDestacar) {
                    drawFeedbackField("Destacar: ", compDetail.aDestacar!!)
                }
                if (hasMejorar) {
                    drawFeedbackField("Mejorar: ", compDetail.aMejorar!!)
                }
                y += 6f
            }
        }

        // Draw footer on final page
        drawFooter(canvas)
        pdfDocument.finishPage(currentPage)
 
        try {
            val file = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "Formato_Oficial_MiniCEX_${student.matricula}_${System.currentTimeMillis()}.pdf"
            )
            pdfDocument.writeTo(FileOutputStream(file))
            withContext(Dispatchers.Main) {
                showSuccess("Reporte PDF oficial generado con éxito")
                openFile(file, "application/pdf")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showError("Error al escribir archivo PDF")
            }
        } finally {
            pdfDocument.close()
        }
    }
 
    private suspend fun exportToExcel() {
        val eval = activeEvaluation ?: return
        val student = activeStudent ?: return
        val evaluator = activeEvaluator ?: return
 
        val fileName = "Evaluacion_MiniCEX_${student.matricula}_${System.currentTimeMillis()}.csv"
        val file = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
 
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date(eval.fechaEvaluacion))
 
        try {
            val writer = FileOutputStream(file).bufferedWriter()
            writer.write("\"Campo\",\"Valor\"")
            writer.newLine()
            writer.write("\"Matrícula del Alumno\",\"${student.matricula}\"")
            writer.newLine()
            writer.write("\"Nombre del Alumno\",\"${student.nombreCompleto}\"")
            writer.newLine()
            writer.write("\"Semestre y Grupo\",\"${student.semestreGrupo}\"")
            writer.newLine()
            writer.write("\"Fecha de Evaluación\",\"$dateStr\"")
            writer.newLine()
            writer.write("\"Evaluador/Docente\",\"${evaluator.nombreCompleto}\"")
            writer.newLine()
            writer.write("\"Entorno Clínico\",\"${eval.entornoClinico}\"")
            writer.newLine()
            writer.write("\"Tipo de Paciente\",\"${eval.tipoPaciente}\"")
            writer.newLine()
            writer.write("\"Complejidad\",\"${eval.complejidad}\"")
            writer.newLine()
            writer.write("\"Asuntos Principales\",\"${eval.asuntoPrincipal}\"")
            writer.newLine()
            writer.write("\"Tiempo Observación (min)\",\"${eval.tiempoObservacion}\"")
            writer.newLine()
            writer.write("\"Tiempo Feedback (min)\",\"${eval.tiempoFeedback}\"")
            writer.newLine()
            writer.write("\"Calificación Final / 100\",\"${String.format(Locale.US, "%.1f", eval.calificacionTotal)}\"")
            writer.newLine()
 
            // Rubric details breakdown rows
            writer.newLine()
            writer.write("\"Competencia Evaluada\",\"Puntaje Obtenido\",\"Notas de Competencia\"")
            writer.newLine()
            activeRubricDetails.forEach { comp ->
                val scoreVal = if (comp.puntaje == 0) "N/V" else comp.puntaje.toString()
                writer.write("\"${comp.competencia}\",\"$scoreVal\",\"${comp.notas ?: ""}\"")
                writer.newLine()
            }
 
            // Global comments
            val globalDetail = activeRubricDetails.firstOrNull { it.competencia == "Valoración Global" }
            writer.newLine()
            writer.write("\"Aspectos a destacar (Fortalezas)\",\"${globalDetail?.aDestacar ?: ""}\"")
            writer.newLine()
            writer.write("\"Aspectos a mejorar (Áreas de mejora)\",\"${globalDetail?.aMejorar ?: ""}\"")
            writer.newLine()
 
            writer.close()
            withContext(Dispatchers.Main) {
                showSuccess("Hoja de cálculo CSV exportada con éxito")
                openFile(file, "text/csv")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                showError("Error al generar archivo CSV")
            }
        }
    }
 
    private fun openFile(file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
 
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
 
        try {
            startActivity(intent)
        } catch (e: Exception) {
            showError("No hay una aplicación para abrir este archivo")
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
