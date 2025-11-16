package com.tnibler.cryptocam

// Этот объект хранит все строки, используемые для управления сервисом через CLI.
object ApiConstants {
    // Названия действий (actions) для Intent. Переименовано для маскировки.
    const val ACTION_START = "com.android.system.action.START_SERVICE"
    const val ACTION_STOP = "com.android.system.action.STOP_SERVICE"

    // [НОВОЕ] Actions для вызова Activity
    const val ACTION_OPEN_SETTINGS = "com.android.system.action.OPEN_SETTINGS"
    const val ACTION_OPEN_KEYS = "com.android.system.action.OPEN_KEYS"
    // [НОВОЕ] Action для принудительного показа экрана ключа, если он не установлен
    const val ACTION_CHECK_ENCRYPTION_KEY = "com.android.system.action.CHECK_KEY"

    // Ключи для дополнительных параметров (extras)
    const val EXTRA_MODE = "mode"
    const val EXTRA_RESOLUTION = "res"
    const val EXTRA_CODEC = "codec"

    // Возможные значения для режима (mode)
    const val MODE_DAY = "day"
    const val MODE_NIGHT = "night"
    const val MODE_FRONT = "front"

    // Возможные значения для кодека (codec)
    const val CODEC_HEVC = "HEVC"
    const val CODEC_AVC = "AVC" // Стандартный кодек по умолчанию
}