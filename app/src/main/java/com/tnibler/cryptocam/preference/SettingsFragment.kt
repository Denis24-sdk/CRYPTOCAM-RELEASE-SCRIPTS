package com.tnibler.cryptocam.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R

class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = javaClass.simpleName

    private val chooseKeyActivityResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            chooseKeyResultListener?.invoke(result)
        }

    // gross, but we have to register all activity result listeners now so we can't
    private var chooseKeyResultListener: ((ActivityResult) -> Unit)? = null
    private fun setChooseKeyResultListener(listener: (ActivityResult) -> Unit) {
        chooseKeyResultListener = listener
    }

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
        keyPreference.key =
            PREF_OPENPGP_KEYIDS
        keyPreference.title = getString(R.string.pref_pgp_key)
        keyPreference.summary = getString(R.string.pref_pgp_key_summary)
        keyPreference.setIcon(R.drawable.ic_key)
        keyPreference.setOnPreferenceClickListener {
            val mainActivity = (requireActivity() as MainActivity)
            mainActivity.openPgpKeyManager.chooseKey(
                chooseKeyActivityResult,
                ::setChooseKeyResultListener
            ) { ok, keyIds ->
                if (!ok) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pick_key_something_went_wrong),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    preferenceManager.sharedPreferences.edit {
                        putStringSet(PREF_OPENPGP_KEYIDS, keyIds.map { it.toString() }.toSet())
                    }
                }
            }
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

        preferenceScreen = screen
    }

    companion object {
        private const val REQUEST_PICK_DIRECTORY = 8735
        const val PREF_OUTPUT_DIRECTORY = "mediaOutputLocation"
        const val PREF_OPENPGP_KEYIDS = "openPgpKeyId"
        const val PREF_FRAMERATE = "videoFramerate"
        const val PREF_VIDEO_RESOLUTION = "videoResolution"
        const val PREF_OVERLAY = "enableOverlay"
        const val PREF_RECORD_ON_START = "recordOnStart"

        const val DEFAULT_RESOLUTION = "1920x1080"
    }
}