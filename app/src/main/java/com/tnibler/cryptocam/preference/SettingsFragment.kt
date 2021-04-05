package com.tnibler.cryptocam.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.keys.KeysKey
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
                preferenceManager.sharedPreferences.edit {
                    putString(PREF_OUTPUT_DIRECTORY, uri.toString())
                    commit()
                }
                val dir = DocumentFile.fromTreeUri(requireContext(), uri)
                if (dir?.findFile(".nomedia") == null) {
                    dir?.createFile("asd/asd", ".nomedia")
                }
            }

        val outputDirPreference = Preference(context)
        outputDirPreference.key =
            PREF_OUTPUT_DIRECTORY
        outputDirPreference.title = getString(R.string.pref_output_directory)
        outputDirPreference.summary = getString(R.string.pref_output_directory_summary)
        outputDirPreference.setIcon(R.drawable.ic_folder)
        outputDirPreference.setOnPreferenceClickListener {
            requestDirectory.launch(null)
            true
        }
        screen.addPreference(outputDirPreference)

        val keyPreference = Preference(context)
        keyPreference.key = PREF_SELECTED_RECIPIENTS
        keyPreference.title = getString(R.string.pref_keys)
        keyPreference.summary = getString(R.string.pref_keys_summary)
        keyPreference.setIcon(R.drawable.ic_key)
        keyPreference.setOnPreferenceClickListener {
            backstack.goTo(KeysKey())
            true
        }
        screen.addPreference(keyPreference)

        val fpsPreference = ListPreference(context).apply {
            setDefaultValue("60")
            entries = arrayOf("30 fps", "60 fps")
            entryValues = arrayOf("30", "60")
        }
        fpsPreference.title = getString(R.string.video_framerate)
        fpsPreference.key = PREF_FRAMERATE
        fpsPreference.setValueIndex(0)
        fpsPreference.isPersistent = true
        fpsPreference.setIcon(R.drawable.ic_timer)
        screen.addPreference(fpsPreference)
        fpsPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            fpsPreference.summary = "$newValue fps"
            (preference as ListPreference).value = newValue as String
            true
        }
        fpsPreference.summary = "${fpsPreference.value} fps"

        val resolutionPreference = ListPreference(context).apply {
            setDefaultValue(DEFAULT_RESOLUTION)
            entries = arrayOf("1280x720", "1920x1080", "3840x2160")
            entryValues = entries
        }
        resolutionPreference.title = getString(R.string.video_resolution)
        resolutionPreference.key = PREF_VIDEO_RESOLUTION
        resolutionPreference.setOnPreferenceChangeListener { preference, newValue ->
            (preference as ListPreference).value = newValue as String
            resolutionPreference.summary = getString(R.string.video_resolution_summary, newValue)
            true
        }

        screen.addPreference(resolutionPreference)
        resolutionPreference.summary =
            getString(R.string.video_resolution_summary, resolutionPreference.value as String)

        val overlayPreference = CheckBoxPreference(context)
        overlayPreference.setDefaultValue(false)
        overlayPreference.key = PREF_OVERLAY
        overlayPreference.title = getString(R.string.enable_overlay)
        overlayPreference.summary = getString(R.string.enable_overlay_summary)

        screen.addPreference(overlayPreference)

        val recordOnStartPreference = CheckBoxPreference(context)
        recordOnStartPreference.setDefaultValue(false)
        recordOnStartPreference.key = PREF_RECORD_ON_START
        recordOnStartPreference.title = getString(R.string.record_on_start)
        recordOnStartPreference.summary = getString(R.string.record_on_start_summary)

        screen.addPreference(recordOnStartPreference)

        val vibrateWhileRecordingPreference = CheckBoxPreference(context)
        vibrateWhileRecordingPreference.setDefaultValue(true)
        vibrateWhileRecordingPreference.key = PREF_VIBRATE_WHILE_RECORDING
        vibrateWhileRecordingPreference.title = getString(R.string.vibrate_while_recording)
        vibrateWhileRecordingPreference.summary =
            getString(R.string.vibrate_while_recording_summary)

        screen.addPreference(vibrateWhileRecordingPreference)

        val tutorialPreference = Preference(context)
        tutorialPreference.key = "tutorial"
        tutorialPreference.title = getString(R.string.open_tutorial_site)
//        tutorialPreference.summary = getString(R.string.tutorial_description)
        tutorialPreference.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(getString(R.string.tutorial_url))
            }
            startActivity(intent)
            true
        }
        screen.addPreference(tutorialPreference)

        val licensePreference = Preference(context)
        licensePreference.key = "licenses"
        licensePreference.title = getString(R.string.licenses)
        licensePreference.summary = getString(R.string.licenses_description)
        licensePreference.setOnPreferenceClickListener {
            backstack.goTo(LicensesKey())
            true
        }
        screen.addPreference(licensePreference)

        preferenceScreen = screen
    }

    companion object {
        private const val REQUEST_PICK_DIRECTORY = 8735
        const val PREF_OUTPUT_DIRECTORY = "mediaOutputLocation"
        const val PREF_FRAMERATE = "videoFramerate"
        const val PREF_VIDEO_RESOLUTION = "videoResolution"
        const val PREF_OVERLAY = "enableOverlay"
        const val PREF_RECORD_ON_START = "recordOnStart"
        const val PREF_VIBRATE_WHILE_RECORDING = "vibrateWhileRecording"
        const val PREF_SELECTED_RECIPIENTS = "selectedX25519Recipients"

        const val SHOWED_BACKGROUND_RECORDING_INFO = "showedBackgroundRecordingInfo"
        const val SHOWED_TUTORIAL_INFO = "showedWebsiteTutorialInfo"

        const val DEFAULT_RESOLUTION = "1920x1080"
    }
}