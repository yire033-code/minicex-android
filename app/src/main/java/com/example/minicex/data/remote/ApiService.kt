package com.example.minicex.data.remote

import com.example.minicex.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {
    @GET("students")
    suspend fun getStudents(@retrofit2.http.Query("evaluador_id") evaluadorId: Int): Response<List<StudentDto>>

    @GET("sync/evaluations")
    suspend fun getEvaluations(@retrofit2.http.Query("evaluador_id") evaluadorId: Int): Response<List<EvaluationSyncDto>>

    @POST("sync/evaluations")
    suspend fun uploadEvaluations(@Body evaluations: List<EvaluationSyncDto>): Response<SyncResponse>

    @POST("sync/students")
    suspend fun uploadStudents(@Body students: List<StudentSyncDto>): Response<StudentSyncResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("sync/resend-email")
    suspend fun resendEmail(@Body request: ResendEmailRequest): Response<SimpleResponse>

    @POST("sync/process_queue")
    suspend fun processQueue(
        @retrofit2.http.Query("evaluador_id") evaluadorId: Int,
        @Body queue: List<SyncQueueDto>
    ): Response<SyncQueueResponse>

    // Read-only report endpoint (online-only)
    @GET("reports/student/{studentId}")
    suspend fun getStudentReport(
        @retrofit2.http.Path("studentId") studentId: Int
    ): Response<StudentReportResponse>

    // Teacher summary report (online-only)
    @GET("reports/teacher-summary")
    suspend fun getTeacherSummary(
        @retrofit2.http.Query("evaluador_id") evaluadorId: Int,
        @retrofit2.http.Query("modo") modo: String = "mine"
    ): Response<TeacherSummaryResponse>

    @GET("update")
    suspend fun checkForUpdates(): Response<AppUpdateDto>

    // ── Export endpoints (server-generated files, online-only) ────────────

    // PDF oficial de evaluación por UUID
    @Streaming
    @GET("reports/evaluation/download-pdf")
    suspend fun downloadEvaluationPdf(
        @Query("uuid") uuid: String,
        @Query("download") download: Int = 1
    ): Response<ResponseBody>

    // Excel (.xlsx) del historial del alumno por UUID
    @Streaming
    @GET("reports/student/download-xlsx")
    suspend fun downloadStudentXlsx(
        @Query("uuid") uuid: String
    ): Response<ResponseBody>

    // Excel (.xlsx) consolidado por docente usando su EMAIL
    @Streaming
    @GET("reports/teacher-summary/download-xlsx")
    suspend fun downloadTeacherSummaryXlsx(
        @Query("email") email: String,
        @Query("modo") modo: String = "mine"
    ): Response<ResponseBody>

    // CSV plano del resumen del docente usando su EMAIL
    @Streaming
    @GET("reports/teacher-summary/export")
    suspend fun downloadTeacherSummaryCsv(
        @Query("email") email: String,
        @Query("modo") modo: String = "mine"
    ): Response<ResponseBody>
}
