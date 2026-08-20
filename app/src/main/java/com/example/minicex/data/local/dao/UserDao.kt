package com.example.minicex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.minicex.data.local.entity.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE id_usuario = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun getUserCount(): Int

    @Query("UPDATE usuarios SET id_usuario = :newId WHERE email = :email")
    suspend fun updateUserIdByEmail(email: String, newId: Int)
}
