package com.tnibler.cryptocam.photo.photoviewer

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

interface PhotoProvider {
    val photo: StateFlow<Bitmap?>
}