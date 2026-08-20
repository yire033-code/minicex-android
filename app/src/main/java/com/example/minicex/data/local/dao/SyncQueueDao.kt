package com.example.minicex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.minicex.data.local.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncAction(action: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY timestamp ASC")
    suspend fun getPendingActions(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'synced' WHERE id = :actionId")
    suspend fun markAsSynced(actionId: Int)

    @Query("DELETE FROM sync_queue WHERE status = 'synced'")
    suspend fun clearSyncedActions()

    @Query("DELETE FROM sync_queue WHERE dataPayload LIKE '%DEMO001%'")
    suspend fun deleteDemoQueueActions()
}
