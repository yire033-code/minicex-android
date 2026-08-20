package com.example.minicex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "alumnos")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_alumno") val idAlumno: Int = 0,
    @ColumnInfo(name = "uuid") val uuid: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "matricula") val matricula: String,
    @ColumnInfo(name = "nombre_completo") val nombreCompleto: String,
    @ColumnInfo(name = "semestre_grupo") val semestreGrupo: String,
    @ColumnInfo(name = "correo") val correo: String = "",
    @ColumnInfo(name = "id_docente") val idDocente: Int = 1,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = true
)

