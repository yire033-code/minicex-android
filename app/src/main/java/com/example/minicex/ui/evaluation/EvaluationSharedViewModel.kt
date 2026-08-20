package com.example.minicex.ui.evaluation

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.local.entity.RubricDetailEntity
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.local.entity.UserEntity
import com.example.minicex.data.local.entity.SyncQueueEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EvaluationSharedViewModel : ViewModel() {

    // Step 1: Datos Generales
    val selectedStudent = MutableLiveData<StudentEntity?>()
    val clinicalSetting = MutableLiveData<String>()
    val patientType = MutableLiveData<String>()
    val mainIssues = MutableLiveData<Set<String>>(emptySet())
    val complexity = MutableLiveData<String>()

    // Step 2: Rúbrica
    val scores = MutableLiveData<Map<String, Int>>(emptyMap())
    val notes = MutableLiveData<Map<String, String>>(emptyMap())

    // Step 3: Feedback y Tiempos
    val strengths = MutableLiveData<String>()
    val toImprove = MutableLiveData<String>()
    val observationTime = MutableLiveData<Int>()
    val feedbackTime = MutableLiveData<Int>()

    // Timing states
    var rubricStartTime: Long = 0L
    var feedbackStartTime: Long = 0L
    var isObservationTimeManuallyEdited: Boolean = false
    var isFeedbackTimeManuallyEdited: Boolean = false

    fun getStep1MissingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (selectedStudent.value == null) missing.add("Alumno")
        if (clinicalSetting.value.isNullOrBlank()) missing.add("Entorno Clínico")
        if (patientType.value.isNullOrBlank()) missing.add("Tipo de Paciente")
        if (complexity.value.isNullOrBlank()) missing.add("Complejidad")
        if (mainIssues.value.isNullOrEmpty()) missing.add("Asunto Principal")
        return missing
    }

    fun isStep1Complete(): Boolean {
        return getStep1MissingFields().isEmpty()
    }

    fun getStep2MissingFields(): List<String> {
        val competencies = listOf(
            "Anamnesis",
            "Exploración Física",
            "Profesionalismo",
            "Juicio Clínico",
            "Habilidades Comunicativas",
            "Organización / Eficiencia",
            "Valoración Global"
        )
        val currentScores = scores.value ?: emptyMap()
        val currentNotes = notes.value ?: emptyMap()
        return competencies.flatMap { competency ->
            buildList {
                if (!currentScores.containsKey(competency)) add("Calificación de $competency")
                if (currentNotes[competency].isNullOrBlank()) add("Notas de $competency")
            }
        }
    }

    fun isStep2Complete(): Boolean {
        return getStep2MissingFields().isEmpty()
    }

    fun getStep3MissingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (strengths.value.isNullOrBlank()) missing.add("Aspectos a destacar")
        if (toImprove.value.isNullOrBlank()) missing.add("Aspectos a mejorar")
        if ((observationTime.value ?: 0) <= 0) missing.add("Tiempo de Observación")
        if ((feedbackTime.value ?: 0) <= 0) missing.add("Tiempo de Feedback")
        return missing
    }

    fun isStep3Complete(): Boolean {
        return getStep3MissingFields().isEmpty()
    }

    // Step 4: Firmas
    val evaluatorSignatureBase64 = MutableLiveData<String?>()
    val studentSignatureBase64 = MutableLiveData<String?>()

    fun selectStudent(student: StudentEntity?) {
        selectedStudent.value = student
    }

    fun setClinicalSetting(setting: String) {
        clinicalSetting.value = setting
    }

    fun setPatientType(type: String) {
        patientType.value = type
    }

    fun toggleMainIssue(issue: String, isSelected: Boolean) {
        val current = mainIssues.value ?: emptySet()
        val updated = current.toMutableSet()
        if (isSelected) {
            updated.add(issue)
        } else {
            updated.remove(issue)
        }
        mainIssues.value = updated
    }

    fun setComplexity(comp: String) {
        complexity.value = comp
    }

    fun setScore(competency: String, score: Int) {
        val current = scores.value ?: emptyMap()
        val updated = current.toMutableMap()
        updated[competency] = score
        scores.value = updated
    }

    fun setNotes(competency: String, note: String) {
        val current = notes.value ?: emptyMap()
        val updated = current.toMutableMap()
        updated[competency] = note
        notes.value = updated
    }

    fun setFeedback(strength: String, improve: String) {
        strengths.value = strength
        toImprove.value = improve
    }

    fun setTimes(obsTime: Int, fbTime: Int) {
        observationTime.value = obsTime
        feedbackTime.value = fbTime
    }

    fun setSignatures(evalSig: String?, studSig: String?) {
        evaluatorSignatureBase64.value = evalSig
        studentSignatureBase64.value = studSig
    }

    fun saveEvaluation(context: Context, evaluatorId: Int, onComplete: (Int?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val evalDao = db.evaluationDao()
                val userDao = db.userDao()
                val studentDao = db.studentDao()
 
                // 1. Dynamic User Autocuración to prevent Foreign Key constraint crashes
                if (userDao.getUserById(evaluatorId) == null) {
                    Log.d("EvaluationVM", "Autocuración: Evaluador ID $evaluatorId no encontrado localmente. Creando placeholder...")
                    val prefs = context.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
                    val userName = prefs.getString("evaluador_nombre", null) ?: "Evaluador local"
                    val userEmail = prefs.getString("evaluador_email", null)
                        ?: "usuario-local-$evaluatorId@invalid"
                    
                    userDao.insertUser(
                        UserEntity(
                            idUsuario = evaluatorId,
                            nombreCompleto = userName,
                            email = userEmail,
                            passwordHash = "",
                            rol = "Docente"
                        )
                    )
                }
 
                // 2. Resolve student from DB by matricula first to ensure we use the updated ID (in case background sync updated it)
                val currentStudent = selectedStudent.value?.let {
                    studentDao.getStudentByMatricula(it.matricula)
                } ?: selectedStudent.value

                val studentId = currentStudent?.idAlumno ?: 0
                if (studentId != 0 && currentStudent != null) {
                    if (studentDao.getStudentById(studentId) == null) {
                        Log.d("EvaluationVM", "Autocuración: Alumno ID $studentId no encontrado localmente. Re-insertando...")
                        studentDao.insertStudent(currentStudent)
                    }
                }
 
                val date = System.currentTimeMillis()
 
                val totalObtained = scores.value?.values?.sum() ?: 0
                val calTotal = (totalObtained.toDouble() * 100.0) / 63.0
 
                val mainIssuesStr = mainIssues.value?.joinToString(", ") ?: ""
 
                val isDemo = selectedStudent.value?.matricula == "DEMO001"
                
                val evaluation = EvaluationEntity(
                    idEvaluador = evaluatorId,
                    idAlumno = studentId,
                    fechaEvaluacion = date,
                    entornoClinico = clinicalSetting.value ?: "Otros",
                    tipoPaciente = patientType.value ?: "Nuevo",
                    asuntoPrincipal = mainIssuesStr,
                    complejidad = complexity.value ?: "Media",
                    tiempoObservacion = observationTime.value ?: 0,
                    tiempoFeedback = feedbackTime.value ?: 0,
                    calificacionTotal = calTotal,
                    firmaEvaluador = evaluatorSignatureBase64.value,
                    firmaAlumno = studentSignatureBase64.value,
                    isSynced = isDemo
                )
 
                val evaluationId = evalDao.insertEvaluation(evaluation).toInt()
 
                // Guardar desgloses de rúbrica
                val competencyNames = listOf(
                    "Anamnesis",
                    "Exploración Física",
                    "Profesionalismo",
                    "Juicio Clínico",
                    "Habilidades Comunicativas",
                    "Organización / Eficiencia",
                    "Valoración Global"
                )
 
                val detailsList = competencyNames.map { name ->
                    val score = scores.value?.get(name) ?: 0 // 0 = N/V
                    val noteText = notes.value?.get(name) ?: ""
 
                    // Guardar feedback general de Step 3 en "Valoración Global"
                    val aDest = if (name == "Valoración Global") strengths.value ?: "" else ""
                    val aMej = if (name == "Valoración Global") toImprove.value ?: "" else ""
 
                    RubricDetailEntity(
                        idEvaluacion = evaluationId,
                        competencia = name,
                        puntaje = score,
                        notas = noteText,
                        aDestacar = aDest,
                        aMejorar = aMej
                    )
                }
 
                evalDao.insertRubricDetails(detailsList)
 
                // Push event to sync queue only if it's not a demo
                if (!isDemo) {
                    val syncQueueDao = db.syncQueueDao()
                    val payloadMap = mapOf(
                        "evaluation" to evaluation,
                        "details" to detailsList
                    )
                    val payloadJson = com.google.gson.Gson().toJson(payloadMap)
                    syncQueueDao.insertSyncAction(
                        com.example.minicex.data.local.entity.SyncQueueEntity(
                            action = "insert",
                            tableName = "evaluaciones",
                            entityUuid = evaluation.uuid,
                            dataPayload = payloadJson
                        )
                    )
                }
 
                withContext(Dispatchers.Main) {
                    onComplete(evaluationId, null)
                }
            } catch (e: Exception) {
                Log.e("EvaluationVM", "Error fatal al guardar la evaluación localmente: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onComplete(null, "Error de Base de Datos al guardar: ${e.localizedMessage}")
                }
            }
        }
    }
}
