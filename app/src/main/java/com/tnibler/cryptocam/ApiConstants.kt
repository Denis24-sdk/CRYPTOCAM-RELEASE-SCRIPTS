package com.tnibler.cryptocam

object ApiConstants {
    const val ACTION_START = "com.android.system.action.START_SERVICE"
    const val ACTION_STOP = "com.android.system.action.STOP_SERVICE"

    const val ACTION_OPEN_OUTPUT_PICKER = "com.android.system.action.PICK_OUTPUT"
    const val ACTION_FORCE_OUTPUT_PICKER = "com.android.system.action.FORCE_PICK_OUTPUT"

    const val ACTION_OPEN_SETTINGS = "com.android.system.action.OPEN_SETTINGS"
    const val ACTION_OPEN_KEYS = "com.android.system.action.OPEN_KEYS"
    const val ACTION_CHECK_ENCRYPTION_KEY = "com.android.system.action.CHECK_KEY"

    const val EXTRA_MODE = "mode"
    const val EXTRA_RESOLUTION = "res"
    const val EXTRA_CODEC = "codec"

    const val MODE_DAY = "day"
    const val MODE_NIGHT = "night"
    const val MODE_FRONT = "front"

    const val CODEC_HEVC = "HEVC"
    const val CODEC_AVC = "AVC"
}