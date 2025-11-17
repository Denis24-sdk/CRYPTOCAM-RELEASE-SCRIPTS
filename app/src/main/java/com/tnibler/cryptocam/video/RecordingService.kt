package com.tnibler.cryptocam.video

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
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.ExperimentalCameraFilter
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.*
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.preference.SettingsFragment
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
@ExperimentalCamera2Interop // <-- [ИСПРАВЛЕНО] Добавлена аннотация
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

    private fun buildOrientationEventListener(): OrientationEventListener {
        return object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                val currentOrientation = when (orientation) {
                    in 75..134 -> Orientation.LAND_RIGHT; in 224..289 -> Orientation.LAND_LEFT; else -> Orientation.PORTRAIT
                }
                surfaceRotation = when (currentOrientation) {
                    Orientation.PORTRAIT -> Surface.ROTATION_0; Orientation.LAND_RIGHT -> Surface.ROTATION_270; Orientation.LAND_LEFT -> Surface.ROTATION_90
                }
                if (state.value !is State.Recording && currentOrientation != lastHandledOrientation) {
                    lastHandledOrientation = currentOrientation
                    initRecording()
                }
                lastHandledOrientation = currentOrientation
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        resolution = getVideoResolutionFromPrefs()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        orientationEventListener.enable()
        (applicationContext as App).recordingService = this



        // Логируем поддержку HEVC при запуске сервиса
        Log.d(TAG, "HEVC Support Test:\n${testHevcSupport()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        orientationEventListener.disable()
        (applicationContext as App).recordingService = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) return START_NOT_STICKY

        when (intent.action) {
            ApiConstants.ACTION_START -> handleStartCommand(intent); ApiConstants.ACTION_STOP -> handleStopCommand()
        }
        return START_REDELIVER_INTENT
    }

    private fun handleStartCommand(intent: Intent) {
        // [ИСПРАВЛЕНО] Перемещаем foreground() в самое начало.
        // Это гарантирует, что startForeground() будет вызван немедленно.
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
        val useUltraWide: Boolean; var fps: Int; var oisEnabled: Boolean; val eisEnabled: Boolean
        when (mode) {
            ApiConstants.MODE_DAY -> { useUltraWide = true; fps = 60; oisEnabled = false; eisEnabled = false }
            ApiConstants.MODE_NIGHT -> { useUltraWide = false; fps = 30; oisEnabled = true; eisEnabled = false }
            ApiConstants.MODE_FRONT -> { useUltraWide = false; fps = 30; oisEnabled = false; eisEnabled = false }
            else -> { useUltraWide = false; fps = 30; oisEnabled = true; eisEnabled = true }
        }
        val resolution = when (resStr.uppercase()) {
            "HD" -> Size(1280, 720); "FHD" -> Size(1920, 1080); "2K" -> Size(2560, 1440); "4K" -> Size(3840, 2160); else -> Size(1920, 1080)
        }
        // Корректируем настройки для высоких разрешений
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

    @SuppressLint("NewApi")
    private fun isHevcSupported(width: Int, height: Int, frameRate: Int): Boolean {
        try {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            val encoderName = mediaCodecList.findEncoderForFormat(format) ?: return false
            val caps = mediaCodecList.codecInfos.first { it.name == encoderName }.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            val supported = caps.videoCapabilities.isSizeSupported(width, height) &&
                           caps.videoCapabilities.getSupportedFrameRatesFor(width, height).contains(frameRate.toDouble())
            Log.d(TAG, "HEVC check for ${width}x${height}@${frameRate}fps: $supported (encoder: $encoderName)")
            return supported
        } catch (e: Exception) {
            Log.e(TAG, "HEVC support check failed", e)
            return false
        }
    }



    // Функция для тестирования поддержки HEVC на устройстве
    fun testHevcSupport(): String {
        val testCases = listOf(
            Triple(1920, 1080, 30), // FHD 30fps
            Triple(1920, 1080, 60), // FHD 60fps
            Triple(3840, 2160, 30), // 4K 30fps
            Triple(2560, 1440, 30), // 2K 30fps
        )
        val results = testCases.map { (w, h, fps) ->
            "HEVC ${w}x${h}@${fps}fps: ${if (isHevcSupported(w, h, fps)) "SUPPORTED" else "NOT SUPPORTED"}"
        }
        return results.joinToString("\n")
    }

    private fun handleStopCommand() {
        if (state.value !is State.Recording || isStopping) return
        isStopping = true; stopServiceAfterRecording = true; stopRecording()
    }




    // --- ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ ---

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

    // --- ВНУТРЕННИЕ МЕТОДЫ СЕРВИСА ---

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
        val hevcSupported = isHevcSupported(params.resolution.width, params.resolution.height, params.fps)
        val forceHevcForTesting = sharedPreferences.getBoolean("debug_force_hevc", false) // Отладочный флаг для тестирования
        val finalCodec = if (params.codec.equals(ApiConstants.CODEC_HEVC, true) && (hevcSupported || forceHevcForTesting)) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        Log.d(TAG, "Mode: ${params.mode}, Resolution: ${params.resolution}, FPS: ${params.fps}, OIS: ${params.isOisEnabled}, EIS: ${params.isEisEnabled}")
        Log.d(TAG, "Requested codec: ${params.codec}, HEVC supported: $hevcSupported, Force HEVC: $forceHevcForTesting, Final codec: $finalCodec")
        // Устанавливаем битрейт в зависимости от разрешения
        val bitRate = when {
            params.resolution.width >= 3840 -> 50_000_000 // 4K: 50 Mbps
            params.resolution.width >= 2560 -> 30_000_000 // 2K: 30 Mbps
            params.resolution.width >= 1920 -> 20_000_000 // FHD: 20 Mbps
            else -> 10_000_000 // HD и ниже: 10 Mbps
        }
        val builder = VideoStreamCapture.Builder().setVideoFrameRate(params.fps).setCameraSelector(cameraSelector).setTargetResolution(params.resolution)
            .setBitRate(bitRate).setTargetRotation(Surface.ROTATION_90).setIFrameInterval(1).setVideoCodec(finalCodec)
        val extender = Camera2Interop.Extender(builder)
        // Устанавливаем диапазоны FPS согласно требованиям
        // Для высоких разрешений в NIGHT режиме пробуем разные подходы
        if (params.resolution.width >= 2560 && params.mode == ApiConstants.MODE_NIGHT) {
            // Для 4K/2K в NIGHT режиме: отключаем OIS и EIS, устанавливаем фиксированный FPS
            extender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0) // OIS OFF
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0) // EIS OFF
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            Log.d(TAG, "4K NIGHT mode: OIS and EIS disabled, FPS fixed to 30")
        } else {
            val fpsRange = when {
                params.mode == ApiConstants.MODE_DAY -> Range(50, 60) // DAY: 50-60 FPS
                params.mode == ApiConstants.MODE_NIGHT -> Range(24, 30) // NIGHT обычное разрешение: 24-30 FPS
                else -> Range(24, 30) // FRONT: 24-30 FPS
            }
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
        }
        extender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, if (params.isOisEnabled) 1 else 0)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, if (params.isEisEnabled) 1 else 0)
        // Автоэкспозиция и автофокус включены по умолчанию
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        try {
            videoCapture = builder.build()
            camera = localCameraProvider.bindToLifecycle(this, cameraSelector, videoCapture!!)
            val actualResolution = videoCapture?.attachedSurfaceResolution
            Log.d(TAG, "Camera bound successfully. Requested: ${params.resolution}, Actual surface resolution: $actualResolution")
        } catch (e: Exception) { Log.e(TAG, "Bind to lifecycle failed", e); return }
        camera?.cameraControl?.enableTorch(state.value.flashOn)
        _state.value = State.NotReadyToRecord(true, state.value.selectedCamera, state.value.flashOn)
        initRecording()
    }

    @SuppressLint("RestrictedApi")
    private fun initRecording() {
        val outputLocationStr = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
        val keyManager: KeyManager = (application as App).globalServices.get()
        val recipients = runBlocking { keyManager.selectedRecipients.first() }
        if (outputLocationStr == null || recipients.isEmpty() || videoCapture == null) return
        val actualRes = videoCapture!!.attachedSurfaceResolution ?: return
        val videoInfo = VideoInfo(actualRes.width, actualRes.height, when (lastHandledOrientation) {
            Orientation.PORTRAIT -> 90; Orientation.LAND_LEFT -> 0; Orientation.LAND_RIGHT -> 180
        }, 10_000_000)
        val audioInfo = AudioInfo(videoCapture!!.audioChannelCount, videoCapture!!.audioBitRate, videoCapture!!.audioSampleRate)
        val outputFileManager = OutputFileManager(outputLocationStr.toUri(), recipients, contentResolver, sharedPreferences, this)
        recordingManager = RecordingManager(videoCapture!!, videoInfo, audioInfo, ContextCompat.getMainExecutor(this), lifecycleScope, outputFileManager,
            recordingStoppedCallback = {
                isStopping = false
                if (stopServiceAfterRecording) {
                    stopServiceAfterRecording = false
                    if (pendingStartIntent == null) {
                        // [ИСПРАВЛЕНО] Совместимый вызов stopForeground
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        isInForeground = false
                        stopSelf()
                    }
                }
                pendingStartIntent?.let { val intent = it; pendingStartIntent = null; handleStartCommand(intent) }
            },
            onRecordingStarted = { vibrateOnStart() },
            onRecordingStopped = { vibrateOnStop() }
        )
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            recordingManager!!.setUp()
            if (state.value is State.NotReadyToRecord) {
                val currentState = state.value as State.NotReadyToRecord
                _state.value = State.ReadyToRecord(resolution, surfaceRotation, currentState.selectedCamera, currentState.flashOn)
                if (startRecordingAction) { startRecordingAction = false; delay(400); startRecording() }
            }
        }}
    }

    private val updateRecordingStateHandler = Handler(Looper.getMainLooper())
    private val updateRecordingStateRunnable: Runnable = object : Runnable {
        override fun run() {
            val recManager = recordingManager ?: return
            if (state.value is State.Recording) { _state.value = (state.value as State.Recording).copy(recordingTime = recManager.recordingTime) }
            if (isInForeground) {
                val d = recManager.recordingTime
                val text = if (d.toHours() > 0) String.format(Locale.US, "%02d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.seconds % 60)
                else String.format(Locale.US, "%02d:%02d", d.toMinutes() % 60, d.seconds % 60)
                // [ИСПРАВЛЕНО] Добавлена проверка разрешений
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@RecordingService, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Не можем обновить уведомление, но сервис продолжит работать
                } else {
                    notificationManager.notify(notificationId, notificationBuilder.setContentText(getString(R.string.notification_text, text)).build())
                }
            }
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
    companion object { const val ACTION_TOGGLE_RECORDING = "CryptocamToggleRecording" }
    inner class RecordingServiceBinder : Binder() { val service: RecordingService get() = this@RecordingService }
    sealed class State(open val selectedCamera: SelectedCamera, open val flashOn: Boolean) {
        data class Recording(val resolution: Size, val surfaceRotation: Int, val recordingTime: Duration, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
        data class ReadyToRecord(val resolution: Size, val surfaceRotation: Int, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
        data class NotReadyToRecord(val useCasesInitialized: Boolean, override val selectedCamera: SelectedCamera, override val flashOn: Boolean) : State(selectedCamera, flashOn)
    }
}
