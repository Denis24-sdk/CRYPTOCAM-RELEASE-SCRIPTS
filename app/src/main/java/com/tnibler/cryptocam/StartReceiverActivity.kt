package com.tnibler.cryptocam

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.content.ContextCompat
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.video.RecordingService
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ExperimentalCameraFilter // <-- [ИСПРАВЛЕНИЕ] Добавлена аннотация
class StartReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [ИСПРАВЛЕНИЕ] Используем runBlocking для вызова suspend функции из onCreate
        val hasKeys = runBlocking {
            val keyManager: KeyManager = (application as App).globalServices.get()
            keyManager.availableKeys.first().isNotEmpty()
        }

        if (hasKeys) {
            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                // [ИСПРАВЛЕНО] Убеждаемся, что action и extras передаются корректно
                action = intent.action
                intent.extras?.let { putExtras(it) }
            }

            // [ИСПРАВЛЕНО] Добавлена проверка версии Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = ApiConstants.ACTION_CHECK_ENCRYPTION_KEY
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(mainIntent)
        }

        finish()
    }
}