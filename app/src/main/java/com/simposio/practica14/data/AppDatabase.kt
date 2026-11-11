package com.simposio.practica14.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MarkerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun markerDao(): MarkerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Garantiza una sola instancia de la base de datos
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "practica14.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
