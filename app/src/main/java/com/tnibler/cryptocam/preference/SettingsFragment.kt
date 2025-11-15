package com.tnibler.cryptocam.preference

import android.content.Context
import android.content.Intent
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
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance() // Показывает текущее значение
            dialogTitle = "Enter Camera ID for Day Mode (e.g., wide-angle)"
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        })

        cameraCategory.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_NIGHT
            title = "Night Mode Camera ID"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter Camera ID for Night Mode (e.g., main)"
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        })

        cameraCategory.addPreference(EditTextPreference(context).apply {
            key = PREF_CAMERA_ID_FRONT
            title = "Front Mode Camera ID"
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            dialogTitle = "Enter Camera ID for Front Mode"
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        })

        // --- ОБЩИЕ НАСТРОЙКИ ---
        val generalCategory = PreferenceCategory(context).apply {
            title = "General Settings"
        }
        screen.addPreference(generalCategory)

        val requestDirectory =
            registerForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
                override fun createIntent(context: Context, input: Uri?): Intent {
                    return super.createIntent(context, input).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    }
                }
            }) { uri ->
                uri ?: return@registerForActivityResult
                requireNotNull(preferenceManager.sharedPreferences).edit {
                    putString(PREF_OUTPUT_DIRECTORY, uri.toString())
                    commit()
                }
                val dir = DocumentFile.fromTreeUri(requireContext(), uri)
                if (dir?.findFile(".nomedia") == null) {
                    dir?.createFile("asd/asd", ".nomedia")
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

        val fpsPreference = ListPreference(context).apply {
            setDefaultValue("30")
            entries = arrayOf("30 fps", "60 fps")
            entryValues = arrayOf("30", "60")
            title = getString(R.string.video_framerate)
            key = PREF_FRAMERATE
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            isPersistent = true
            setIcon(R.drawable.ic_timer)
        }
        generalCategory.addPreference(fpsPreference)

        val resolutionPreference = ListPreference(context).apply {
            setDefaultValue(DEFAULT_RESOLUTION)
            entries = arrayOf("1280x720", "1920x1080", "3840x2160")
            entryValues = entries
            title = getString(R.string.video_resolution)
            key = PREF_VIDEO_RESOLUTION
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        generalCategory.addPreference(resolutionPreference)

        val fileNamePreference = EditTextPreference(context).apply {
            setDefaultValue("cryptocam-\$num.age")
            key = "outputFileName"
            title = getString(R.string.output_file_name)
            summary = getString(R.string.filename_pattern_description) + getString(R.string.filename_pattern_variables)
        }
        generalCategory.addPreference(fileNamePreference)

        // --- НАСТРОЙКИ ПОВЕДЕНИЯ ---
        val behaviorCategory = PreferenceCategory(context).apply {
            title = "Behavior"
        }
        screen.addPreference(behaviorCategory)

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_REMEMBER_SHOOTING_MODE
            title = getString(R.string.remember_shooting_mode)
            summary = getString(R.string.remember_shooting_mode_summary)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_REMOVE_EXIF
            title = getString(R.string.remove_exif_data)
            summary = getString(R.string.remove_exif_data_summary)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_RECORD_ON_START
            title = getString(R.string.record_on_start)
            summary = getString(R.string.record_on_start_summary)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(true)
            key = PREF_VIBRATE_WHILE_RECORDING
            title = getString(R.string.vibrate_while_recording)
            summary = getString(R.string.vibrate_while_recording_summary)
        })

        behaviorCategory.addPreference(CheckBoxPreference(context).apply {
            setDefaultValue(true)
            key = PREF_VIBRATE_ON_PHOTO
            title = getString(R.string.vibrate_on_photo)
            summary = getString(R.string.vibrate_on_photo_summary)
        })

        // --- ПРОЧЕЕ ---
        val otherCategory = PreferenceCategory(context).apply {
            title = "Other"
        }
        screen.addPreference(otherCategory)

        val tutorialPreference = Preference(context).apply {
            title = getString(R.string.open_tutorial_site)
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(getString(R.string.tutorial_url))
                }
                startActivity(intent)
                true
            }
        }
        otherCategory.addPreference(tutorialPreference)

        val licensePreference = Preference(context).apply {
            title = getString(R.string.licenses)
            summary = getString(R.string.licenses_description)
            setOnPreferenceClickListener {
                backstack.goTo(LicensesKey())
                true
            }
        }
        otherCategory.addPreference(licensePreference)

        preferenceScreen = screen
    }

    companion object {
        const val PREF_CAMERA_ID_DAY = "pref_camera_id_day"
        const val PREF_CAMERA_ID_NIGHT = "pref_camera_id_night"
        const val PREF_CAMERA_ID_FRONT = "pref_camera_id_front"

        const val PREF_OUTPUT_DIRECTORY = "mediaOutputLocation"
        const val PREF_FRAMERATE = "videoFramerate"
        const val PREF_VIDEO_RESOLUTION = "videoResolution"
        const val PREF_OVERLAY = "enableOverlay"
        const val PREF_RECORD_ON_START = "recordOnStart"
        const val PREF_VIBRATE_WHILE_RECORDING = "vibrateWhileRecording"
        const val PREF_VIBRATE_ON_PHOTO = "vibrateOnPhoto"
        const val PREF_SELECTED_RECIPIENTS = "selectedX25519Recipients"
        const val PREF_REMOVE_EXIF = "removeExifData"
        const val PREF_REMEMBER_SHOOTING_MODE = "rememberShootingMode"

        // Эти константы больше не нужны, так как мы удалили кастомные уведомления
        // const val PREF_CUSTOMIZE_NOTIFICATION = "customizeNotification"
        // ... и остальные PREF_CUSTOM...

        const val SHOWED_BACKGROUND_RECORDING_INFO = "showedBackgroundRecordingInfo"
        const val SHOWED_TUTORIAL_INFO = "showedWebsiteTutorialInfo"
        const val LAST_SHOOTING_MODE = "lastShootingMode"

        const val DEFAULT_RESOLUTION = "1920x1080"
    }
}