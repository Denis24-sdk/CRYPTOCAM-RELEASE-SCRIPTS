package com.tnibler.cryptocam.preference

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.*
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.keys.keyList.KeysKey
import com.zhuinden.simplestackextensions.fragmentsktx.backstack

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // --- КАТЕГОРИЯ ДЛЯ РУЧНОЙ НАСТРОЙКИ ID КАМЕР ---
        val cameraCategory = PreferenceCategory(context).apply {
            title = "Camera ID Assignments"
            summary = "Optional: Manually assign camera IDs for each mode. Leave blank to use auto-detection."
        }
        screen.addPreference(cameraCategory)

        cameraCategory.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_DAY
            title = "Day Mode Camera ID"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter Camera ID for Day Mode (e.g., wide-angle)"
            setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
        })

        cameraCategory.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_NIGHT
            title = "Night Mode Camera ID"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter Camera ID for Night Mode (e.g., main)"
            setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
        })

        cameraCategory.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_FRONT
            title = "Front Mode Camera ID"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter Camera ID for Front Mode"
            setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
        })


        // --- НОВЫЙ БЛОК: СПИСОК ДОСТУПНЫХ КАМЕР ---
        val availableCamerasCategory = PreferenceCategory(context).apply {
            title = "Available Cameras"
            summary = "List of all cameras detected on this device. Use these IDs for manual assignment above."
        }
        screen.addPreference(availableCamerasCategory)

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList

            if (cameraIds.isEmpty()) {
                availableCamerasCategory.addPreference(Preference(context).apply {
                    title = "No cameras found on this device"
                    isEnabled = false
                })
            } else {
                cameraIds.forEach { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val lensFacing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                        CameraCharacteristics.LENS_FACING_BACK -> "Back"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                        else -> "Unknown"
                    }
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val summaryText = "Lens: $lensFacing | Focal lengths: ${focalLengths?.joinToString() ?: "N/A"}"

                    availableCamerasCategory.addPreference(Preference(context).apply {
                        title = "Camera ID: $id"
                        summary = summaryText
                        isSelectable = false // элемент некликабельный
                    })
                }
            }
        } catch (e: Exception) {
            availableCamerasCategory.addPreference(Preference(context).apply {
                title = "Error accessing cameras"
                summary = e.message
                isEnabled = false
            })
        }


        // --- ОБЩИЕ НАСТРОЙКИ ---
        val generalCategory = PreferenceCategory(context).apply { title = "General Settings" }
        screen.addPreference(generalCategory)

        val requestDirectory = registerForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                return super.createIntent(context, input).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
            }
        }) { uri ->
            uri ?: return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            requireNotNull(preferenceManager.sharedPreferences).edit {
                putString(PREF_OUTPUT_DIRECTORY, uri.toString())
                commit()
            }
            val dir = DocumentFile.fromTreeUri(requireContext(), uri)
            if (dir?.findFile(".nomedia") == null) {
                dir?.createFile("application/octet-stream", ".nomedia")
            }
        }

        val outputDirPreference = Preference(context).apply {
            key = PREF_OUTPUT_DIRECTORY
            title = getString(R.string.pref_output_directory)
            summary = getString(R.string.pref_output_directory_summary)
            setIcon(R.drawable.ic_folder)
            setOnPreferenceClickListener {
                requestDirectory.launch(null)
                true
            }
        }
        generalCategory.addPreference(outputDirPreference)

        val keyPreference = Preference(context).apply {
            key = PREF_SELECTED_RECIPIENTS
            title = getString(R.string.pref_keys)
            summary = getString(R.string.pref_keys_summary)
            setIcon(R.drawable.ic_key)
            setOnPreferenceClickListener {
                backstack.goTo(KeysKey())
                true
            }
        }
        generalCategory.addPreference(keyPreference)

        val fileNamePreference = EditTextPreference(context).apply {
            setDefaultValue("cryptocam-\$\$num.age")
            key = PREF_OUTPUT_FILE_NAME
            title = getString(R.string.output_file_name)
            summary = getString(R.string.filename_pattern_description) + getString(R.string.filename_pattern_variables)
        }
        generalCategory.addPreference(fileNamePreference)


        // --- НАСТРОЙКИ ПОВЕДЕНИЯ ---
        val behaviorCategory = PreferenceCategory(context).apply { title = "Behavior" }
        screen.addPreference(behaviorCategory)

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            key = PREF_VIBRATE_ON_START
            title = "Vibrate on start"
            summary = "Short vibration when recording starts"
            setDefaultValue(true)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            key = PREF_VIBRATE_ON_STOP
            title = "Vibrate on stop"
            summary = "Double vibration when recording stops"
            setDefaultValue(true)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(true)
            key = PREF_VIBRATE_WHILE_RECORDING
            title = getString(R.string.vibrate_while_recording)
            summary = getString(R.string.vibrate_while_recording_summary)
        })

        preferenceScreen = screen
    }

    companion object {
        const val PREF_CAMERA_ID_DAY = "pref_camera_id_day"
        const val PREF_CAMERA_ID_NIGHT = "pref_camera_id_night"
        const val PREF_CAMERA_ID_FRONT = "pref_camera_id_front"
        const val PREF_OUTPUT_DIRECTORY = "mediaOutputLocation"
        const val PREF_VIDEO_RESOLUTION = "videoResolution"
        const val PREF_VIBRATE_WHILE_RECORDING = "vibrateWhileRecording"
        const val PREF_SELECTED_RECIPIENTS = "selectedX25519Recipients"
        const val PREF_OUTPUT_FILE_NAME = "outputFileName"

        const val PREF_VIBRATE_ON_START = "pref_vibrate_on_start"
        const val PREF_VIBRATE_ON_STOP = "pref_vibrate_on_stop"

        const val DEFAULT_RESOLUTION = "1920x1080"
    }
}
