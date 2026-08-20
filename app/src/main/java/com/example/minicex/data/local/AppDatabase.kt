package com.example.minicex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.minicex.data.local.dao.EvaluationDao
import com.example.minicex.data.local.dao.StudentDao
import com.example.minicex.data.local.dao.UserDao
import com.example.minicex.data.local.entity.EvaluationEntity
import com.example.minicex.data.local.entity.RubricDetailEntity
import com.example.minicex.data.local.entity.StudentEntity
import com.example.minicex.data.local.entity.UserEntity
import com.example.minicex.data.local.dao.SyncQueueDao
import com.example.minicex.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        UserEntity::class,
        StudentEntity::class,
        EvaluationEntity::class,
        RubricDetailEntity::class,
        SyncQueueEntity::class
    ],
    version = 8,

    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun evaluationDao(): EvaluationDao
    abstract fun userDao(): UserDao
    abstract fun studentDao(): StudentDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minicex_local_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

