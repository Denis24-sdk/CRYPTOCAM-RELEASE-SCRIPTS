package com.tnibler.cryptocam.video

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.service.quicksettings.TileService
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.RemoteViews
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.*
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.preference.SettingsFragment
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import android.content.pm.PackageManager
import android.media.MediaCodecList
import android.media.MediaFormat

import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraFilter
import androidx.camera.core.ExperimentalCameraFilter
import android.hardware.camera2.CameraCharacteristics



import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraInfo
import androidx.core.app.NotificationCompat


@ExperimentalCameraFilter
class RecordingService : Service(), LifecycleOwner {
    private val VIBRATE_INTENSITY = 128
    private val TAG = javaClass.simpleName
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val binder = RecordingServiceBinder()
    private var isBound = false
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val notificationId = 1
    private val notificationBuilder by lazy { notificationBuilder(this) }

    private var recipients: Collection<KeyManager.X25519Recipient> = setOf()
    private var outputFileManager: OutputFileManager? = null
    private var recordingManager: RecordingManager? = null
    private val _state: MutableStateFlow<State> =
        MutableStateFlow(State.NotReadyToRecord(false, SelectedCamera.BACK, flashOn = false))
    val state = _state.asStateFlow()

    private lateinit var cameraSettings: CameraSettings
    private var cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoStreamCapture? = null
    private var camera: Camera? = null
    private lateinit var resolution: Size
    private val orientationEventListener: OrientationEventListener by lazy {
        buildOrientationEventListener()
    }
    var lastHandledOrientation: Orientation = Orientation.LAND_LEFT
        private set
    private var surfaceRotation: Int =
        Surface.ROTATION_90 // should be set by orientationChangedListener before it's used the first time

    private var isInForeground = false
    private var startRecordingAction = false
    private var stopServiceAfterRecording = false

    private val vibrator by lazy { ContextCompat.getSystemService(this, Vibrator::class.java)!! }

