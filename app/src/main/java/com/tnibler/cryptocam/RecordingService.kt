package com.tnibler.cryptocam

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.impl.VideoStreamCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.videoProcessing.VideoAudioMuxer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration

class RecordingService : Service(), LifecycleOwner {
    private val FEEDBACK_INTERVAL = 5_000 // vibrate every ~5 seconds while recording
    private val VIBRATE_INTENSITY = 128
    private val TAG = javaClass.simpleName
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val binder = RecordingServiceBinder()
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val notificationId = 1
    private val notificationBuilder by lazy {
        val resultIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Notification.Builder(this, App.CHANNEL_ID)
                    .setContentTitle(getString(R.string.notification_title))
                    .setContentText(getString(R.string.notification_text))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setLocalOnly(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
            }
            else -> {
                Notification.Builder(this)
                    .setContentTitle(getString(R.string.notification_title))
                    .setContentText(getString(R.string.notification_text))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setLocalOnly(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
            }
        }
    }

    private lateinit var openPgpKeyManager: OpenPgpManager
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

    fun initOpenPgp(openPgpKeyManager: OpenPgpManager) {
        Log.d(TAG, "initOpenPgp()")
        this.openPgpKeyManager = openPgpKeyManager
        if (cameraProvider == null) {
            initCamera()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        orientationEventListener.disable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        return START_REDELIVER_INTENT
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
        val videoCaptureBuilder = VideoStreamCaptureConfig.Builder()
            .setVideoFrameRate(60)
            .setCameraSelector(cameraSelector)
            .setTargetResolution(resolution)
            .setTargetRotation(Surface.ROTATION_90)
            .setBitRate(10000000)
            .setIFrameInterval(2)
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
        val keyIds = sharedPreferences.getStringSet(SettingsFragment.PREF_OPENPGP_KEYIDS, setOf())
            ?.map { it.toLong() }
        if (keyIds == null) {
            debugToast("keyIds is null in initRecording")
            return
        } else if (keyIds.isEmpty()) {
            debugToast("keyIds is empty in initRecording")
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
        val videoInfo = VideoAudioMuxer.VideoInfo(
            bitrate = cameraSettings.bitrate,
            framerate = cameraSettings.frameRate,
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
        val audioInfo = VideoAudioMuxer.AudioInfo(
            bitrate = videoCapture.audioBitRate,
            sampleRate = videoCapture.audioSampleRate,
            channelCount = videoCapture.audioChannelCount
        )
        outputFileManager = OutputFileManager(
            openPgpManager = openPgpKeyManager,
            outputLocation = Uri.parse(outputLocation),
            keyIds = keyIds,
            contentResolver = contentResolver,
            context = this
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
                        )
                    ) {
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

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun debugToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    inner class RecordingServiceBinder : Binder() {
        val service: RecordingService
            get() = this@RecordingService
    }

    enum class SelectedCamera {
        FRONT, BACK;

        fun other() = when (this) {
            BACK -> FRONT
            FRONT -> BACK
        }
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

    enum class Orientation {
        PORTRAIT, LAND_LEFT, LAND_RIGHT
    }
}