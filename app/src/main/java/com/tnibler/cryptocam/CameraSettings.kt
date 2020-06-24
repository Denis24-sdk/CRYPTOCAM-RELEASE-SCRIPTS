package com.tnibler.cryptocam

import android.content.SharedPreferences
import androidx.camera.core.AspectRatio

class CameraSettings(private val sharedPreferences: SharedPreferences) {
    val aspectRatio: Int = AspectRatio.RATIO_16_9
    val bitrate: Int = 10000000
    val frameRate: Int = sharedPreferences.getString(com.tnibler.cryptocam.preference.SettingsFragment.PREF_FRAMERATE, "30")?.toInt() ?: 30
}