    private fun vibrateOnStart() {
        // TODO: Добавить проверку настройки из SharedPreferences
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun vibrateOnStop() {
        if (!sharedPreferences.getBoolean("vibrate_on_stop", true)) return

        val pattern = longArrayOf(0, 100, 80, 100) // чёткий "двойной импульс"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }


    private fun buildOrientationEventListener(): OrientationEventListener {
        return object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
//                Log.d(TAG, "onOrientationChanged: $orientation")
                val currentOrientation = when (orientation) {
                    in 75..134 -> Orientation.LAND_RIGHT
                    in 224..289 -> Orientation.LAND_LEFT
                    else -> Orientation.PORTRAIT
                }
                surfaceRotation = when (currentOrientation) {
                    Orientation.PORTRAIT -> Surface.ROTATION_0
                    Orientation.LAND_RIGHT -> Surface.ROTATION_270
                    Orientation.LAND_LEFT -> Surface.ROTATION_90
                }
                val orientationChanged = currentOrientation != lastHandledOrientation
                val isRecording = state.value is State.Recording
                val isReadyToRecord = state.value is State.ReadyToRecord
                val useCasesInitialized =
                    (state.value as? State.NotReadyToRecord)?.useCasesInitialized == true

                if ((useCasesInitialized || isReadyToRecord) && !isRecording && orientationChanged) {
                    Log.d(TAG, "Orientation changed, calling initRecording()")
                    lastHandledOrientation = currentOrientation
                    initRecording()
                }
                lastHandledOrientation = currentOrientation
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        resolution = getVideoResolutionFromPrefs()
        Log.d(TAG, "resolution from Preferences: $resolution")
        cameraSettings = CameraSettings(sharedPreferences)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        orientationEventListener.enable()
        (applicationContext as App).recordingService = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        orientationEventListener.disable()
        (applicationContext as App).recordingService = null
    }

    private var currentParams: RecordingParams? = null // Храним параметры текущей записи
    @Volatile private var isStopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() with action: ${intent?.action}")

        if (intent?.action == null) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            ApiConstants.ACTION_START -> {
                Log.d(TAG, "Foregrounding service to handle API start command.")
                foreground()
                handleStartCommand(intent)
            }
            ApiConstants.ACTION_STOP -> {
                // Уведомление уже должно быть, так как сервис работает. Просто вызываем остановку.
                handleStopCommand()
            }
            ACTION_TOGGLE_RECORDING -> {
                Log.d(TAG, "ACTION_TOGGLE_RECORDING received")
                if (state.value is State.Recording) {
                    handleStopCommand() // Используем общую логику остановки
                } else if (state.value is State.ReadyToRecord) {
                    val defaultIntent = Intent().apply {
                        putExtra(ApiConstants.EXTRA_MODE, ApiConstants.MODE_DAY)
                        putExtra(ApiConstants.EXTRA_RESOLUTION, "FHD")
                    }
                    foreground() // Перед стартом нужно показать уведомление
                    handleStartCommand(defaultIntent)
                }
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun handleStartCommand(intent: Intent) {
        if (state.value is State.Recording || isStopping) {
            Log.w(TAG, "Start command received, but recording is already in progress or stopping. Ignoring.")
            return
        }

        Log.d(TAG, "===> STEP 1: handleStartCommand CALLED")

        val mode = intent.getStringExtra(ApiConstants.EXTRA_MODE) ?: ApiConstants.MODE_DAY
        val resStr = intent.getStringExtra(ApiConstants.EXTRA_RESOLUTION) ?: "FHD"
        val codec = intent.getStringExtra(ApiConstants.EXTRA_CODEC) ?: ApiConstants.CODEC_AVC

        val useUltraWide: Boolean
        val fps: Int
        val oisEnabled: Boolean
        val eisEnabled: Boolean
        var lensFacing: Int

        when (mode) {
            ApiConstants.MODE_DAY -> {
                lensFacing = CameraSelector.LENS_FACING_BACK
                useUltraWide = true
                fps = 60
                oisEnabled = false
                eisEnabled = false
            }
            ApiConstants.MODE_NIGHT -> {
                lensFacing = CameraSelector.LENS_FACING_BACK
                useUltraWide = false
                fps = 30
                oisEnabled = true
                eisEnabled = false
            }
            ApiConstants.MODE_FRONT -> {
                lensFacing = CameraSelector.LENS_FACING_FRONT
                useUltraWide = false
                fps = 30
                oisEnabled = false
                eisEnabled = false
            }
            else -> {
                lensFacing = CameraSelector.LENS_FACING_BACK
                useUltraWide = false
                fps = 30
                oisEnabled = true
                eisEnabled = true
            }
        }

        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val resolution = when (resStr.uppercase()) {
            "HD" -> Size(1280, 720)
            "FHD" -> Size(1920, 1080)
            "2K" -> Size(2560, 1440)
            "4K" -> Size(3840, 2160)
            else -> Size(1920, 1080)
        }

        currentParams = RecordingParams(
            mode = mode,
            resolution = resolution,
            codec = codec,
            fps = fps,
            useUltraWide = useUltraWide,
            isOisEnabled = oisEnabled,
            isEisEnabled = eisEnabled
        )

        Log.d(TAG, "===> STEP 2: Parameters parsed successfully: $currentParams")

        if (state.value is State.NotReadyToRecord || state.value is State.ReadyToRecord) {
            Log.d(TAG, "===> STEP 3: Starting camera initialization process...")
            val app = (applicationContext as App)
            val keyManager: KeyManager = app.globalServices.get()
            init(keyManager.selectedRecipients.value)
            startRecordingAction = true

            if (cameraProvider == null) {
                Log.d(TAG, "CameraProvider is null. Calling initCamera().")
                initCamera()
            } else {
                Log.d(TAG, "CameraProvider already exists. Calling initUseCases().")
                initUseCases()
            }
        } else {
            Log.e(TAG, "Could not start initialization, service in unexpected state: ${state.value}")
        }
    }

    @SuppressLint("NewApi")
    private fun isHevcSupported(width: Int, height: Int, frameRate: Int): Boolean {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)

        val encoderName = mediaCodecList.findEncoderForFormat(format)
        if (encoderName == null) {
            Log.w(TAG, "HEVC codec is NOT supported on this device.")
            return false
        }

        Log.d(TAG, "HEVC codec found: $encoderName. Checking capabilities...")

        try {
            val codecInfo = mediaCodecList.codecInfos.first { it.name == encoderName }
            val capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            val videoCapabilities = capabilities.videoCapabilities

            if (videoCapabilities.isSizeSupported(width, height) &&
                videoCapabilities.getSupportedFrameRatesFor(width, height).contains(frameRate.toDouble())) {

                Log.d(TAG, "SUCCESS: HEVC is supported for ${width}x${height} @ ${frameRate}fps.")
                return true
            } else {
                Log.w(TAG, "FAILURE: HEVC codec exists, but does NOT support ${width}x${height} @ ${frameRate}fps.")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check HEVC capabilities", e)
            return false
        }
    }


    private fun handleStopCommand() {
        if (state.value !is State.Recording) {
            Log.w(TAG, "Stop command received, but not recording. Ignoring.")
            return
        }
        // --- НАЧАЛО ИЗМЕНЕНИЙ (Исправление гонки состояний) ---
        if (isStopping) {
            Log.w(TAG, "Stop command received, but service is already in stopping process. Ignoring.")
            return
        }
        isStopping = true
        // --- КОНЕЦ ИЗМЕНЕНИЙ ---

        Log.d(TAG, "Stopping recording via command.")
        // Устанавливаем флаг, чтобы callback знал, что нужно остановить сервис
        stopServiceAfterRecording = true
        stopRecording()
    }



    fun init(recipients: Collection<KeyManager.X25519Recipient>) {
        Log.d(TAG, "Service init() called.")
        this.recipients = recipients
    }

    fun startRecording() {
        val recordingManager = recordingManager ?: return
        if (state.value is State.ReadyToRecord) {
            recordingManager.recordButtonClicked()
            updateRecordingStateHandler.post(updateRecordingStateRunnable)
            _state.value = State.Recording(
                (state.value as State.ReadyToRecord).resolution,
                (state.value as State.ReadyToRecord).surfaceRotation,
                Duration.ZERO,
                (state.value as State.ReadyToRecord).selectedCamera,
                (state.value as State.ReadyToRecord).flashOn
            )
        }
    }



    fun stopRecording() {
        val recordingManager = recordingManager ?: return
        if (state.value is State.Recording) {
            recordingManager.recordButtonClicked()
            updateRecordingStateHandler.removeCallbacks(updateRecordingStateRunnable)

            _state.value = State.ReadyToRecord(
                (state.value as State.Recording).resolution,
                (state.value as State.Recording).surfaceRotation,
                (state.value as State.Recording).selectedCamera,
                (state.value as State.Recording).flashOn
            )
        }
    }



    fun toggleFlash() {
        val currentState = _state.value
        val newFlashState = !currentState.flashOn
        val cameraControl = camera?.cameraControl
        if (cameraControl == null) {
            debugToast("cameraControll is null in toggleFlash")
            return
        }
        cameraControl.enableTorch(newFlashState)
        _state.value = when (currentState) {
            is State.ReadyToRecord -> {
                currentState.copy(flashOn = newFlashState)
            }
            is State.Recording -> {
                currentState.copy(flashOn = newFlashState)
            }
            is State.NotReadyToRecord -> {
                currentState.copy(flashOn = newFlashState)
            }
        }
    }

    fun toggleCamera() {
        val currentState = _state.value
        val newSelectedCamera = currentState.selectedCamera.other()
        val newState = when (currentState) {
            is State.Recording -> return
            is State.ReadyToRecord -> {
                currentState.copy(selectedCamera = newSelectedCamera)
            }
            is State.NotReadyToRecord -> {
                currentState.copy(selectedCamera = newSelectedCamera)
            }
        }
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                when (newSelectedCamera) {
                    SelectedCamera.BACK -> CameraSelector.LENS_FACING_BACK
                    SelectedCamera.FRONT -> CameraSelector.LENS_FACING_FRONT
                }
            )
            .build()
        _state.value = State.NotReadyToRecord(false, newSelectedCamera, currentState.flashOn)
        when (cameraProvider) {
            null -> initCamera()
            else -> initUseCases()
        }
    }

    fun foreground() {
        Log.d(TAG, "foreground()")

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Attempted to start foreground service without FOREGROUND_SERVICE permission.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                notificationId,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notificationId, notificationBuilder.build())
        }
        NotificationManagerCompat.from(this).notify(notificationId, notificationBuilder.build())
        isInForeground = true
    }

