package com.tnibler.system

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.tnibler.system.keys.KeyManager
import com.tnibler.system.preference.SettingsFragment
import com.tnibler.system.video.RecordingService
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri

@ExperimentalCameraFilter
class StartReceiverActivity : AppCompatActivity() {

    private val keyManager: KeyManager by lazy { (application as App).globalServices.get() }
    private val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startRecordingService()
            } else {
                showPermissionsRationale()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasKeys = runBlocking { keyManager.availableKeys.first().isNotEmpty() }
        if (!hasKeys) {
            launchKeySetup()
            return
        }

        if (!outputDirExists()) {
            launchOutputPicker()
            return
        }

        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startRecordingService()
            finish()
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun outputDirExists(): Boolean {
        return try {
            val savedUri = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
                ?: return false
            val docFile = DocumentFile.fromTreeUri(this, savedUri.toUri())
            docFile?.exists() == true && docFile.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = intent.action
            intent.extras?.let { putExtras(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showPermissionsRationale() {
        val rationaleIntent = Intent(this, PermissionRationaleActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(rationaleIntent)
    }

    private fun launchKeySetup() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = ApiConstants.ACTION_CHECK_ENCRYPTION_KEY
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(mainIntent)
        finish()
    }

    private fun launchOutputPicker() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = ApiConstants.ACTION_FORCE_OUTPUT_PICKER
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(mainIntent)
        finish()
    }
}