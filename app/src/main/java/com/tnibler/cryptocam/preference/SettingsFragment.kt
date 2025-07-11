package com.tnibler.cryptocam.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.*
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.keys.keyList.KeysKey
import com.zhuinden.simplestackextensions.fragmentsktx.backstack

class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = javaClass.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        rootKey?.let { screen.key = rootKey }
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
            key =
                PREF_OUTPUT_DIRECTORY
            title = getString(R.string.pref_output_directory)
            summary = getString(R.string.pref_output_directory_summary)
            setIcon(R.drawable.ic_folder)
            setOnPreferenceClickListener {
                requestDirectory.launch(null)
                true
            }
        }
        screen.addPreference(outputDirPreference)

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
        screen.addPreference(keyPreference)

        val fpsPreference = ListPreference(context).apply {
            setDefaultValue("30")
            entries = arrayOf("30 fps", "60 fps")
            entryValues = arrayOf("30", "60")
            title = getString(R.string.video_framerate)
            key = PREF_FRAMERATE
            setValueIndex(0)
            isPersistent = true
            setIcon(R.drawable.ic_timer)
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                summary = "$newValue fps"
                (preference as ListPreference).value = newValue as String
                true
            }
        }
        screen.addPreference(fpsPreference)
        fpsPreference.summary = "${fpsPreference.value} fps"

        val resolutionPreference = ListPreference(context).apply {
            setDefaultValue(DEFAULT_RESOLUTION)
            entries = arrayOf("1280x720", "1920x1080", "3840x2160")
            entryValues = entries
            title = getString(R.string.video_resolution)
            key = PREF_VIDEO_RESOLUTION
            setOnPreferenceChangeListener { preference, newValue ->
                (preference as ListPreference).value = newValue as String
                summary = getString(R.string.video_resolution_summary, newValue)
                true
            }
        }

        screen.addPreference(resolutionPreference)
        resolutionPreference.summary =
            getString(R.string.video_resolution_summary, resolutionPreference.value)

        val rememberShootingModePreference = CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_REMEMBER_SHOOTING_MODE
            title = getString(R.string.remember_shooting_mode)
            summary = getString(R.string.remember_shooting_mode_summary)
        }
        screen.addPreference(rememberShootingModePreference)

        val removeExifPreference = CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_REMOVE_EXIF
            title = getString(R.string.remove_exif_data)
            summary = getString(R.string.remove_exif_data_summary)
        }
        screen.addPreference(removeExifPreference)

        val overlayPreference = CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_OVERLAY
            title = getString(R.string.enable_overlay)
            summary = getString(R.string.enable_overlay_summary)
        }

        screen.addPreference(overlayPreference)

        val recordOnStartPreference = CheckBoxPreference(context).apply {
            setDefaultValue(false)
            key = PREF_RECORD_ON_START
            title = getString(R.string.record_on_start)
            summary = getString(R.string.record_on_start_summary)
        }

        screen.addPreference(recordOnStartPreference)

        val vibrateWhileRecordingPreference = CheckBoxPreference(context).apply {
            setDefaultValue(true)
            key = PREF_VIBRATE_WHILE_RECORDING
            title = getString(R.string.vibrate_while_recording)
            summary =
                getString(R.string.vibrate_while_recording_summary)

        }
        screen.addPreference(vibrateWhileRecordingPreference)

        val vibrateOnPhotoPreference = CheckBoxPreference(context).apply {
            setDefaultValue(true)
            key = PREF_VIBRATE_ON_PHOTO
            title = getString(R.string.vibrate_on_photo)
            summary = getString(R.string.vibrate_on_photo_summary)
        }

        screen.addPreference(vibrateOnPhotoPreference)

        val customNotificationPreference = Preference(context).apply {
            key = "customNotification"
            setTitle(R.string.custom_notification_settings)
            setSummary(R.string.custom_notification_summary)
            setOnPreferenceClickListener {
                backstack.goTo(CustomNotificationSettingsKey())
                true
            }
        }
        screen.addPreference(customNotificationPreference)

        val fileNamePreference = EditTextPreference(context).apply {
            setDefaultValue("cryptocam-\$num.age")
            key = "outputFileName"
            setTitle(R.string.output_file_name)
            setOnPreferenceChangeListener { preference, newValue ->
                summary = newValue.toString() + "\n" + getString(R.string.filename_pattern_description)
                true
            }
        }
        screen.addPreference(fileNamePreference)
        fileNamePreference.summary = fileNamePreference.text + "\n" +
                getString(R.string.filename_pattern_description) +
                getString(R.string.filename_pattern_variables)

        val tutorialPreference = Preference(context).apply {
            key = "tutorial"
            title = getString(R.string.open_tutorial_site)
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(getString(R.string.tutorial_url))
                }
                startActivity(intent)
                true
            }
        }
        screen.addPreference(tutorialPreference)

        val licensePreference = Preference(context).apply {
            key = "licenses"
            title = getString(R.string.licenses)
            summary = getString(R.string.licenses_description)
            setOnPreferenceClickListener {
                backstack.goTo(LicensesKey())
                true
            }
        }
        screen.addPreference(licensePreference)

        preferenceScreen = screen
    }

    companion object {
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

        const val PREF_CUSTOMIZE_NOTIFICATION = "customizeNotification"
        const val PREF_CUSTOM_NOTIFICATION_APP_NAME = "customNotificationAppName"
        const val PREF_CUSTOM_NOTIFICATION_TITLE = "customNotificationTitle"
        const val PREF_CUSTOM_NOTIFICATION_TEXT = "customNotificationText"
        const val PREF_CUSTOM_NOTIFICATION_ICON = "customNotificationIcon"
        const val PREF_CUSTOM_NOTIFICATION_STYLE = "customNotificationStyle"

        const val SHOWED_BACKGROUND_RECORDING_INFO = "showedBackgroundRecordingInfo"
        const val SHOWED_TUTORIAL_INFO = "showedWebsiteTutorialInfo"
        const val LAST_SHOOTING_MODE = "lastShootingMode"

        const val DEFAULT_RESOLUTION = "1920x1080"
    }
}