    fun background() {
        Log.d(TAG, "background()")
        stopForeground(true)
        isInForeground = false
    }

    private fun initCamera() {
        Log.d(TAG, "===> STEP 4: initCamera CALLED")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                this.cameraProvider = cameraProvider
                Log.d(TAG, "===> STEP 5: CameraProvider received successfully. Initializing UseCases...")
                initUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "===> FATAL: Failed to get CameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }






    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    fun initUseCases() {
        Log.d(TAG, "===> STEP 6: initUseCases CALLED")
        val cameraProvider = cameraProvider ?: return
        cameraProvider.unbindAll()

        val params = currentParams
        if (params == null) {
            Log.e(TAG, "===> FATAL: initUseCases called but no RecordingParams were set. Aborting.")
            return
        }

        // --- НАЧАЛО БЛОКА ВЫБОРА КАМЕРЫ С ФОЛЛБЭКОМ ---
        val requestedIdKey = when (params.mode) {
            ApiConstants.MODE_DAY -> SettingsFragment.PREF_CAMERA_ID_DAY
            ApiConstants.MODE_NIGHT -> SettingsFragment.PREF_CAMERA_ID_NIGHT
            ApiConstants.MODE_FRONT -> SettingsFragment.PREF_CAMERA_ID_FRONT
            else -> null
        }

        val customCameraId = requestedIdKey?.let { sharedPreferences.getString(it, null)?.trim() }

        val cameraSelectorBuilder = CameraSelector.Builder()

        if (!customCameraId.isNullOrBlank()) {
            Log.d(TAG, "Manual camera ID specified: '$customCameraId'. Attempting to select.")
            cameraSelectorBuilder.addCameraFilter { cameraInfoList ->
                val matchingCamera = cameraInfoList.firstOrNull { cameraInfo ->
                    Camera2CameraInfo.from(cameraInfo).cameraId == customCameraId
                }
                if (matchingCamera != null) {
                    Log.d(TAG, "Successfully found camera with ID '$customCameraId'.")
                    listOf(matchingCamera)
                } else {
                    Log.w(TAG, "Camera with ID '$customCameraId' NOT FOUND. Falling back to default logic.")
                    // Если не нашли, возвращаем исходный список, чтобы сработал фоллбэк
                    cameraInfoList
                }
            }
        } else {
            Log.d(TAG, "No manual camera ID. Using auto-detection logic.")
            // Если ID не указан, используем автоматику
            if (params.useUltraWide) {
                Log.d(TAG, "Auto-detection: searching for wide-angle camera.")
                cameraSelectorBuilder.addCameraFilter { cameraInfoList ->
                    val widestCamera = cameraInfoList.minByOrNull { cameraInfo ->
                        try {
                            val characteristics = Camera2CameraInfo.extractCameraCharacteristics(cameraInfo)
                            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                            focalLengths?.minOrNull() ?: Float.MAX_VALUE
                        } catch (e: Exception) { Float.MAX_VALUE }
                    }
                    if (widestCamera != null) {
                        val cameraId = Camera2CameraInfo.from(widestCamera).cameraId
                        Log.d(TAG, "Auto-detection: selected camera with smallest focal length: $cameraId")
                        listOf(widestCamera)
                    } else {
                        cameraInfoList
                    }
                }
            } else {
                // Для ночного и фронтального режима просто выбираем по направлению
                val lensFacing = if (params.mode == ApiConstants.MODE_FRONT) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                Log.d(TAG, "Auto-detection: selecting camera with LENS_FACING = $lensFacing")
                cameraSelectorBuilder.requireLensFacing(lensFacing)
            }
        }
        // --- КОНЕЦ БЛОКА ВЫБОРА КАМЕРЫ ---

        val finalCameraSelector = cameraSelectorBuilder.build()

        var finalCodec = MediaFormat.MIMETYPE_VIDEO_AVC
        if (params.codec == ApiConstants.CODEC_HEVC) {
            Log.d(TAG, "HEVC codec requested. Attempting to use it.")
            finalCodec = MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        fun createVideoCaptureBuilder(): VideoStreamCapture.Builder {
            return VideoStreamCapture.Builder()
                .setVideoFrameRate(params.fps)
                .setCameraSelector(finalCameraSelector)
                .setTargetResolution(params.resolution)
                .setBitRate(cameraSettings.bitrate)
                .setTargetRotation(Surface.ROTATION_90)
                .setIFrameInterval(1)
                .setVideoCodec(finalCodec)
        }

        val builderWithSettings = createVideoCaptureBuilder()
        val extender = Camera2Interop.Extender(builderWithSettings)

        if (params.fps >= 50) {
            Log.d(TAG, "Applying high-speed FPS range via Camera2Interop")
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(params.fps, params.fps))
        }

        Log.d(TAG, "Applying stabilization settings: OIS=${params.isOisEnabled}, EIS=${params.isEisEnabled}")
        extender.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, if (params.isOisEnabled) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, if (params.isEisEnabled) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)

        try {
            Log.d(TAG, "Attempting to bind with custom settings...")
            videoCapture = builderWithSettings.build()
            camera = cameraProvider.bindToLifecycle(this, finalCameraSelector, videoCapture!!)
            Log.d(TAG, "===> STEP 7: bindToLifecycle successful WITH custom settings.")

        } catch (e: Exception) {
            Log.e(TAG, "Bind with custom settings FAILED. Falling back to default.", e)
            try {
                cameraProvider.unbindAll()
                val fallbackBuilder = createVideoCaptureBuilder()
                videoCapture = fallbackBuilder.build()
                camera = cameraProvider.bindToLifecycle(this, finalCameraSelector, videoCapture!!)
                Log.d(TAG, "===> STEP 7 (Fallback): bindToLifecycle successful WITHOUT custom settings.")
            } catch (e2: Exception) {
                Log.e(TAG, "===> FATAL: Fallback bind also FAILED. Cannot initialize camera.", e2)
                return
            }
        }

        camera?.cameraControl?.enableTorch(_state.value.flashOn)
        _state.value = State.NotReadyToRecord(true, state.value.selectedCamera, state.value.flashOn)
        initRecording()
    }





