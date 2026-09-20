package com.example.shared.data.repositories

import com.example.shared.data.daos.PictureDao
import com.example.shared.data.entities.Picture
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ViewModelScoped
class PictureRepository @Inject constructor(
    private val pictureDao: PictureDao
) {
    val allPictures: Flow<List<Picture>> = pictureDao.getAllPictures()

    suspend fun insertPicture(picture: Picture) {
        pictureDao.insertPicture(picture)
    }

    suspend fun deletePicture(picture: Picture) {
        pictureDao.deletePicture(picture)

    }

}