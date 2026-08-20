package com.example.minicex.data.repository

import android.content.Context
import android.util.Log
import com.example.minicex.data.local.AppDatabase
import com.example.minicex.data.local.dao.EvaluationDao
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.remote.ApiService
import com.example.minicex.data.remote.dto.SyncQueueDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(
    private val evaluationDao: EvaluationDao,
    private val apiService: ApiService,
    private val context: Context
) {

    suspend fun autoSync(isManual: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("minicex_prefs", Context.MODE_PRIVATE)
                val evaluadorEmail = prefs.getString("evaluador_email", "")

                if (evaluadorEmail.isNullOrEmpty()) {
                    Log.d("Sync", "Sincronización omitida: No hay ningún usuario autenticado (sin correo).")
                    return@withContext
                }

                val db = AppDatabase.getDatabase(context)
                val userLocal = db.userDao().getUserByEmail(evaluadorEmail)
                val evaluadorId = userLocal?.idUsuario ?: prefs.getInt("evaluador_id", -1)

                if (evaluadorId == -1) {
                    Log.d("Sync", "Sincronización omitida: ID de usuario no disponible.")
                    return@withContext
                }

                Log.d("Sync", "Iniciando sincronización por colas para el docente ID: $evaluadorId...")
                val syncQueueDao = db.syncQueueDao()
                val studentDao = db.studentDao()

                // 1. Obtener acciones pendientes de la cola local
                val pendingActions = syncQueueDao.getPendingActions()
                val queuePayload = pendingActions.map {
                    SyncQueueDto(
                        action = it.action,
                        tableName = it.tableName,
                        entityUuid = it.entityUuid,
                        dataPayload = it.dataPayload,
                        timestamp = it.timestamp
                    )
                }

                Log.d("Sync", "Enviando ${queuePayload.size} acciones pendientes al servidor.")

                // 2. Enviar cola al servidor y recibir acciones remotas
                val response = apiService.processQueue(evaluadorId, queuePayload)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val responseBody = response.body()!!

                    // 3. Marcar las acciones locales enviadas como sincronizadas
                    responseBody.processedIds?.let { processedIdxs ->
                        // The server doesn't know the local queue IDs, it just processes them in order.
                        // Or we can just mark all sent ones as synced assuming the server transaction succeeded.
                        pendingActions.forEach { action ->
                            syncQueueDao.markAsSynced(action.id)
                        }
                        // Opcional: limpiar acciones sincronizadas
                        syncQueueDao.clearSyncedActions()
                        Log.d("Sync", "Acciones locales marcadas como sincronizadas.")
                    }

                    // 4. Procesar acciones que vienen del servidor
                    val serverActions = responseBody.serverActions ?: emptyList()
                    val gson = Gson()
                    
                    for (serverAction in serverActions) {
                        try {
                            if (serverAction.tableName == "usuarios") {
                                if (serverAction.action == "delete") {
                                    // User deleted on server! Logout automatically
                                    prefs.edit().clear().apply()
                                    db.clearAllTables()
                                    
                                    val intent = android.content.Intent(context, com.example.minicex.MainActivity::class.java).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    context.startActivity(intent)
                                    Log.d("Sync", "Usuario eliminado del servidor. Cerrando sesión local.")
                                    return@withContext // Stop sync
                                } else if (serverAction.action == "update") {
                                    val typeToken = object : TypeToken<Map<String, Any>>() {}.type
                                    val payloadMap: Map<String, Any> = gson.fromJson(serverAction.dataPayload, typeToken)
                                    
                                    val id = (payloadMap["id_usuario"] as Double).toInt()
                                    val email = payloadMap["email"] as? String ?: ""
                                    
                                    val existingUser = db.userDao().getUserByEmail(email)
                                    if (existingUser != null && existingUser.idUsuario != id) {
                                        db.userDao().updateUserIdByEmail(email, id)
                                    }
                                    
                                    val user = com.example.minicex.data.local.entity.UserEntity(
                                        idUsuario = id,
                                        nombreCompleto = payloadMap["nombre_completo"] as? String ?: "",
                                        email = email,
                                        rol = payloadMap["rol"] as? String ?: "",
                                        passwordHash = payloadMap["password_hash"] as? String ?: ""
                                    )
                                    db.userDao().insertUser(user)
                                    
                                    // Actualizar el evaluador en preferencias SOLO si coincide con el usuario logueado actual (por correo)
                                    val currentEvaluadorEmail = prefs.getString("evaluador_email", "")
                                    if (email.equals(currentEvaluadorEmail, ignoreCase = true)) {
                                        prefs.edit()
                                            .putString("evaluador_nombre", user.nombreCompleto)
                                            .putString("evaluador_email", user.email)
                                            .putInt("evaluador_id", id)
                                            .apply()
                                    }
                                }
                            } else if (serverAction.tableName == "alumnos") {
                                val studentDto = gson.fromJson(serverAction.dataPayload, StudentEntity::class.java)
                                val localStudent = studentDao.getStudentByMatricula(studentDto.matricula)
                                
                                if (serverAction.action == "insert" || serverAction.action == "update") {
                                    if (localStudent != null) {
                                        // Update the Primary Key to match the server
                                        studentDao.updateSyncedStudent(localStudent.matricula, studentDto.idAlumno)
                                        val updatedStudent = studentDto.copy(isSynced = true)
                                        studentDao.insertStudent(updatedStudent)
                                    } else {
                                        val newStudent = studentDto.copy(idAlumno = 0, isSynced = true)
                                        studentDao.insertStudent(newStudent)
                                    }
                                } else if (serverAction.action == "delete") {
                                    if (localStudent != null) {
                                        studentDao.deleteStudent(localStudent)
                                    }
                                }
                            } else if (serverAction.tableName == "evaluaciones") {
                                // Evaluaciones: El payload es un Map con "evaluation" y "details"
                                val typeToken = object : TypeToken<Map<String, Any>>() {}.type
                                val payloadMap: Map<String, Any> = gson.fromJson(serverAction.dataPayload, typeToken)
                                
                                val evalJson = gson.toJson(payloadMap["evaluation"])
                                val evalDto = gson.fromJson(evalJson, EvaluationEntity::class.java)
                                
                                val existingEval = evaluationDao.getEvaluationByUuid(evalDto.uuid)
                                
                                if (serverAction.action == "insert" || serverAction.action == "update") {
                                    if (existingEval != null) {
                                        // Update not fully implemented in old system, but we can do it
                                        // Omitting for now to avoid overwriting local edits if not handled perfectly, 
                                        // or just marking as synced
                                        evaluationDao.markAsSynced(existingEval.idEvaluacion)
                                    } else {
                                        // Insert
                                        val studentMatricula = payloadMap["evaluation"].let { it as Map<String, Any> }["studentMatricula"] as? String ?: ""
                                        val localStudent = studentDao.getStudentByMatricula(studentMatricula)
                                        
                                        val finalIdAlumno = if (localStudent != null) {
                                            localStudent.idAlumno
                                        } else {
                                            val newStudent = StudentEntity(
                                                idAlumno = evalDto.idAlumno,
                                                uuid = java.util.UUID.randomUUID().toString(),
                                                matricula = studentMatricula.ifEmpty { "TEMP_${evalDto.idAlumno}" },
                                                nombreCompleto = "Estudiante Sincronizado",
                                                semestreGrupo = "N/A",
                                                correo = "",
                                                idDocente = evaluadorId,
                                                isSynced = true
                                            )
                                            studentDao.insertStudent(newStudent).toInt()
                                        }
                                        
                                        val newEval = evalDto.copy(idEvaluacion = 0, idAlumno = finalIdAlumno, isSynced = true)
                                        val newEvalId = evaluationDao.insertEvaluation(newEval).toInt()
                                        
                                        // Insert details
                                        val detailsJson = gson.toJson(payloadMap["details"])
                                        val detailsTypeToken = object : TypeToken<List<com.example.minicex.data.local.entity.RubricDetailEntity>>() {}.type
                                        val detailsList: List<com.example.minicex.data.local.entity.RubricDetailEntity> = gson.fromJson(detailsJson, detailsTypeToken)
                                        
                                        val cleanDetails = detailsList.map {
                                            it.copy(idDetalle = 0, idEvaluacion = newEvalId)
                                        }
                                        evaluationDao.insertRubricDetails(cleanDetails)
                                    }
                                } else if (serverAction.action == "delete") {
                                    if (existingEval != null) {
                                        evaluationDao.deleteRubricDetailsForEvaluation(existingEval.idEvaluacion)
                                        evaluationDao.deleteEvaluation(existingEval)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Sync", "Error procesando acción del servidor: ${e.message}")
                        }
                    }
                    Log.d("Sync", "Procesadas ${serverActions.size} acciones remotas.")

                } else {
                    Log.e("Sync", "Error en la respuesta de cola: ${response.message()}")
                }

                // 5. Reenvío de correos pendientes
                val pendingEmails = evaluationDao.getPendingEmailEvaluations()
                if (pendingEmails.isNotEmpty()) {
                    Log.d("Sync", "Procesando ${pendingEmails.size} reenvíos de correo...")
                    for (pending in pendingEmails) {
                        try {
                            val res = apiService.resendEmail(com.example.minicex.data.remote.dto.ResendEmailRequest(pending.uuid))
                            if (res.isSuccessful && res.body()?.success == true) {
                                evaluationDao.updateEmailPendingStatus(pending.idEvaluacion, false)
                            }
                        } catch (e: Exception) {
                            Log.e("Sync", "Error en reenvío email: ${e.message}")
                        }
                    }
                }
                
                Log.d("Sync", "Sincronización por colas finalizada.")
            } catch (e: Exception) {
                Log.e("Sync", "Error crítico en autoSync: ${e.message}")
            }
        }
    }
}
