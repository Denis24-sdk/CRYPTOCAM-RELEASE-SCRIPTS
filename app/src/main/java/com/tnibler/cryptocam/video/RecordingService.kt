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

class RecordingService : Service(), LifecycleOwner {
    private val FEEDBACK_INTERVAL = 5_000 // vibrate every ~5 seconds while recording
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

    private var lastVibrateFeedback = 0L
    private var isInForeground = false
    private var startRecordingAction = false
    private var stopServiceAfterRecording = false

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        if (intent?.action == ACTION_TOGGLE_RECORDING) {
            Log.d(TAG, "ACTION_TOGGLE_RECORDING")
            startRecordingAction = true
            stopServiceAfterRecording = true
            Log.d(TAG, "stopServiceAfterRecording = true")
            if (_state.value is State.ReadyToRecord || _state.value is State.NotReadyToRecord) {
                if (!isBound) {
                    Log.d(TAG, "Service not bound, foregrounding")
                    foreground()
                } else {
                    // the service is started with startForeground() on Android >= O, so we need to call
                    // startForeground() in here even if we don't want to be in foreground.
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                        foreground()
                        background()
                    }
                }
            }
            when (_state.value) {
                is State.Recording -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        TileService.requestListeningState(
                            this,
                            ComponentName(this, RecordTileService::class.java)
                        )
                    }
                    toggleRecording()
                }
                is State.NotReadyToRecord -> {
                    Log.d(TAG, "Initializing from onStartCommand")
                    val app = (applicationContext as App)
                    val keyManager: KeyManager = app.globalServices.get()
                    init(keyManager.selectedRecipients.value)
                }
                is State.ReadyToRecord -> {
                    toggleRecording()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    fun init(recipients: Collection<KeyManager.X25519Recipient>) {
        this.recipients = recipients
        if (cameraProvider == null) {
            initCamera()
        }
    }

    fun toggleRecording() {
        Log.d(TAG, "toggleRecording()")
        val recordingManager = recordingManager
        if (recordingManager == null) {
            Toast.makeText(this, "RecordingService: recordingManager is null", Toast.LENGTH_LONG)
                .show()
            return
        }
        _state.value = when (val currentState = _state.value) {
            is State.NotReadyToRecord -> currentState
            is State.Recording -> {
                when (recordingManager.recordButtonClicked()) {
                    RecordingManager.State.NOT_RECORDING -> {
                        updateRecordingStateHandler.removeCallbacks(updateRecordingStateRunnable)
                        stopForeground(true)
                        isInForeground = false
                        State.ReadyToRecord(
                            currentState.resolution,
                            currentState.surfaceRotation,
                            currentState.selectedCamera,
                            currentState.flashOn
                        )
                    }
                    RecordingManager.State.RECORDING -> currentState
                }
            }
            is State.ReadyToRecord -> {
                when (recordingManager.recordButtonClicked()) {
                    RecordingManager.State.RECORDING -> {
                        updateRecordingStateHandler.post(updateRecordingStateRunnable)
                        lastVibrateFeedback = System.currentTimeMillis()
                        State.Recording(
                            currentState.resolution,
                            currentState.surfaceRotation,
                            Duration.ZERO,
                            currentState.selectedCamera,
                            currentState.flashOn
                        )
                    }
                    RecordingManager.State.NOT_RECORDING -> currentState
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, ComponentName(this, RecordTileService::class.java))
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
        Log.d(TAG, "initCamera()")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider
            initUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("RestrictedApi")
    fun initUseCases() {
        Log.d(TAG, "initUseCases()")
        val cameraProvider = cameraProvider
        if (cameraProvider == null) {
            debugToast("cameraProvider is null in initUseCases")
            return
        }
        cameraProvider.unbindAll()
        Log.d(
            TAG,
            "Building videoCapture with resolution=$resolution, targetRotation=$surfaceRotation"
        )
        Log.d(TAG, "Framerate: ${cameraSettings.frameRate}")
        val videoCaptureBuilder = VideoStreamCapture.Builder()
            .setVideoFrameRate(cameraSettings.frameRate)
            .setCameraSelector(cameraSelector)
            .setTargetResolution(resolution)
            .setBitRate(cameraSettings.bitrate)
            .setTargetRotation(Surface.ROTATION_90)
            .setIFrameInterval(1)
        videoCapture = videoCaptureBuilder.build()
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)
        camera?.cameraControl?.enableTorch(_state.value.flashOn)
        _state.value = State.NotReadyToRecord(true, state.value.selectedCamera, state.value.flashOn)
        initRecording()
    }

    @SuppressLint("RestrictedApi")
    private fun initRecording() {
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
        val shouldVibrate =
            sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_WHILE_RECORDING, true)
        recordingManager = RecordingManager(
            cameraSettings,
            videoCapture,
            videoInfo,
            audioInfo,
            ContextCompat.getMainExecutor(this),
            lifecycleScope,
            outputFileManager!!,
            recordingStoppedCallback = {
                if (stopServiceAfterRecording) {
                    Log.d(TAG, "recordingStoppedCallback: stopSelf()")
                    stopSelf()
                }
            },
            videoPacketCallback = {
                val now = System.currentTimeMillis()
                if (shouldVibrate && now - lastVibrateFeedback >= FEEDBACK_INTERVAL) {
                    if (isInForeground) {
                        vibrate()
                    }
                    lastVibrateFeedback = now
                }
            }
        )
        lifecycleScope.launchWhenStarted {
            recordingManager!!.setUp()
            when (val currentState = _state.value) {
                is State.NotReadyToRecord -> {
                    _state.value = State.ReadyToRecord(
                        resolution,
                        surfaceRotation,
                        currentState.selectedCamera,
                        currentState.flashOn
                    )
                    if (!(application as App).startedRecordingOnLaunch && sharedPreferences.getBoolean(
                            SettingsFragment.PREF_RECORD_ON_START,
                            false
                        ) || startRecordingAction
                    ) {
                        startRecordingAction = false
                        delay(400)
                        (application as App).startedRecordingOnLaunch = true
                        toggleRecording()
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

    private val vibrator by lazy { ContextCompat.getSystemService(this, Vibrator::class.java)!! }
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VIBRATE_INTENSITY))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
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
                val n =
                    notificationBuilder.setContentText(getString(R.string.notification_text, text))
                        .build()
                notificationManager.notify(notificationId, n)
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