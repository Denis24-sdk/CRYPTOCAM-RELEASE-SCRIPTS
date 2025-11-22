package com.tnibler.system.video

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.*
import android.os.StatFs
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.ExperimentalCameraFilter
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.tnibler.system.*
import com.tnibler.system.R
import com.tnibler.system.keys.KeyManager
import com.tnibler.system.preference.SettingsFragment
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.*

@ExperimentalCameraFilter
@ExperimentalCamera2Interop
class RecordingService : Service(), LifecycleOwner {
    private val TAG = javaClass.simpleName
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val binder = RecordingServiceBinder()
    private var isBound = false
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val notificationId = 1
    private val notificationBuilder by lazy { notificationBuilder(this) }
    private var recordingManager: RecordingManager? = null
    private val _state: MutableStateFlow<State> = MutableStateFlow(State.NotReadyToRecord(false, SelectedCamera.BACK, flashOn = false))
    val state = _state.asStateFlow()
    private var cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoStreamCapture? = null
    private var camera: Camera? = null
    private lateinit var resolution: Size
    private val orientationEventListener by lazy { buildOrientationEventListener() }
    private var lastHandledOrientation: Orientation = Orientation.LAND_LEFT
    private var surfaceRotation: Int = Surface.ROTATION_90
    private var isInForeground = false
    private var startRecordingAction = false
    private var stopServiceAfterRecording = false
    private val vibrator by lazy { ContextCompat.getSystemService(this, Vibrator::class.java)!! }
    @Volatile private var isStopping = false
    private var pendingStartIntent: Intent? = null
    private var currentParams: RecordingParams? = null

