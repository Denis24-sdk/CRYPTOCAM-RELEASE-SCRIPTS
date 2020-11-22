package com.tnibler.cryptocam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.MyCamera2Config
import androidx.camera.core.*
import androidx.camera.core.impl.VideoStreamCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.CameraScreenBinding
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.videoProcessing.VideoAudioMuxer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {
    private val TAG = javaClass.simpleName
    private var displayId = -1
    private lateinit var binding: CameraScreenBinding
    private lateinit var preview: Preview
    private lateinit var videoCapture: VideoStreamCapture
    private lateinit var displayManager: DisplayManager
    private lateinit var cameraSettings: CameraSettings
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null
    private var recordingManager: RecordingManager? = null
    private var outputFileManager: OutputFileManager? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var camera: Camera? = null
    private var flashMode: FlashMode = FlashMode.OFF
    private lateinit var orientationEventListener: OrientationEventListener
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        cameraSettings =
            CameraSettings(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView")
        binding = CameraScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStop() {
        super.onStop()
        outputFileManager?.fileNotUsed()
        Log.d(TAG, "onStop")
    }

    private var lastHandledOrientation: Orientation? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        binding.run {

            if (!allPermissionsGranted()) {
                val requestPermissions = ActivityResultContracts.RequestMultiplePermissions()
                registerForActivityResult(requestPermissions) { result ->
                    if (allPermissionsGranted()) {
                        binding.viewFinder.post {
                            initCamera()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Permissions not granted by the user.",
                            Toast.LENGTH_SHORT
                        ).show()
                        activity?.finish()
                    }
                }.launch(REQUIRED_PERMISSIONS)
                return
            }

            // wait until views are all set up otherwise viewFinder.display may be null
            viewFinder.post {
                Log.d(TAG, "onStart: initializing camera")
                initCamera()
            }
            displayManager =
                requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            btnToggleCamera.setOnClickListener { toggleCamera() }

            btnRecordVideo.visibility = View.INVISIBLE
            btnRecordVideo.setOnClickListener {
                try {
                    val state = recordingManager?.recordButtonClicked() ?: run {
                        Log.e(TAG, "btnRecordVideo.onClick: RecordingManager is null!")
                        return@setOnClickListener
                    }
                    when (state) {
                        RecordingManager.State.NOT_RECORDING -> {
                            btnRecordVideo.visibility = View.INVISIBLE
                            btnRecordVideo.setImageResource(R.drawable.ic_record_video)
                            uiMode = UiState.NOT_RECORDING
                        }
                        RecordingManager.State.RECORDING -> {
                            btnRecordVideo.visibility = View.VISIBLE
                            btnRecordVideo.setImageResource(R.drawable.ic_stop_video)
                            uiMode = UiState.RECORDING
                        }
                    }
                }
                catch (e: EncryptionException) {
                    Toast.makeText(requireContext(), getString(R.string.error_encrypting), Toast.LENGTH_LONG).show()
                }
            }
            btnSettings.setOnClickListener {
                Navigation.findNavController(root)
                    .navigate(R.id.action_cameraFragment_to_settingsFragment)
            }
            uiMode = UiState.NOT_RECORDING
            btnFlash.setOnClickListener {
                toggleFlash()
            }
            setFlashMode(flashMode)

            if (sharedPreferences.getBoolean(SettingsFragment.PREF_OVERLAY, false)) {
                lifecycleScope.launch {
                    while (true) {
                        overlayText.visibility = when (overlayText.visibility) {
                            View.VISIBLE -> View.INVISIBLE
                            else ->  View.VISIBLE
                        }
                        delay(1000)
                    }
                }
            }
            else {
                overlayText.visibility = View.GONE
            }
        }
    }

    private fun toggleFlash() {
        when (flashMode) {
            FlashMode.OFF -> setFlashMode(FlashMode.ON)
            FlashMode.ON -> setFlashMode(FlashMode.OFF)
        }
    }

    private fun setFlashMode(flashMode: FlashMode) {
        this.flashMode = flashMode
        when (flashMode) {
            FlashMode.OFF -> {
                binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
                camera?.cameraControl?.enableTorch(false)
            }
            FlashMode.ON -> {
                binding.btnFlash.setImageResource(R.drawable.ic_flash_on)
                camera?.cameraControl?.enableTorch(true)
            }
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun initCamera() {
        Log.d(TAG, "initCamera")
        try {
            ProcessCameraProvider.configureInstance(MyCamera2Config.defaultConfig())
        }
        catch (e: IllegalStateException) {
            // already configured
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider
            Log.d(TAG, "initCamera: initializing use cases")
            initUseCases()
            initRecordingAndOutputFiles(lastHandledOrientation ?: Orientation.PORTRAIT)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("RestrictedApi", "ClickableViewAccessibility")
    private fun initUseCases() {
        Log.d(TAG, "initUseCases")
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        preview = Preview.Builder()
//            .setTargetAspectRatio(cameraSettings.aspectRatio)
            .setTargetResolution(getVideoResolution())
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()

        Log.d(TAG, "Setting resolution to ${getVideoResolution()}")
        val videoCaptureBuilder = VideoStreamCaptureConfig.Builder()
            .setVideoFrameRate(cameraSettings.frameRate)
            .setCameraSelector(cameraSelector)
//            .setTargetAspectRatio(cameraSettings.aspectRatio)
            .setTargetResolution(getVideoResolution())
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBitRate(cameraSettings.bitrate)
            .setIFrameInterval(2)
//        val extender = Camera2Interop.Extender(videoCaptureBuilder)
//        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(24, 60))

        videoCapture = videoCaptureBuilder.build()

        try {
            cameraProvider?.unbindAll()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }
        catch (e: Exception) {
            Log.e(TAG, "Failed to bind use case", e)
        }
        val camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, videoCapture) ?: throw RuntimeException("Error binding use cases")
        this.camera = camera

//        camera.cameraControl.enableTorch(true)
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio: Float = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1.0F
                camera.cameraControl.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                return true
            }
        })

        val gestureDetector = GestureDetector(requireContext(), object :
            GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera.cameraControl.startFocusAndMetering(action)
                return true
            }
        })

        binding.viewFinder.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            if (scaleGestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            false
        }
    }

    private fun getVideoResolution(): Size {
        val r = sharedPreferences.getString(SettingsFragment.PREF_VIDEO_RESOLUTION, SettingsFragment.DEFAULT_RESOLUTION) ?: SettingsFragment.DEFAULT_RESOLUTION
        val s = r.split("x")
        return Size(s[1].toInt(), s[0].toInt())
    }

    @SuppressLint("RestrictedApi")
    private fun initRecordingAndOutputFiles(orientation: Orientation) {
        val outputLocation = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
        val keyIds = sharedPreferences.getStringSet(SettingsFragment.PREF_OPENPGP_KEYIDS, setOf())?.map { it.toLong() } ?: listOf()
        val actualRes = videoCapture.attachedSurfaceResolution ?: throw RuntimeException()
//        Toast.makeText(requireContext(), getString(R.string.actual_recording_resolution, actualRes), Toast.LENGTH_LONG).show()
        Log.d(TAG, "actual resolution: $actualRes")
        val onReadyToRecord = {
            Log.d(TAG, "onReadyToRecord")
            binding.btnRecordVideo.visibility = View.VISIBLE
        }
        val width = if (lastHandledOrientation == Orientation.PORTRAIT) actualRes.height else actualRes.width
        val height = if (lastHandledOrientation == Orientation.PORTRAIT) actualRes.width else actualRes.height
        val videoInfo = VideoAudioMuxer.VideoInfo(
            bitrate = cameraSettings.bitrate,
            framerate = cameraSettings.frameRate,
            height = height,
            width = width,
            rotation = when (binding.viewFinder.display.rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            } + when (orientation) {
                Orientation.PORTRAIT -> 90
                Orientation.LAND_RIGHT -> 180
                Orientation.LAND_LEFT -> 0
                else -> 0
            }
        )
        Log.d(TAG, "$videoInfo")
        val audioInfo = VideoAudioMuxer.AudioInfo(
            bitrate = videoCapture.audioBitRate,
            sampleRate = videoCapture.audioSampleRate,
            channelCount = videoCapture.audioChannelCount
        )
        outputFileManager = OutputFileManager(
            openPgpManager = (requireActivity() as MainActivity).openPgpKeyManager,
            outputLocation = Uri.parse(outputLocation),
            keyIds = keyIds,
            contentResolver = requireContext().contentResolver,
            context = requireContext()
        )
        recordingManager = RecordingManager(
            cameraSettings,
            videoCapture,
            videoInfo,
            audioInfo,
            onReadyToRecord,
            ContextCompat.getMainExecutor(requireContext()),
            viewLifecycleOwner.lifecycleScope,
            outputFileManager!!
        )
    }

    private fun toggleCamera() {
        Log.d(TAG, "toggleCamera")
        when (lensFacing) {
            CameraSelector.LENS_FACING_BACK -> {
                binding.btnToggleCamera.setImageResource(R.drawable.ic_outline_camera_front)
                lensFacing = CameraSelector.LENS_FACING_FRONT
            }
            CameraSelector.LENS_FACING_FRONT -> {
                binding.btnToggleCamera.setImageResource(R.drawable.ic_outline_camera_rear)
                lensFacing = CameraSelector.LENS_FACING_BACK
            }
        }
        when (cameraProvider) {
            null -> initCamera()
            else -> {
                initUseCases()
                initRecordingAndOutputFiles(lastHandledOrientation ?: Orientation.PORTRAIT)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun rotateUiTo(degrees: Int) {
        binding.run {
            listOf(btnFlash, btnToggleCamera, btnSettings, btnRecordVideo, layoutRecordingTime)
                .forEach { v ->
                    v.animate().rotation(degrees.toFloat()).start()
                }
        }
    }

    private val recordingTimeTextHandler: Handler = Handler()
    private val recordingTimeTextRunnable: Runnable = object : Runnable {
        override fun run() {
            val d = recordingManager?.recordingTime ?: return
            if (d.toHours() > 0) {
                binding.recordingTime.text = String.format("%02d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.seconds % 60)
            }
            else {
                binding.recordingTime.text = String.format("%02d:%02d", d.toMinutes() % 60, d.seconds % 60)
            }
            recordingTimeTextHandler.postDelayed(this, 100)
        }
    }

    private val dotBlinkAnimator = AlphaAnimation(0.0f, 1.0f).apply {
        duration = 1000
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
        interpolator = DecelerateInterpolator()
    }
    private var uiMode: UiState = UiState.NOT_RECORDING
        set(value) {
            field = value
            when (value) {
                UiState.NOT_RECORDING -> {
                    binding.dotRecording.clearAnimation()
                    binding.layoutRecordingTime.visibility = View.GONE
                    binding.btnSettings.visibility = View.VISIBLE
                    binding.btnToggleCamera.visibility = View.VISIBLE
                }
                UiState.RECORDING -> {
                    recordingTimeTextHandler.post(recordingTimeTextRunnable)
                    binding.dotRecording.startAnimation(dotBlinkAnimator)
                    binding.layoutRecordingTime.visibility = View.VISIBLE
                    binding.btnSettings.visibility = View.INVISIBLE
                    binding.btnToggleCamera.visibility = View.GONE
                }
            }
        }


    override fun onResume() {
        super.onResume()
        orientationEventListener =
            object : OrientationEventListener(requireContext(), SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    Log.d(
                        TAG,
                        "onOrientationChanged, orientation=$orientation, recordingManager==$recordingManager"
                    )
                    val currentOrientation = when (orientation) {
                        in 75..134 -> Orientation.LAND_RIGHT
                        in 224..289 -> Orientation.LAND_LEFT
                        else -> Orientation.PORTRAIT
                    }
                    if (currentOrientation != lastHandledOrientation) {
                        val rotateTo = when (currentOrientation) {
                            Orientation.LAND_LEFT -> 90.also { Log.d(TAG, "land left") }
                            Orientation.LAND_RIGHT -> (-90).also { Log.d(TAG, "land right") }
                            Orientation.PORTRAIT -> 0.also { Log.d(TAG, "portrait") }
                        }
                        rotateUiTo(rotateTo)
                        if (recordingManager?.state == RecordingManager.State.NOT_RECORDING) {
                            initRecordingAndOutputFiles(currentOrientation)
                        }
                    }
                    lastHandledOrientation = currentOrientation
                }
            }
        orientationEventListener.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener.disable()
    }

    override fun onDetach() {
        super.onDetach()
        cameraProvider?.unbindAll()
        camera = null
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE_PERMISSIONS = 10

        private enum class UiState {
            RECORDING, NOT_RECORDING
        }

        private enum class FlashMode {
            ON, OFF
        }

        private enum class Orientation {
            PORTRAIT, LAND_LEFT, LAND_RIGHT
        }
    }

}