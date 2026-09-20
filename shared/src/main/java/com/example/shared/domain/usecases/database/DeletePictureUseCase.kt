package com.example.shared.domain.usecases.database

import com.example.shared.data.entities.Picture
import com.example.shared.data.repositories.PictureRepository
import javax.inject.Inject

class DeletePictureUseCase @Inject constructor(
    private val pictureRepository: PictureRepository
) {
    suspend operator fun invoke(picture: Picture) {
        pictureRepository.deletePicture(picture)
    }
}