    @SuppressLint("RestrictedApi")
    private fun initRecording() {
        Log.d(TAG, "===> STEP 8: initRecording CALLED")
        Log.d(TAG, "initRecording()")
        _state.value = State.NotReadyToRecord(true, state.value.selectedCamera, state.value.flashOn)
        val outputLocation =
            sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
        if (recipients.isEmpty()) {
            debugToast(getString(R.string.no_key_selected))
            return
        }
        val actualRes = videoCapture?.attachedSurfaceResolution
        if (actualRes == null) {
            debugToast("actualRes is null in initRecording")
            return
        }

        val width = actualRes.width
        val height = actualRes.height

        if (16 * height != 9 * width) {
            debugToast("Actual recording resolution: ${width}x${height}. This is probably a bug.")
        } else if (Size(width, height) != resolution) {
            debugToast("Actual recording resolution: ${width}x${height}.")
        }
        val orientation = lastHandledOrientation

        Log.d(TAG, "Orientation: $orientation")
        val videoInfo = VideoInfo(
            bitrate = cameraSettings.bitrate,
            height = height,
            width = width,
            rotation = when (orientation) {
                Orientation.PORTRAIT -> 90
                Orientation.LAND_LEFT -> 0
                Orientation.LAND_RIGHT -> 180
            }
        )
        Log.d(TAG, "VideoInfo: $videoInfo")
        val videoCapture = checkNotNull(videoCapture)
        val audioInfo = AudioInfo(
            bitrate = videoCapture.audioBitRate,
            sampleRate = videoCapture.audioSampleRate,
            channelCount = videoCapture.audioChannelCount
        )
        outputFileManager = OutputFileManager(
            outputLocation = Uri.parse(outputLocation),
            contentResolver = contentResolver,
            context = this,
            sharedPreferences = sharedPreferences,
            recipients = recipients
        )

        recordingManager = RecordingManager(
            cameraSettings,
            videoCapture,
            videoInfo,
            audioInfo,
            ContextCompat.getMainExecutor(this),
            lifecycleScope,
            outputFileManager!!,
            recordingStoppedCallback = {
                // --- НАЧАЛО ИЗМЕНЕНИЙ (Исправление гонки состояний) ---
                Log.d(TAG, "RecordingManager: recordingStoppedCallback triggered.")
                isStopping = false // Сбрасываем флаг
                if (stopServiceAfterRecording) {
                    Log.d(TAG, "Callback: Stopping foreground and self.")
                    // Теперь это безопасное место для остановки сервиса
                    stopForeground(true)
                    isInForeground = false
                    stopSelf()
                }
                // --- КОНЕЦ ИЗМЕНЕНИЙ ---
            },

            // === ПЕРЕДАЁМ ВИБРАЦИЮ ===
            onRecordingStarted = { vibrateOnStart() },
            onRecordingStopped = { vibrateOnStop() }

        )
        lifecycleScope.launchWhenStarted {
            Log.d(TAG, "===> STEP 9: RecordingManager setup starting...")
            recordingManager!!.setUp()
            Log.d(TAG, "===> STEP 10: RecordingManager setup complete. State is being set to ReadyToRecord.")
            when (val currentState = _state.value) {
                is State.NotReadyToRecord -> {
                    _state.value = State.ReadyToRecord(
                        resolution,
                        surfaceRotation,
                        currentState.selectedCamera,
                        currentState.flashOn
                    )
                    if (startRecordingAction) {
                        startRecordingAction = false
                        Log.d(TAG, "===> STEP 11: startRecordingAction is true. Starting recording now...")
                        delay(400)
                        startRecording()
                    }
                }
                else -> {
                    Log.w(TAG, "onReadyToRecord called but state is $currentState!")
                }
            }
        }
    }

