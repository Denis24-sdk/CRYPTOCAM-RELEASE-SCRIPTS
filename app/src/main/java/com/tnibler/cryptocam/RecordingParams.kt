package com.tnibler.cryptocam.video

import android.util.Size

// Этот data-класс будет содержать все настройки для текущей сессии записи.
// Мы будем создавать его на основе параметров из CLI-команды.
data class RecordingParams(
    val mode: String,
    val resolution: Size,
    val codec: String,
    // Позже мы добавим сюда FPS, стабилизацию и другие настройки из ТЗ.
)