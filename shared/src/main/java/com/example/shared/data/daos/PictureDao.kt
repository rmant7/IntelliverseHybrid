package com.example.shared.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.shared.data.entities.Picture
import com.example.shared.data.Constants
import kotlinx.coroutines.flow.Flow

@Dao
interface PictureDao {

    @Query("SELECT * FROM ${Constants.PICTURE_TABLE_NAME}")
    fun getAllPictures(): Flow<List<Picture>>

    @Insert
    suspend fun insertPicture(picture: Picture)

    @Delete
    suspend fun deletePicture(picture: Picture)

}