    @SuppressLint("MissingPermission")
    private fun vibrateOnStart() {
        if (!sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_ON_START, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else { @Suppress("DEPRECATION") vibrator.vibrate(50) }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateOnStop() {
        if (!sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_ON_STOP, true)) return
        val pattern = longArrayOf(0, 100, 80, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else { @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1) }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateWhileRecording() {
        if (!sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_WHILE_RECORDING, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else { @Suppress("DEPRECATION") vibrator.vibrate(50) }
    }

    private fun buildOrientationEventListener(): OrientationEventListener {
        return object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                val newSurfaceRotation = when (orientation) {
                    in 45..135 -> Surface.ROTATION_270  // Landscape right
                    in 135..225 -> Surface.ROTATION_180 // Portrait upside down
                    in 225..315 -> Surface.ROTATION_90  // Landscape left
                    else -> Surface.ROTATION_0          // Portrait
                }

                // Обновляем только если rotation действительно изменился
                if (newSurfaceRotation != surfaceRotation) {
                    surfaceRotation = newSurfaceRotation
                    Log.d(TAG, "Orientation changed: $orientation -> Surface.ROTATION_$surfaceRotation")

                    // Переинициализируем запись только если не записываем и use cases уже инициализированы
                    if (state.value !is State.Recording && state.value is State.ReadyToRecord) {
                        Log.d(TAG, "Reinitializing recording with new orientation")
                        initRecording()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        resolution = getVideoResolutionFromPrefs()

        // Запускаем отслеживание ориентации
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
            Log.d(TAG, "Orientation listener enabled")
        } else {
            Log.w(TAG, "Cannot detect orientation")
        }

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        (applicationContext as App).recordingService = this
    }

    override fun onDestroy() {
        orientationEventListener.disable()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        (applicationContext as App).recordingService = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) return START_NOT_STICKY

        when (intent.action) {
            ApiConstants.ACTION_START -> handleStartCommand(intent); ApiConstants.ACTION_STOP -> handleStopCommand()
        }
        return START_REDELIVER_INTENT
    }

    private fun handleStartCommand(intent: Intent) {

        foreground()

        if (state.value is State.Recording) {
            Log.w(TAG, "Start command received, but recording is already in progress. Ignoring.")
            return
        }
        if (isStopping) {
            Log.w(TAG, "Start command received while stopping. Queuing command.")
            pendingStartIntent = intent
            return
        }

        Log.d(TAG, "===> STEP 1: handleStartCommand CALLED")

        val mode = intent.getStringExtra(ApiConstants.EXTRA_MODE) ?: ApiConstants.MODE_DAY
        val resStr = intent.getStringExtra(ApiConstants.EXTRA_RESOLUTION) ?: "FHD"
        val codec = intent.getStringExtra(ApiConstants.EXTRA_CODEC) ?: ApiConstants.CODEC_AVC
        val useUltraWide: Boolean; val fps: Int; var oisEnabled: Boolean; val eisEnabled: Boolean
        when (mode) {
            ApiConstants.MODE_DAY -> { useUltraWide = true; fps = 60; oisEnabled = false; eisEnabled = false }
            ApiConstants.MODE_NIGHT -> { useUltraWide = false; fps = 30; oisEnabled = true; eisEnabled = false }
            ApiConstants.MODE_FRONT -> { useUltraWide = false; fps = 30; oisEnabled = false; eisEnabled = false }
            else -> { useUltraWide = false; fps = 30; oisEnabled = true; eisEnabled = true }
        }
        val resolution = when (resStr.uppercase()) {
            "HD" -> Size(1280, 720); "FHD" -> Size(1920, 1080); "2K" -> Size(2560, 1440); "4K" -> Size(3840, 2160); else -> Size(1920, 1080)
        }

        // настройки для высоких разрешений
        if (resolution.width >= 2560) {
            // Отключаем OIS для высоких разрешений
            if (oisEnabled) {
                oisEnabled = false
                Log.d(TAG, "OIS disabled for high resolution: ${resolution}")
            }
            // Для NIGHT режима с 4K используем специальную камеру если настроена
            if (mode == ApiConstants.MODE_NIGHT && resolution.width >= 3840) {
                Log.d(TAG, "4K NIGHT mode: Using configured camera for 4K support")
            }
        }
        currentParams = RecordingParams(mode, resolution, codec, fps, useUltraWide, oisEnabled, eisEnabled)
        startRecordingAction = true
        if (cameraProvider == null) initCamera() else initUseCases()
    }

    private fun isHevcSupported(): Boolean {
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            // Ищем любой энкодер, который поддерживает HEVC (H.265)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                try {
                    val types = info.supportedTypes
                    for (type in types) {
                        if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                            return true
                        }
                    }
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking HEVC support", e)
        }
        return false
    }

    private fun handleStopCommand() {
        // 1. ВАЖНО: Всегда вызываем foreground().
        // Если сервис был перезапущен этой командой, система требует уведомление,
        // иначе приложение упадет с ошибкой (ForegroundServiceDidNotStartInTimeException).
        foreground()

        // 2. Если запись уже не идет (или сервис только проснулся)
        if (state.value !is State.Recording) {
            Log.d(TAG, "Stop command received, but recording is not active. Stopping service.")
            // Просто убиваем сервис, так как работы для него нет
            stopSelf()
            return
        }

        if (isStopping) return
        isStopping = true; stopServiceAfterRecording = true; stopRecording()
    }

    fun startRecording() {
        if (state.value is State.ReadyToRecord) {
            recordingManager?.recordButtonClicked()
            updateRecordingStateHandler.post(updateRecordingStateRunnable)
            val currentState = state.value as State.ReadyToRecord
            _state.value = State.Recording(currentState.resolution, currentState.surfaceRotation, Duration.ZERO, currentState.selectedCamera, currentState.flashOn)
        }
    }

    fun stopRecording() {
        if (state.value is State.Recording) {
            recordingManager?.recordButtonClicked()
            updateRecordingStateHandler.removeCallbacks(updateRecordingStateRunnable)
            vibrationCounter = 0 // Reset vibration counter when stopping recording
            val currentState = state.value as State.Recording
            _state.value = State.ReadyToRecord(currentState.resolution, currentState.surfaceRotation, currentState.selectedCamera, currentState.flashOn)
        }
    }

    fun toggleFlash() {
        val newFlashState = !state.value.flashOn
        camera?.cameraControl?.enableTorch(newFlashState)
        _state.value = when (val currentState = _state.value) {
            is State.ReadyToRecord -> currentState.copy(flashOn = newFlashState)
            is State.Recording -> currentState.copy(flashOn = newFlashState)
            is State.NotReadyToRecord -> currentState.copy(flashOn = newFlashState)
        }
    }

    fun toggleCamera() {
        if (state.value is State.Recording) return
        val newSelectedCamera = state.value.selectedCamera.other()
        _state.value = State.NotReadyToRecord(false, newSelectedCamera, state.value.flashOn)
        if (cameraProvider == null) initCamera() else initUseCases()
    }

    fun bindUseCase(useCase: UseCase) {
        cameraProvider?.bindToLifecycle(this, cameraSelector, useCase)
    }

    fun unbindUseCase(useCase: UseCase) {
        cameraProvider?.unbind(useCase)
    }

    fun scaleZoomRatio(scaleFactor: Float) {
        val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: return
        camera?.cameraControl?.setZoomRatio(currentZoomRatio * scaleFactor)
    }

    fun startFocusAndMetering(action: FocusMeteringAction) {
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun foreground() {
        if (isInForeground) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(notificationId, notificationBuilder.build())
        }
        isInForeground = true
    }

    private fun initCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get()
                initUseCases()
            } catch (e: Exception) { Log.e(TAG, "Failed to get CameraProvider", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("RestrictedApi")
    fun initUseCases() {
        val localCameraProvider = cameraProvider ?: return
        localCameraProvider.unbindAll()
        val params = currentParams ?: return

        // 1. Выбор камеры
        val customCameraId = sharedPreferences.getString(when (params.mode) {
            ApiConstants.MODE_DAY -> SettingsFragment.PREF_CAMERA_ID_DAY
            ApiConstants.MODE_NIGHT -> SettingsFragment.PREF_CAMERA_ID_NIGHT
            ApiConstants.MODE_FRONT -> SettingsFragment.PREF_CAMERA_ID_FRONT
            else -> null
        }, null)?.trim()

        val cameraSelectorBuilder = CameraSelector.Builder()
        if (!customCameraId.isNullOrBlank()) {
            cameraSelectorBuilder.addCameraFilter { it.filter { Camera2CameraInfo.from(it).cameraId == customCameraId } }
        } else if (params.useUltraWide) {
            cameraSelectorBuilder.addCameraFilter { it.sortedBy { c -> try { Camera2CameraInfo.extractCameraCharacteristics(c)[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.minOrNull() ?: Float.MAX_VALUE } catch (e: Exception) { Float.MAX_VALUE } } }
        } else {
            cameraSelectorBuilder.requireLensFacing(if (params.mode == ApiConstants.MODE_FRONT) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
        }
        cameraSelector = cameraSelectorBuilder.build()

        // 2. Определяем кодек
        val hevcExists = isHevcSupported()
        val userWantsHevc = params.codec.equals(ApiConstants.CODEC_HEVC, true)

        var targetCodec = if (userWantsHevc && hevcExists) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        val initialBitRate = when {
            params.resolution.width >= 3840 -> 60_000_000
            params.resolution.width >= 2560 -> 40_000_000
            params.resolution.width >= 1920 -> 25_000_000
            else -> 15_000_000
        }

        Log.d(TAG, "Attempting init with codec: $targetCodec (UserWants=$userWantsHevc, Hardware=$hevcExists)")

        // 3. Попытка запуска с Fallback
        try {
            setupVideoCapture(localCameraProvider, targetCodec, initialBitRate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video capture with codec $targetCodec", e)

            if (targetCodec == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                Log.w(TAG, "Fallback: Trying AVC because HEVC failed.")
                try {
                    setupVideoCapture(localCameraProvider, MediaFormat.MIMETYPE_VIDEO_AVC, initialBitRate)
                } catch (retryE: Exception) {
                    Log.e(TAG, "Fatal: Failed to setup video capture even with AVC", retryE)
                }
            }
        }
    }

    // Вынесенная логика настройки VideoCapture
    @SuppressLint("RestrictedApi")
    private fun setupVideoCapture(provider: ProcessCameraProvider, codec: String, rawBitRate: Int) {
        val params = currentParams ?: return

        // --- БИТРЕЙТ НАСТРОЙКИ ---
        // Используем полные битрейты для обоих кодеков (AVC и HEVC)
        // для достижения максимальной четкости видео
        val adjustedBitRate = rawBitRate

        Log.d(TAG, "Setup VideoCapture. Codec=$codec, RawBitrate=$rawBitRate, AdjustedBitrate=$adjustedBitRate")

        val builder = VideoStreamCapture.Builder()
            .setVideoFrameRate(params.fps)
            .setCameraSelector(cameraSelector)
            .setTargetResolution(params.resolution)
            .setBitRate(adjustedBitRate) // Используем скорректированный битрейт
            .setTargetRotation(surfaceRotation)
            .setIFrameInterval(1)
            .setVideoCodec(codec)
            // --- НАСТРОЙКИ АУДИО ДЛЯ WINDOWS ---
            .setAudioBitRate(256000)
            .setAudioSampleRate(48000)
            .setAudioChannelCount(2)

        val extender = Camera2Interop.Extender(builder)

        val fpsRange = when {
            params.mode == ApiConstants.MODE_DAY -> Range(50, 60)
            params.mode == ApiConstants.MODE_NIGHT -> Range(24, 30)
            else -> Range(24, 30)
        }

        if (params.resolution.width >= 2560 && params.mode == ApiConstants.MODE_NIGHT) {
            extender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0)
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0)
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        } else {
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }

        extender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, if (params.isOisEnabled) 1 else 0)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, if (params.isEisEnabled) 1 else 0)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

        videoCapture = builder.build()
        camera = provider.bindToLifecycle(this, cameraSelector, videoCapture!!)

        val actualRes = videoCapture?.attachedSurfaceResolution
        Log.d(TAG, "SUCCESS bind: Codec=$codec, ReqRes=${params.resolution}, ActualRes=$actualRes")

        // --- ПРОВЕРКА СООТНОШЕНИЯ СТОРОН ---
        val w = actualRes?.width?.toFloat() ?: 0f
        val h = actualRes?.height?.toFloat() ?: 1f
        val ratio = if (w > h) w / h else h / w

        val isBadAspectRatio = ratio < 1.5

        var finalResolution = params.resolution

        // Базовый расчет битрейта (высокий, для AVC)
        var newRawBitRate = rawBitRate

        if (isBadAspectRatio) {
            Log.w(TAG, "Aspect Ratio mismatch! Got 4:3 ($actualRes). Forcing Vertical orientation of REQUESTED resolution.")
            val minDim = Math.min(params.resolution.width, params.resolution.height)
            val maxDim = Math.max(params.resolution.width, params.resolution.height)
            finalResolution = Size(minDim, maxDim)

            newRawBitRate = when {
                maxDim >= 3840 -> 60_000_000
                maxDim >= 2560 -> 40_000_000
                maxDim >= 1920 -> 25_000_000
                else -> 15_000_000
            }
        } else {
            newRawBitRate = when {
                actualRes?.width ?: 0 >= 3840 -> 60_000_000
                actualRes?.width ?: 0 >= 2560 -> 40_000_000
                actualRes?.width ?: 0 >= 1920 -> 25_000_000
                else -> 15_000_000
            }
        }

        // Снова применяем логику HEVC для нового битрейта
        val finalAdjustedBitRate = newRawBitRate

        // --- ЛОГИКА ПЕРЕСОЗДАНИЯ (Rebinding) ---
// Сравниваем с adjustedBitRate (текущим), чтобы понять, изменилось ли что-то
        if ((finalAdjustedBitRate != adjustedBitRate || isBadAspectRatio) && actualRes != null) {
            Log.d(TAG, "Rebinding needed. NewBitrate=$finalAdjustedBitRate (Raw=$newRawBitRate), NewRes=$finalResolution, SurfaceRotation=$surfaceRotation")
            provider.unbind(videoCapture!!)

            val correctedBuilder = VideoStreamCapture.Builder()
                .setVideoFrameRate(params.fps)
                .setCameraSelector(cameraSelector)
                .setTargetResolution(finalResolution)
                .setBitRate(finalAdjustedBitRate) // ВАЖНО: используем сниженный битрейт
                .setTargetRotation(surfaceRotation)  // ← ИСПРАВЛЕНО: используем текущий surfaceRotation
                .setIFrameInterval(1)
                .setVideoCodec(codec)
                // --- АУДИО ФИКС ---
                .setAudioBitRate(256000)
                .setAudioSampleRate(48000)
                .setAudioChannelCount(2)

            val correctedExtender = Camera2Interop.Extender(correctedBuilder)

            if (params.resolution.width >= 2560 && params.mode == ApiConstants.MODE_NIGHT) {
                correctedExtender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0)
                correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0)
                correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            } else {
                correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            }

            correctedExtender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, if (params.isOisEnabled) 1 else 0)
            correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, if (params.isEisEnabled) 1 else 0)
            correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            correctedExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

            videoCapture = correctedBuilder.build()
            camera = provider.bindToLifecycle(this, cameraSelector, videoCapture!!)

            val newRes = videoCapture?.attachedSurfaceResolution
            Log.d(TAG, "Rebound finish. ActualRes=$newRes, SurfaceRotation=$surfaceRotation")
        }

        camera?.cameraControl?.enableTorch(state.value.flashOn)
        _state.value = State.NotReadyToRecord(true, state.value.selectedCamera, state.value.flashOn)

        initRecording()
    }

    @SuppressLint("RestrictedApi")
    private fun initRecording() {
        // Проверяем, что videoCapture инициализирован
        if (videoCapture == null) {
            Log.w(TAG, "initRecording called but videoCapture is null")
            return
        }

        val outputLocationStr = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
        val keyManager: KeyManager = (application as App).globalServices.get()
        val recipients = runBlocking { keyManager.selectedRecipients.first() }

        var isOutputDirectoryOk = false
        if (!outputLocationStr.isNullOrEmpty() && recipients.isNotEmpty()) {
            try {
                val dirUri = outputLocationStr.toUri()
                val hasPersistedPermission = contentResolver.persistedUriPermissions.any {
                    it.uri == dirUri && it.isWritePermission
                }
                if (hasPersistedPermission) {
                    val docFile = DocumentFile.fromTreeUri(this, dirUri)
                    if (docFile != null && docFile.exists()) {
                        isOutputDirectoryOk = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while checking output directory permissions", e)
            }
        }


        // Если папка не выбрана, недоступна или нет ключей шифрования
        if (!isOutputDirectoryOk || videoCapture == null) {
            Log.e(TAG, "Pre-recording check failed. isOutputDirectoryOk=$isOutputDirectoryOk, videoCapture==null is ${videoCapture == null}")

            if (!isOutputDirectoryOk) {
                Toast.makeText(this, "Output directory permission issue. Please select it again.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = ApiConstants.ACTION_FORCE_OUTPUT_PICKER
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            if (recipients.isEmpty()) {
                Toast.makeText(this, "No encryption key selected.", Toast.LENGTH_LONG).show()
            }

            stopSelf()
            return
        }

        // Check storage space
        val outputDirPath = outputLocationStr?.toUri()?.path ?: return
        if (!checkStorageSpaceBeforeRecording(outputDirPath)) {
            Log.e(TAG, "Insufficient storage space for recording")
            Toast.makeText(this, "Insufficient storage space. At least 100MB required.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        val actualRes = videoCapture!!.attachedSurfaceResolution ?: return
        val codecName = currentParams?.codec ?: ApiConstants.CODEC_AVC

        // ВАЖНО: Используем surfaceRotation, который обновляется в orientation listener
        val rotationValue = when (surfaceRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        Log.d(TAG, "Final video rotation set to: $rotationValue for camera: ${state.value.selectedCamera}, surfaceRotation: $surfaceRotation")

        val videoInfo = VideoInfo(
            actualRes.width,
            actualRes.height,
            rotationValue,  // Теперь используем реальное значение из orientation listener
            10_000_000,
            codecName
        )

        val audioInfo = AudioInfo(videoCapture!!.audioChannelCount, videoCapture!!.audioBitRate, videoCapture!!.audioSampleRate)

        val outputFileManager = outputLocationStr?.let { OutputFileManager(it.toUri(), recipients, contentResolver, sharedPreferences, this) }

        recordingManager = outputFileManager?.let {
            RecordingManager(videoCapture!!, videoInfo, audioInfo, ContextCompat.getMainExecutor(this), lifecycleScope,
                it,
                recordingStoppedCallback = {
                    isStopping = false
                    if (stopServiceAfterRecording) {
                        stopServiceAfterRecording = false
                        if (pendingStartIntent == null) {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                            isInForeground = false
                            stopSelf()
                        }
                    }
                    pendingStartIntent?.let { intent ->
                        pendingStartIntent = null
                        handleStartCommand(intent)
                    }
                },
                onRecordingStarted = { vibrateOnStart() },
                onRecordingStopped = { vibrateOnStop() }
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                recordingManager?.setUp()
                if (state.value is State.NotReadyToRecord) {
                    val currentState = state.value as State.NotReadyToRecord
                    _state.value = State.ReadyToRecord(resolution, surfaceRotation, currentState.selectedCamera, currentState.flashOn)
                    if (startRecordingAction) {
                        startRecordingAction = false
                        delay(400)
                        startRecording()
                    }
                }
            }
        }
    }

    private val updateRecordingStateHandler = Handler(Looper.getMainLooper())
    private var vibrationCounter = 0
    private val updateRecordingStateRunnable: Runnable = object : Runnable {
        override fun run() {
            val recManager = recordingManager ?: return
            if (state.value is State.Recording) {
                _state.value = (state.value as State.Recording).copy(recordingTime = recManager.recordingTime)
                // Vibrate every 5 seconds (25 * 200ms = 5 seconds)
                vibrationCounter++
                if (vibrationCounter >= 25) {
                    vibrateWhileRecording()
                    vibrationCounter = 0
                }

                // Check storage space during recording
                val outputLocationStr = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
                val outputDirPath = outputLocationStr?.toUri()?.path
                if (outputDirPath != null && !checkStorageSpaceDuringRecording(outputDirPath)) {
                    Log.w(TAG, "Storage space critically low during recording. Stopping.")
                    Toast.makeText(this@RecordingService, "Storage space critically low. Stopping recording.", Toast.LENGTH_LONG).show()
                    stopRecording()
                    return
                }
            }
            /*
            if (isInForeground) {
            val d = recManager.recordingTime
            val text = if (d.toHours() > 0) String.format(Locale.US, "%02d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.seconds % 60)
            else String.format(Locale.US, "%02d:%02d", d.toMinutes() % 60, d.seconds % 60)
            // проверка разрешений
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@RecordingService, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            } else {
                notificationManager.notify(notificationId, notificationBuilder.setContentText(getString(R.string.notification_text, text)).build())
            }
        }
        */

            updateRecordingStateHandler.postDelayed(this, 200)
        }
    }

    private fun getVideoResolutionFromPrefs(): Size {
        val s = (sharedPreferences.getString(SettingsFragment.PREF_VIDEO_RESOLUTION, SettingsFragment.DEFAULT_RESOLUTION) ?: SettingsFragment.DEFAULT_RESOLUTION).split("x")
        return Size(s[0].toInt(), s[1].toInt())
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override fun onBind(intent: Intent?): IBinder { isBound = true; stopServiceAfterRecording = false; return binder }
    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        if (state.value !is State.Recording) stopSelf()
        return super.onUnbind(intent)
    }

    private fun debugToast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // Storage space management
    private fun getAvailableStorageBytes(path: String): Long {
        return try {
            // For SAF URIs or any path that starts with /tree/, use external storage
            // since we can't directly access the filesystem path for SAF directories
            val storagePath = if (path.startsWith("/tree/")) {
                // Use external storage directory for SAF paths
                android.os.Environment.getExternalStorageDirectory().absolutePath
            } else {
                // Use the provided path for regular filesystem paths
                path
            }

            val stat = StatFs(storagePath)
            val bytesAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
            bytesAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage space", e)
            Long.MAX_VALUE // Assume plenty of space if can't check
        }
    }

    private fun checkStorageSpaceBeforeRecording(outputPath: String): Boolean {
        val availableBytes = getAvailableStorageBytes(outputPath)
        val minRequiredBytes = 100L * 1024 * 1024 // 100 MB minimum
        return availableBytes > minRequiredBytes
    }

    private fun checkStorageSpaceDuringRecording(outputPath: String): Boolean {
        val availableBytes = getAvailableStorageBytes(outputPath)
        val criticalThreshold = 50L * 1024 * 1024 // 50 MB critical threshold
        return availableBytes > criticalThreshold
    }

    companion object {
        const val ACTION_TOGGLE_RECORDING = "CryptocamToggleRecording"
        private const val MIN_STORAGE_BYTES = 100L * 1024 * 1024 // 100 MB
        private const val CRITICAL_STORAGE_BYTES = 50L * 1024 * 1024 // 50 MB
    }
    inner class RecordingServiceBinder : Binder() { val service: RecordingService get() = this@RecordingService }
    sealed class State(open val selectedCamera: SelectedCamera, open val flashOn: Boolean) {
        data class Recording(val resolution: Size, val surfaceRotation: Int, val recordingTime: Duration, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
        data class ReadyToRecord(val resolution: Size, val surfaceRotation: Int, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
        data class NotReadyToRecord(val useCasesInitialized: Boolean, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
    }
}
