package com.example.minicex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "evaluaciones",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id_usuario"],
            childColumns = ["id_evaluador"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id_alumno"],
            childColumns = ["id_alumno"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id_evaluador"]),
        Index(value = ["id_alumno"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_synced"])
    ]
)
data class EvaluationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_evaluacion") val idEvaluacion: Int = 0,
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "id_evaluador") val idEvaluador: Int,
    @ColumnInfo(name = "id_alumno") val idAlumno: Int,
    @ColumnInfo(name = "fecha_evaluacion") val fechaEvaluacion: Long, // Stored as Unix timestamp
    @ColumnInfo(name = "entorno_clinico") val entornoClinico: String,
    @ColumnInfo(name = "tipo_paciente") val tipoPaciente: String,
    @ColumnInfo(name = "asunto_principal") val asuntoPrincipal: String,
    @ColumnInfo(name = "complejidad") val complejidad: String,
    @ColumnInfo(name = "tiempo_observacion") val tiempoObservacion: Int,
    @ColumnInfo(name = "tiempo_feedback") val tiempoFeedback: Int,
    @ColumnInfo(name = "calificacion_total") val calificacionTotal: Double = 0.0,
    @ColumnInfo(name = "firma_evaluador") val firmaEvaluador: String?, // Base64 or local file path
    @ColumnInfo(name = "firma_alumno") val firmaAlumno: String?, // Base64 or local file path
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "email_pending") val emailPending: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

