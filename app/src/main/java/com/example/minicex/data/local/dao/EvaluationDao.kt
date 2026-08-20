package com.example.minicex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.local.entity.RubricDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvaluationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(evaluation: EvaluationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRubricDetails(details: List<RubricDetailEntity>)

    @Query("SELECT * FROM evaluaciones ORDER BY fecha_evaluacion DESC")
    fun getAllEvaluations(): Flow<List<EvaluationEntity>>

    @Query("SELECT * FROM evaluaciones WHERE id_evaluacion = :id")
    suspend fun getEvaluationById(id: Int): EvaluationEntity?

    @Query("SELECT * FROM detalles_rubrica WHERE id_evaluacion = :evaluationId")
    suspend fun getRubricDetailsForEvaluation(evaluationId: Int): List<RubricDetailEntity>

    @Query("UPDATE evaluaciones SET is_synced = :isSynced WHERE id_evaluacion = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)

    @Query("SELECT * FROM evaluaciones WHERE is_synced = 0")
    suspend fun getUnsyncedEvaluations(): List<EvaluationEntity>

    @Query("UPDATE evaluaciones SET is_synced = 1 WHERE id_evaluacion = :id")
    suspend fun markAsSynced(id: Int)

    @Query("SELECT * FROM evaluaciones WHERE uuid = :uuid LIMIT 1")
    suspend fun getEvaluationByUuid(uuid: String): EvaluationEntity?

    @Query("DELETE FROM detalles_rubrica WHERE id_evaluacion = :evaluationId")
    suspend fun deleteRubricDetailsForEvaluation(evaluationId: Int)

    @Query("DELETE FROM detalles_rubrica WHERE id_evaluacion IN (SELECT id_evaluacion FROM evaluaciones WHERE id_alumno = :studentId)")
    suspend fun deleteRubricDetailsForStudent(studentId: Int)

    @Query("DELETE FROM evaluaciones WHERE id_alumno = :studentId")
    suspend fun deleteEvaluationsForStudent(studentId: Int)

    @Query("SELECT * FROM evaluaciones WHERE email_pending = 1")
    suspend fun getPendingEmailEvaluations(): List<EvaluationEntity>

    @Query("UPDATE evaluaciones SET email_pending = :pending WHERE id_evaluacion = :id")
    suspend fun updateEmailPendingStatus(id: Int, pending: Boolean)

    @Query("SELECT * FROM evaluaciones WHERE id_evaluador = :evaluadorId")
    suspend fun getEvaluationsForEvaluator(evaluadorId: Int): List<EvaluationEntity>

    @Query("SELECT * FROM evaluaciones WHERE id_evaluador = :evaluadorId ORDER BY fecha_evaluacion DESC")
    fun getEvaluationsForEvaluatorFlow(evaluadorId: Int): Flow<List<EvaluationEntity>>

    @androidx.room.Delete
    suspend fun deleteEvaluation(evaluation: EvaluationEntity)
}

