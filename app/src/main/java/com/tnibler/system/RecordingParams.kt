package com.tnibler.system.video

import android.util.Size


data class RecordingParams(
    val mode: String,
    val resolution: Size,
    val codec: String,
    val fps: Int,
    val useUltraWide: Boolean,
    val isOisEnabled: Boolean,
    val isEisEnabled: Boolean
)