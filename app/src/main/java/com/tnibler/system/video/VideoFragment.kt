package com.tnibler.system.video

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalCameraFilter
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.system.Orientation
import com.tnibler.system.R
import com.tnibler.system.SelectedCamera
import com.tnibler.system.databinding.VideoScreenBinding
import com.tnibler.system.preference.SettingsKey
import com.zhuinden.simplestackextensions.fragmentsktx.backstack

@ExperimentalCameraFilter
class VideoFragment : Fragment() {
    private val TAG = javaClass.simpleName
    private var preview: Preview? = null
    private var binding: VideoScreenBinding? = null
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    var service: RecordingService? = null
        private set
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            this@VideoFragment.service = (service as RecordingService.RecordingServiceBinder).service
            onServiceBound(this@VideoFragment.service!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            this@VideoFragment.service = null
        }
    }

    private var currentCamera: SelectedCamera? = null
        /*
    private val orientationEventListener by lazy {
        object : OrientationEventListener(requireContext(), SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                val currentOrientation = when (orientation) {
                    in 75..134 -> Orientation.LAND_RIGHT
                    in 224..289 -> Orientation.LAND_LEFT
                    else -> Orientation.PORTRAIT
                }
                if (currentOrientation != lastHandledOrientation) {
                    rotateUiTo(currentOrientation)
                }
                lastHandledOrientation = currentOrientation
            }
        }
    }
    */

    private var lastHandledOrientation: Orientation? = null

    private fun onServiceBound(service: RecordingService) {
        Log.d(TAG, "onServiceBound")
        onStateChanged(service.state.value)
        lifecycleScope.launchWhenResumed {
            service.state.collect { state ->
                onStateChanged(state)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.video_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = VideoScreenBinding.bind(view)
        val binding = binding!!
        if (!allPermissionsGranted(requireContext())) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (allPermissionsGranted(requireContext())) {
                    setupUi(binding)
                    service?.initUseCases()
                } else {
                    Toast.makeText(requireContext(), "Permissions not granted.", Toast.LENGTH_SHORT).show()
                    activity?.finish()
                }
            }.launch(REQUIRED_PERMISSIONS)
            return
        }
        setupUi(binding)
    }

    private fun setupUi(binding: VideoScreenBinding) {
        binding.run {
            btnFlash.setOnClickListener { service?.toggleFlash() }
            btnToggleCamera.setOnClickListener { service?.toggleCamera() }
            btnRecordVideo.setOnClickListener {
                val currentService = service ?: return@setOnClickListener
                when (currentService.state.value) {
                    is RecordingService.State.Recording -> currentService.stopRecording()
                    is RecordingService.State.ReadyToRecord -> currentService.startRecording()
                    else -> Log.d(TAG, "Record button pressed, but service is not in a state to react.")
                }
            }
            btnSettings.setOnClickListener { backstack.goTo(SettingsKey()) }

        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (service == null) {
            Log.d(TAG, "Starting and binding Recording service")
            val intent = Intent(requireContext(), RecordingService::class.java)
            requireContext().startService(intent)
            requireContext().bindService(intent, connection, Context.BIND_ABOVE_CLIENT)
        } else {
            onServiceBound(service!!)
        }
    }

    private fun onStateChanged(state: RecordingService.State) {
        binding?.run {
            if (state.selectedCamera != currentCamera || preview == null) {
                when (state) {
                    is RecordingService.State.ReadyToRecord, is RecordingService.State.Recording -> {
                        val resolution = (state as? RecordingService.State.ReadyToRecord)?.resolution ?: (state as RecordingService.State.Recording).resolution
                        val surfaceRotation = (state as? RecordingService.State.ReadyToRecord)?.surfaceRotation ?: (state as RecordingService.State.Recording).surfaceRotation
                        setUpPreview(resolution, surfaceRotation, this)
                        currentCamera = state.selectedCamera
                    }
                    else -> { /* NotReadyToRecord */ }
                }
            }
            btnFlash.setImageResource(if (state.flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)

            when (state) {
                is RecordingService.State.NotReadyToRecord -> {
                    dotRecording.clearAnimation()
                    animationStarted = false
                    layoutRecordingTime.visibility = View.INVISIBLE
                    btnSettings.visibility = View.VISIBLE
                    btnToggleCamera.visibility = View.VISIBLE
                    btnRecordVideo.visibility = View.INVISIBLE
                }
                is RecordingService.State.Recording -> {
                    val d = state.recordingTime
                    recordingTime.text = if (d.toHours() > 0) {
                        String.format("%02d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.seconds % 60)
                    } else {
                        String.format("%02d:%02d", d.toMinutes() % 60, d.seconds % 60)
                    }
                    if (!animationStarted) {
                        dotRecording.startAnimation(dotBlinkAnimator)
                        animationStarted = true
                    }
                    layoutRecordingTime.visibility = View.VISIBLE
                    btnSettings.visibility = View.INVISIBLE
                    btnToggleCamera.visibility = View.INVISIBLE
                    btnRecordVideo.visibility = View.VISIBLE
                    btnRecordVideo.setImageResource(R.drawable.ic_stop_video)
                }
                is RecordingService.State.ReadyToRecord -> {
                    dotRecording.clearAnimation()
                    animationStarted = false
                    layoutRecordingTime.visibility = View.INVISIBLE
                    btnSettings.visibility = View.VISIBLE
                    btnToggleCamera.visibility = View.VISIBLE
                    btnToggleCamera.setImageResource(
                        when (state.selectedCamera) {
                            SelectedCamera.BACK -> R.drawable.ic_outline_camera_front
                            SelectedCamera.FRONT -> R.drawable.ic_outline_camera_rear
                        }
                    )
                    btnRecordVideo.visibility = View.VISIBLE
                    btnRecordVideo.setImageResource(R.drawable.ic_record_video)
                }
            }
        }
    }

    private fun setUpPreview(resolution: Size, surfaceRotation: Int, binding: VideoScreenBinding) = binding.run {
        val currentService = service ?: run {
            debugToast("service is null in setUpPreview")
            return
        }
        preview?.let { currentService.unbindUseCase(it) }

        val display = view?.display
        if (display == null) {
            debugToast("display is null in setUpPreview")
            return
        }

        val newPreview = Preview.Builder()
            .setTargetRotation(display.rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()

        viewFinder.scaleType = PreviewView.ScaleType.FIT_START
        preview = newPreview
        currentService.bindUseCase(newPreview)
        newPreview.setSurfaceProvider(viewFinder.surfaceProvider)

        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentService.scaleZoomRatio(detector.scaleFactor)
                return true
            }
        })
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                val action = FocusMeteringAction.Builder(binding.viewFinder.meteringPointFactory.createPoint(event.x, event.y)).build()
                currentService.startFocusAndMetering(action)
                return true
            }
        })
        binding.viewFinder.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        val currentService = service
        preview?.let { currentService?.unbindUseCase(it) }
        preview = null

        if (currentService != null) {

            try {
                requireContext().unbindService(connection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not registered to be unbound.")
            }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        binding = null
    }

    private fun rotateUiTo(currentOrientation: Orientation) {
        val degrees = when (currentOrientation) {
            Orientation.LAND_LEFT -> 90
            Orientation.LAND_RIGHT -> -90
            Orientation.PORTRAIT -> 0
        }.toFloat()

        binding?.overlayText?.rotation = when (currentOrientation) {
            Orientation.LAND_RIGHT -> -90f
            Orientation.LAND_LEFT -> 90f
            Orientation.PORTRAIT -> 0f
        }
        binding?.run {
            listOf(btnFlash, btnRecordVideo, btnSettings, btnToggleCamera, dotRecording, layoutRecordingTime)
                .forEach { v -> v.animate().rotation(degrees).start() }
        }
    }

    private fun debugToast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private var animationStarted = false
    private val dotBlinkAnimator = AlphaAnimation(0.0f, 1.0f).apply {
        duration = 1000
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
        interpolator = DecelerateInterpolator()
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    arrayOf()
                }

        fun allPermissionsGranted(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}