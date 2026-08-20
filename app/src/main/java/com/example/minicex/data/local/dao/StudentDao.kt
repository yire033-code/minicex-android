package com.example.minicex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.minicex.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Query("SELECT * FROM alumnos ORDER BY nombre_completo ASC")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT * FROM alumnos ORDER BY nombre_completo ASC")
    fun getAllStudentsFlow(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM alumnos WHERE id_alumno = :id LIMIT 1")
    suspend fun getStudentById(id: Int): StudentEntity?

    @Query("SELECT COUNT(*) FROM alumnos")
    suspend fun getStudentCount(): Int

    @androidx.room.Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("SELECT * FROM alumnos WHERE is_synced = 0")
    suspend fun getUnsyncedStudents(): List<StudentEntity>

    @Query("UPDATE alumnos SET id_alumno = :newId, is_synced = 1 WHERE matricula = :matricula")
    suspend fun updateSyncedStudent(matricula: String, newId: Int)

    @Query("SELECT * FROM alumnos WHERE id_docente = :docenteId ORDER BY nombre_completo ASC")
    suspend fun getStudentsForDocente(docenteId: Int): List<StudentEntity>

    @Query("SELECT * FROM alumnos WHERE id_docente = :docenteId ORDER BY nombre_completo ASC")
    fun getStudentsForDocenteFlow(docenteId: Int): kotlinx.coroutines.flow.Flow<List<StudentEntity>>

    @Query("SELECT * FROM alumnos WHERE matricula = :matricula LIMIT 1")
    suspend fun getStudentByMatricula(matricula: String): StudentEntity?

    @Query("DELETE FROM alumnos WHERE matricula = :matricula")
    suspend fun deleteStudentByMatricula(matricula: String)
}
