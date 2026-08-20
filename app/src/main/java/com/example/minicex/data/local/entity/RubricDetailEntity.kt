package com.example.minicex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "detalles_rubrica",
    foreignKeys = [
        ForeignKey(
            entity = EvaluationEntity::class,
            parentColumns = ["id_evaluacion"],
            childColumns = ["id_evaluacion"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id_evaluacion"])
    ]
)
data class RubricDetailEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_detalle") val idDetalle: Int = 0,
    @ColumnInfo(name = "id_evaluacion") val idEvaluacion: Int,
    @ColumnInfo(name = "competencia") val competencia: String,
    @ColumnInfo(name = "puntaje") val puntaje: Int = 0, // 0 = N/V
    @ColumnInfo(name = "notas") val notas: String?,
    @ColumnInfo(name = "a_destacar") val aDestacar: String?,
    @ColumnInfo(name = "a_mejorar") val aMejorar: String?
)
