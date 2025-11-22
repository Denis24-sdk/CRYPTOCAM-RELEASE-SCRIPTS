package com.tnibler.system.preference

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class CameraIdSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // Поле для ID дневного режима
        screen.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_DAY
            title = "Day Mode Camera ID"
            summary = "ID of the camera for 'day' mode (e.g., wide-angle)"
            dialogTitle = "Enter Camera ID"
            setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
        })

        // Поле для ID ночного режима
        screen.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_NIGHT
            title = "Night Mode Camera ID"
            summary = "ID of the camera for 'night' mode (e.g., main)"
            dialogTitle = "Enter Camera ID"
            setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
        })

        // Поле для ID фронтального режима
        screen.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_FRONT
            title = "Front Mode Camera ID"
            summary = "ID of the camera for 'front' mode"
            dialogTitle = "Enter Camera ID"
            setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
        })

        preferenceScreen = screen
    }

    companion object {
        const val PREF_CAMERA_ID_DAY = "pref_camera_id_day"
        const val PREF_CAMERA_ID_NIGHT = "pref_camera_id_night"
        const val PREF_CAMERA_ID_FRONT = "pref_camera_id_front"
    }
}