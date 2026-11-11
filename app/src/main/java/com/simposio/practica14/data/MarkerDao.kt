package com.simposio.practica14.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Insert
    suspend fun insert(marker: MarkerEntity)

    // El Flow se actualiza cuando cambia la tabla
    @Query("SELECT * FROM markers")
    fun getAll(): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getById(id: Int): MarkerEntity?

    @Update
    suspend fun update(marker: MarkerEntity)

    @Delete
    suspend fun delete(marker: MarkerEntity)

    @Query("DELETE FROM markers")
    suspend fun deleteAll()
}