    fun scaleZoomRatio(scaleFactor: Float) {
        val currentZoomRatio: Float? = camera?.cameraInfo?.zoomState?.value?.zoomRatio
        if (currentZoomRatio == null) {
            when {
                camera == null -> debugToast("camera is null in scaleZoomRatio")
                camera?.cameraInfo == null -> debugToast("cameraInfo is null in scaleZoomRatio")
                camera?.cameraInfo?.zoomState == null -> debugToast("zoomState is null in scaleZoomRatio")
                else -> debugToast("zoomRatio is null in scaleZoomRatio")
            }
            return
        }
        val cameraControl = camera?.cameraControl
        if (cameraControl == null) {
            debugToast("cameraControl is null in scaleZoomRatio")
            return
        }
        cameraControl.setZoomRatio(currentZoomRatio * scaleFactor)
    }

    fun startFocusAndMetering(action: FocusMeteringAction) {
        val cameraControl = camera?.cameraControl
        if (cameraControl == null) {
            when {
                camera == null -> debugToast("camera is null in startFocusAndMetering")
                else -> debugToast("cameraControl is null in startFocusAndMetering")
            }

            return
        }
        cameraControl.startFocusAndMetering(action)
    }

    fun bindUseCase(useCase: UseCase) {
        val cameraProvider = cameraProvider
        if (cameraProvider == null) {
            debugToast("cameraProvider is null in bindUseCase")
            return
        }
        cameraProvider.bindToLifecycle(this, cameraSelector, useCase)
    }

