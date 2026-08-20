package com.example.minicex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String, // "insert", "update", "delete"
    val tableName: String, // "alumnos", "evaluaciones"
    val entityUuid: String, // UUID of the record being modified
    val dataPayload: String, // JSON payload representing the data
    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis()
)
