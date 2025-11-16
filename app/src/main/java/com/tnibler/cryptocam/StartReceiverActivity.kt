package com.tnibler.cryptocam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.content.ContextCompat
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.video.RecordingService
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ExperimentalCameraFilter
class StartReceiverActivity : AppCompatActivity() {

    private val keyManager: KeyManager by lazy { (application as App).globalServices.get() }

    // Лаунчер для запроса разрешений
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Этот код выполнится ПОСЛЕ того, как пользователь ответит на запрос
            if (permissions.all { it.value }) {
                // Все разрешения получены, запускаем запись
                startRecordingService()
            } else {
                // Разрешения отклонены, показываем наш экран с кнопкой "Настройки"
                showPermissionsRationale()
            }
            // В любом случае завершаем эту Activity
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Проверка ключа шифрования
        val hasKeys = runBlocking { keyManager.availableKeys.first().isNotEmpty() }
        if (!hasKeys) {
            launchKeySetup()
            return
        }

        // 2. Проверка разрешений
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // Все разрешения уже есть, запускаем запись
            startRecordingService()
            finish()
        } else {
            // Запрашиваем недостающие разрешения
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
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
            intent.extras?.let { putExtras(it) }
        }
        startActivity(mainIntent)
        finish()
    }
}