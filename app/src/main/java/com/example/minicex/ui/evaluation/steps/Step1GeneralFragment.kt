package com.example.minicex.ui.evaluation.steps

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.minicex.ui.utils.showSuccess
import com.example.minicex.ui.utils.showError
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.minicex.R
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.local.entity.SyncQueueEntity
import com.example.minicex.data.repository.SyncRepository
import com.google.gson.Gson
import com.example.minicex.data.remote.RetrofitClient
import com.example.minicex.databinding.FragmentStep1GeneralBinding
import com.example.minicex.ui.evaluation.EvaluationSharedViewModel
import kotlinx.coroutines.launch

class Step1GeneralFragment : Fragment() {
    private var _binding: FragmentStep1GeneralBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStep1GeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Add entry animation
        view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in_up))
        
        val viewModel = ViewModelProvider(requireParentFragment()).get(EvaluationSharedViewModel::class.java)

        // Observe students from database reactively
        observeStudents()

        // Button to add student
        binding.btnAddStudent.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_student, null)
            val etMatricula = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMatricula)
            val etNombreCompleto = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreCompleto)
            val etSemestreGrupo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSemestreGrupo)
            val etCorreo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCorreo)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Registrar Nuevo Alumno")
                .setView(dialogView)
                .setPositiveButton("Guardar") { dialog, _ ->
                    val matricula = etMatricula.text.toString().trim()
                    val nombreCompleto = etNombreCompleto.text.toString().trim()
                    val semestreGrupo = etSemestreGrupo.text.toString().trim()
                    val correo = etCorreo.text.toString().trim()

                    if (matricula.isEmpty() || nombreCompleto.isEmpty() || semestreGrupo.isEmpty() || correo.isEmpty()) {
                        showError("Todos los campos son obligatorios")
                        return@setPositiveButton
                     }
 
                     if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                        showError("Por favor, ingresa un correo electrónico válido")
                        return@setPositiveButton
                     }
 
                     val appContext = requireContext().applicationContext
                     val dialogContext = requireContext()
                     viewLifecycleOwner.lifecycleScope.launch {
                         val db = AppDatabase.getDatabase(appContext)
                         val prefs = appContext.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
                         val evaluadorEmail = prefs.getString("evaluador_email", "")
                         val userLocal = db.userDao().getUserByEmail(evaluadorEmail ?: "")
                         val evaluadorId = userLocal?.idUsuario ?: prefs.getInt("evaluador_id", 1)
 
                         val newStudent = StudentEntity(
                             idAlumno = 0,
                             matricula = matricula,
                             nombreCompleto = nombreCompleto,
                             semestreGrupo = semestreGrupo,
                             correo = correo,
                             idDocente = evaluadorId,
                             isSynced = false
                         )
 
                         val insertedId = db.studentDao().insertStudent(newStudent).toInt()
                         val savedStudent = newStudent.copy(idAlumno = insertedId)
                         
                         // Push to sync queue
                         val syncQueueDao = db.syncQueueDao()
                         syncQueueDao.insertSyncAction(
                             SyncQueueEntity(
                                 action = "insert",
                                 tableName = "alumnos",
                                 entityUuid = savedStudent.uuid,
                                 dataPayload = Gson().toJson(savedStudent)
                             )
                         )
                         
                         if (_binding != null) {
                             showSuccess("Alumno registrado localmente")
                             // Select student reactively in ViewModel
                             viewModel.selectStudent(savedStudent)
                         }

                        // Trigger sync in background using MainActivity's lifecycleScope so it doesn't get cancelled
                        val mainActivity = activity as? com.example.minicex.MainActivity
                        if (mainActivity != null) {
                            mainActivity.triggerSync()
                        } else {
                            launch {
                                try {
                                    val syncRepo = SyncRepository(
                                        db.evaluationDao(),
                                        RetrofitClient.instance,
                                        appContext
                                    )
                                    syncRepo.autoSync()
                                } catch (e: java.lang.Exception) {
                                    android.util.Log.e("AddStudent", "Sync failed: ${e.message}")
                                }
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Setup other spinners
        val settings = arrayOf("Ambulatorio", "Intra hospitalario", "Otros")
        val patientTypes = arrayOf("Nuevo", "Subsecuente")
        val complexities = arrayOf("Baja", "Media", "Alta")

        setupDropdown(binding.acClinicalSetting, settings)
        setupDropdown(binding.acPatientType, patientTypes)
        setupDropdown(binding.acComplexity, complexities)

        // Listeners to sync options with ViewModel
        binding.acClinicalSetting.setOnItemClickListener { _, _, _, _ ->
            val selectedSetting = binding.acClinicalSetting.text.toString()
            val isOther = selectedSetting == "Otros"
            binding.tilOtherClinicalSetting.visibility = if (isOther) View.VISIBLE else View.GONE
            if (isOther) {
                viewModel.setClinicalSetting(binding.etOtherClinicalSetting.text?.toString()?.trim().orEmpty())
                binding.etOtherClinicalSetting.requestFocus()
            } else {
                binding.etOtherClinicalSetting.setText("")
                viewModel.setClinicalSetting(selectedSetting)
            }
        }
        binding.etOtherClinicalSetting.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.acClinicalSetting.text.toString() == "Otros") {
                    val customSetting = s?.toString()?.trim().orEmpty()
                    viewModel.setClinicalSetting(customSetting)
                    binding.tilOtherClinicalSetting.error =
                        if (customSetting.isBlank()) "Especifica el entorno clínico" else null
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val savedSetting = viewModel.clinicalSetting.value.orEmpty()
        if (savedSetting.isNotBlank()) {
            if (savedSetting in settings && savedSetting != "Otros") {
                binding.acClinicalSetting.setText(savedSetting, false)
            } else {
                binding.acClinicalSetting.setText("Otros", false)
                binding.tilOtherClinicalSetting.visibility = View.VISIBLE
                binding.etOtherClinicalSetting.setText(savedSetting)
            }
        }
        viewModel.clinicalSetting.observe(viewLifecycleOwner) { setting ->
            if (setting.isNullOrBlank()) return@observe
            if (setting in settings && setting != "Otros") {
                if (binding.acClinicalSetting.text.toString() != setting) {
                    binding.acClinicalSetting.setText(setting, false)
                }
                binding.tilOtherClinicalSetting.visibility = View.GONE
                binding.tilOtherClinicalSetting.error = null
            } else {
                if (binding.acClinicalSetting.text.toString() != "Otros") {
                    binding.acClinicalSetting.setText("Otros", false)
                }
                binding.tilOtherClinicalSetting.visibility = View.VISIBLE
                if (binding.etOtherClinicalSetting.text?.toString() != setting) {
                    binding.etOtherClinicalSetting.setText(setting)
                }
            }
        }
        viewModel.patientType.observe(viewLifecycleOwner) { patientType ->
            if (!patientType.isNullOrBlank() && binding.acPatientType.text.toString() != patientType) {
                binding.acPatientType.setText(patientType, false)
            }
        }
        viewModel.complexity.observe(viewLifecycleOwner) { complexity ->
            if (!complexity.isNullOrBlank() && binding.acComplexity.text.toString() != complexity) {
                binding.acComplexity.setText(complexity, false)
            }
        }
        binding.acPatientType.setOnItemClickListener { _, _, _, _ ->
            viewModel.setPatientType(binding.acPatientType.text.toString())
        }
        binding.acComplexity.setOnItemClickListener { _, _, _, _ ->
            viewModel.setComplexity(binding.acComplexity.text.toString())
        }

        // main issues chips mapping
        val chipMap = mapOf(
            binding.chipAnamnesis to "Anamnesis",
            binding.chipDiagnosis to "Diagnóstico",
            binding.chipTreatment to "Tratamiento",
            binding.chipPrevention to "Prevención",
            binding.chipControl to "Control"
        )
        chipMap.forEach { (chip, name) ->
            chip.setOnCheckedChangeListener { view, isChecked ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.toggleMainIssue(name, isChecked)
            }
        }
    }

    private var step1TutorialStarted = false   // prevents multiple triggers from Flow emissions

    private fun observeStudents() {
        val appContext = context?.applicationContext ?: return
        val viewModel = ViewModelProvider(requireParentFragment()).get(EvaluationSharedViewModel::class.java)
        val prefs = appContext.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
        val evaluadorEmail = prefs.getString("evaluador_email", "")

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val isTutorial = com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(ctx) ==
                com.example.minicex.ui.utils.TutorialManager.PHASE_STEP1

            val db = AppDatabase.getDatabase(appContext)
            val userLocal = db.userDao().getUserByEmail(evaluadorEmail ?: "")
            val evaluadorId = userLocal?.idUsuario ?: prefs.getInt("evaluador_id", -1)

            if (isTutorial) {
                // ── Ensure demo student exists ──────────────────────────────
                val existingStudents = db.studentDao().getStudentsForDocente(evaluadorId)
                val demoStudent = if (existingStudents.none { it.matricula == "DEMO001" }) {
                    val newDemo = StudentEntity(
                        idAlumno = 0, matricula = "DEMO001",
                        nombreCompleto = "Alumno Demo (Tutorial)",
                        semestreGrupo = "1° Semestre A",
                        correo = "demo@tutorial.mx",
                        idDocente = evaluadorId, isSynced = true
                    )
                    val insertedId = db.studentDao().insertStudent(newDemo).toInt()
                    newDemo.copy(idAlumno = insertedId)
                } else existingStudents.first { it.matricula == "DEMO001" }

                // ── Pre-fill ViewModel with demo data ──────────────────────
                viewModel.selectStudent(demoStudent)
                viewModel.setClinicalSetting("Ambulatorio")
                viewModel.setPatientType("Nuevo")
                viewModel.setComplexity("Media")
                viewModel.toggleMainIssue("Anamnesis", true)
                viewModel.toggleMainIssue("Diagnóstico", true)

                // ── Pre-fill rubric scores ─────────────────────────────────
                listOf(
                    "Anamnesis" to 8,
                    "Exploración Física" to 7,
                    "Profesionalismo" to 9,
                    "Juicio Clínico" to 8,
                    "Habilidades Comunicativas" to 8,
                    "Organización / Eficiencia" to 7,
                    "Valoración Global" to 8
                ).forEach { (comp, score) -> viewModel.setScore(comp, score) }
                listOf(
                    "Anamnesis" to "Historia clínica completa y ordenada.",
                    "Exploración Física" to "Exploración dirigida y segura.",
                    "Profesionalismo" to "Trato respetuoso y conducta profesional.",
                    "Juicio Clínico" to "Integra adecuadamente los hallazgos clínicos.",
                    "Habilidades Comunicativas" to "Explica con claridad y escucha activamente.",
                    "Organización / Eficiencia" to "Mantiene una secuencia eficiente durante la consulta.",
                    "Valoración Global" to "Desempeño clínico consistente para su nivel."
                ).forEach { (comp, note) -> viewModel.setNotes(comp, note) }

                // ── Pre-fill feedback ─────────────────────────────────────
                viewModel.setFeedback(
                    "Excelente manejo de la entrevista clínica. Buena empatía con el paciente.",
                    "Profundizar en la exploración física sistémica."
                )
                viewModel.setTimes(15, 10)
                viewModel.isObservationTimeManuallyEdited = true
                viewModel.isFeedbackTimeManuallyEdited = true
            }

            db.studentDao().getStudentsForDocenteFlow(evaluadorId).collect { studentList ->
                val displayNames = studentList.map { "${it.matricula} - ${it.nombreCompleto}" }.toTypedArray()

                if (_binding != null) {
                    setupDropdown(binding.acStudent, displayNames)
                    binding.acStudent.setOnItemClickListener { parent, _, position, _ ->
                        val selectedName = parent.getItemAtPosition(position) as? String
                        val selectedMatricula = selectedName?.substringBefore(" - ")
                        val selected = studentList.firstOrNull { it.matricula == selectedMatricula }
                        viewModel.selectStudent(selected)
                    }

                    // Restore selected student text (including pre-filled demo)
                    viewModel.selectedStudent.value?.let { current ->
                        if (studentList.any { it.matricula == current.matricula }) {
                            binding.acStudent.setText(
                                "${current.matricula} - ${current.nombreCompleto}", false)
                        }
                    }

                    // Restore chip selections from ViewModel
                    val currentIssues = viewModel.mainIssues.value ?: emptySet()
                    binding.chipAnamnesis.isChecked = "Anamnesis" in currentIssues
                    binding.chipDiagnosis.isChecked = "Diagnóstico" in currentIssues

                    // Show tutorial ONCE per view lifecycle
                    if (com.example.minicex.ui.utils.TutorialManager.getCurrentPhase(requireContext())
                        == com.example.minicex.ui.utils.TutorialManager.PHASE_STEP1
                        && !step1TutorialStarted) {
                        step1TutorialStarted = true
                        view?.postDelayed({ showStep1Tutorial() }, 600)
                    }
                }
            }
        }
    }

    private fun showStep1Tutorial() {
        if (!isAdded || _binding == null) return
        val ctx = requireContext()

        // Single overlay instance for this fragment
        val overlay = com.example.minicex.ui.utils.TutorialOverlay(requireActivity())

        val steps = listOf(
            Triple(binding.acStudent as View,
                "Selecciona al alumno",
                "Para este recorrido ya elegimos al Alumno Demo. En una evaluación real, busca aquí al estudiante por su matrícula o nombre."),
            Triple(binding.btnAddStudent as View,
                "Registra un alumno nuevo",
                "Si no aparece en la lista, agrégalo aquí. Puedes hacerlo incluso sin internet."),
            Triple(binding.acClinicalSetting as View,
                "Indica el entorno clínico",
                "Elige Ambulatorio, Intra hospitalario u Otros. Si eliges Otros, podrás escribir el entorno observado."),
            Triple(binding.acPatientType as View,
                "Indica el tipo de paciente",
                "Señala si fue un paciente nuevo o subsecuente."),
            Triple(binding.acComplexity as View,
                "Valora la complejidad",
                "Elige si el caso clínico fue de complejidad baja, media o alta."),
            Triple(binding.cgMainIssue as View,
                "Marca lo que observaste",
                "Selecciona uno o varios aspectos principales trabajados durante el encuentro clínico.")
        )

        fun showStep(index: Int) {
            if (!isAdded || _binding == null) return
            if (index >= steps.size) {
                overlay.dismiss()
                com.example.minicex.ui.utils.TutorialManager.advancePhase(ctx) // → PHASE_STEP2
                // ── AUTO-AVANZAR al paso 2 del wizard ──────────────────────
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(400)
                    if (_binding != null && isAdded) {
                        val evalFragment = parentFragment as? com.example.minicex.ui.evaluation.EvaluationFragment
                        evalFragment?.advanceToNextStep()
                    }
                }
                return
            }
            val (targetView, title, desc) = steps[index]
            overlay.show(
                targetView = targetView,
                stepNum = com.example.minicex.ui.utils.TutorialManager.START_STEP_STEP1 + index,
                title = title, description = desc,
                isLastStep = false,
                onNext = { showStep(index + 1) },
                onSkip = { com.example.minicex.ui.utils.TutorialManager.skipAll(ctx) }
            )
        }
        showStep(0)
    }

    private fun setupDropdown(view: android.widget.AutoCompleteTextView, items: Array<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        view.setAdapter(adapter)
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) view.showDropDown() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

