package com.example.shared.presentation.permissions

import android.Manifest


class PermissionSet {

    fun getCameraPermissionSet(): Array<String> {
        // Camera permission only – no media/storage read permissions
        return arrayOf(
            Manifest.permission.CAMERA
        )
    }
}