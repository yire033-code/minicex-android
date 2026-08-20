package com.example.minicex.data.remote.dto

import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.local.entity.RubricDetailEntity

data class EvaluationSyncDto(
    val evaluation: EvaluationEntity,
    val details: List<RubricDetailEntity>
)

data class SyncResponse(
    val success: Boolean,
    val message: String,
    val syncedUuids: List<String>
)

data class StudentDto(
    val id_alumno: Int,
    val matricula: String,
    val nombre_completo: String,
    val semestre_grupo: String,
    val correo: String? = ""
)

data class StudentSyncDto(
    val matricula: String,
    val nombre_completo: String,
    val semestre_grupo: String,
    val id_docente: Int,
    val correo: String = ""
)

data class SyncedStudentItem(
    val matricula: String,
    val id_alumno: Int
)

data class StudentSyncResponse(
    val success: Boolean,
    val message: String?,
    val synced: List<SyncedStudentItem>?
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginUser(
    val id_usuario: Int,
    val nombre_completo: String,
    val email: String,
    val rol: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val user: LoginUser?
)

data class ResendEmailRequest(
    val uuid: String
)

data class SimpleResponse(
    val success: Boolean,
    val message: String
)

data class SyncQueueDto(
    val action: String,
    val tableName: String,
    val entityUuid: String,
    val dataPayload: String,
    val timestamp: Long
)

data class SyncQueueResponse(
    val success: Boolean,
    val message: String?,
    val processedIds: List<Int>?,
    val serverActions: List<SyncQueueDto>?
)

// ── Report DTOs (read-only, online-only) ──────────────────────────────────────

data class StudentReportDto(
    val idAlumno: Int,
    val uuid: String,
    val matricula: String,
    val nombreCompleto: String,
    val semestreGrupo: String,
    val correo: String?,
    val idDocente: Int,
    val docenteNombre: String?
)

data class ReportRubricDetail(
    val idDetalle: Int,
    val idEvaluacion: Int,
    val competencia: String,
    val puntaje: Int,
    val notas: String?,
    val aDestacar: String?,
    val aMejorar: String?
)

data class ReportEvaluation(
    val idEvaluacion: Int,
    val uuid: String,
    val idEvaluador: Int,
    val idAlumno: Int,
    val fechaEvaluacion: String,
    val entornoClinico: String,
    val tipoPaciente: String,
    val asuntoPrincipal: String,
    val complejidad: String,
    val tiempoObservacion: Int,
    val tiempoFeedback: Int,
    val calificacionTotal: Double,
    val evaluadorNombre: String?,
    val detalles: List<ReportRubricDetail>
)

data class ReportCompetencia(
    val competencia: String,
    val promedio: Double,
    val count: Int
)

data class ReportComplejidad(
    val complejidad: String,
    val count: Int
)

data class ReportIndices(
    val totalEvaluaciones: Int,
    val promedio: Double,
    val promedioDisplay: Double,
    val trend: Double,
    val trendText: String?,
    val competenciaFuerte: String?,
    val competenciaDebil: String?,
    val consistencia: Double,
    val consistenciaText: String?,
    val progreso: Double,
    val progresoText: String?,
    val topAreasMejora: Map<String, Int>?
)

data class StudentReportResponse(
    val success: Boolean,
    val message: String?,
    val student: StudentReportDto?,
    val evaluaciones: List<ReportEvaluation>?,
    val competencias: List<ReportCompetencia>?,
    val complejidad: List<ReportComplejidad>?,
    val indices: ReportIndices?
)

// ── Teacher Summary DTOs (read-only, online-only) ──────────────────────────────

data class TeacherSummaryDocente(
    val idUsuario: Int,
    val nombreCompleto: String,
    val email: String
)

data class TeacherSummaryResumen(
    val totalAlumnos: Int,
    val alumnosConEvaluaciones: Int,
    val totalEvaluaciones: Int,
    val promedioGeneral: Double
)

data class StudentSummaryIndices(
    val totalEvaluaciones: Int,
    val promedio: Double,
    val promedioDisplay: Double,
    val trend: Double,
    val trendText: String?,
    val competenciaFuerte: String?,
    val competenciaDebil: String?,
    val consistencia: Double,
    val consistenciaText: String?,
    val progreso: Double,
    val progresoText: String?,
    val topAreasMejora: Map<String, Int>?
)

data class StudentSummaryItem(
    val idAlumno: Int,
    val matricula: String,
    val nombreCompleto: String,
    val semestreGrupo: String,
    val indices: StudentSummaryIndices,
    val evaluaciones: List<ReportEvaluation>?,
    val competencias: List<ReportCompetencia>?,
    val complejidad: List<ReportComplejidad>?
)

data class TeacherSummaryResponse(
    val success: Boolean,
    val message: String?,
    val docente: TeacherSummaryDocente?,
    val modo: String?,
    val resumen: TeacherSummaryResumen?,
    val alumnos: List<StudentSummaryItem>?
)


