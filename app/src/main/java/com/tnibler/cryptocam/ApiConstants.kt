package com.tnibler.cryptocam

// Этот объект хранит все строки, используемые для управления сервисом через CLI.
object ApiConstants {
    // Названия действий (actions) для Intent
    const val ACTION_START = "CryptocamApiStart" // Нейтральное название, как в ТЗ
    const val ACTION_STOP = "CryptocamApiStop"

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