    fun unbindUseCase(useCase: UseCase) {
        val cameraProvider = cameraProvider
        if (cameraProvider == null) {
            debugToast("cameraProvider is null in unbindUseCase")
            return
        }
        cameraProvider.unbind(useCase)
    }


    private val updateRecordingStateHandler = Handler(Looper.getMainLooper())
    private val updateRecordingStateRunnable: Runnable = object : Runnable {
        override fun run() {
            val recordingManager = recordingManager
            if (recordingManager == null) {
                debugToast("recordingManagerNull in updateRecordingStateRunnable")
                return
            }
            _state.value =
                (_state.value as State.Recording).copy(recordingTime = recordingManager.recordingTime)
            if (isInForeground) {
                val d = recordingManager.recordingTime
                val text = if (d.toHours() > 0) {
                    String.format(
                        "%02d:%02d:%02d",
                        d.toHours(),
                        d.toMinutes() % 60,
                        d.seconds % 60
                    )
                } else {
                    String.format("%02d:%02d", d.toMinutes() % 60, d.seconds % 60)
                }
                val n = (notificationBuilder as NotificationCompat.Builder)
                    .setContentText(getString(R.string.notification_text, text))
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@RecordingService, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                } else {
                    notificationManager.notify(notificationId, n)
                }
            }
            updateRecordingStateHandler.postDelayed(this, 200)
        }
    }

    private fun getVideoResolutionFromPrefs(): Size {
        val r = sharedPreferences.getString(
            SettingsFragment.PREF_VIDEO_RESOLUTION,
            SettingsFragment.DEFAULT_RESOLUTION
        ) ?: SettingsFragment.DEFAULT_RESOLUTION
        val s = r.split("x")
        return Size(s[0].toInt(), s[1].toInt())
    }

    private fun buildNotificationRemoteViews(): RemoteViews {
        val remoteView = RemoteViews(packageName, R.layout.custom_notification_pixel)
        return remoteView
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder {
        isBound = true
        Log.d(TAG, "stopServiceAfterRecording = false")
        stopServiceAfterRecording = false
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        return super.onUnbind(intent)
    }

    private fun debugToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val ACTION_TOGGLE_RECORDING = "CryptocamToggleRecording"
    }

    inner class RecordingServiceBinder : Binder() {
        val service: RecordingService
            get() = this@RecordingService
    }

    sealed class State(open val selectedCamera: SelectedCamera, open val flashOn: Boolean) {
        data class Recording(
            val resolution: Size,
            val surfaceRotation: Int,
            val recordingTime: Duration,
            override val selectedCamera: SelectedCamera,
            override val flashOn: Boolean
        ) : State(selectedCamera, flashOn)

        data class ReadyToRecord(
            val resolution: Size,
            val surfaceRotation: Int,
            override val selectedCamera: SelectedCamera,
            override val flashOn: Boolean
        ) : State(selectedCamera, flashOn)

        data class NotReadyToRecord(
            val useCasesInitialized: Boolean,
            override val selectedCamera: SelectedCamera,
            override val flashOn: Boolean
        ) : State(selectedCamera, flashOn)
    }

}