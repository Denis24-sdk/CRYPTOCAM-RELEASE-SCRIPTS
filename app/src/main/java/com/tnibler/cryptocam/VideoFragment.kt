package com.tnibler.cryptocam

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.VideoScreenBinding
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.preference.SettingsKey
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

class VideoFragment : Fragment() {
    private val TAG = javaClass.simpleName
    private var preview: Preview? = null
    private var binding: VideoScreenBinding? = null
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(
            requireContext()
        )
    }
    private val keyManager: KeyManager by lazy { lookup() }

    var service: RecordingService? = null
        private set
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            this@VideoFragment.service =
                (service as RecordingService.RecordingServiceBinder).service
            onServiceBound(this@VideoFragment.service!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            this@VideoFragment.service = null
        }
    }

    private var currentCamera: RecordingService.SelectedCamera? = null

    private fun onServiceBound(service: RecordingService) {
        Log.d(TAG, "onServiceBound")
        service.background()
        onStateChanged(service.state.value)
        service.init(keyManager.selectedRecipients.value)
        lifecycleScope.launchWhenResumed {
            service.state.collect { state ->
                onStateChanged(state)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.video_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = VideoScreenBinding.bind(view)
        val binding = binding!!
        if (!allPermissionsGranted(requireContext())) {
            val requestPermissions = ActivityResultContracts.RequestMultiplePermissions()
            registerForActivityResult(requestPermissions) { result ->
                if (allPermissionsGranted(requireContext())) {
                    setupUi(binding)
                    val service = service
                    if (service == null) {
                        debugToast("service is null in onViewCreated onActivityResult, all permissions granted")
                        return@registerForActivityResult
                    }
                    service.initUseCases() // use cases are already initialized but references like AudioRecorder are null inside VideoStreamCapture without these permissions
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
        setupUi(binding)
    }

    private fun setupUi(binding: VideoScreenBinding) {
        binding.run {
            btnFlash.setOnClickListener {
                service?.toggleFlash()
            }
            btnToggleCamera.setOnClickListener {
                service?.toggleCamera()
            }
            btnRecordVideo.setOnClickListener { service?.toggleRecording() }
            btnSettings.setOnClickListener {
                backstack.goTo(SettingsKey())
            }
            if (sharedPreferences.getBoolean(SettingsFragment.PREF_OVERLAY, false)) {
                lifecycleScope.launchWhenResumed {
                    while (true) {
                        overlayText.visibility = when (overlayText.visibility) {
                            View.VISIBLE -> View.INVISIBLE
                            else -> View.VISIBLE
                        }
                        delay(1000)
                    }
                }
            } else {
                overlayText.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // connect to service
        val service = service
        if (service == null) {
            Log.d(TAG, "Starting Recording service")
            val recordingServiceIntent = Intent(requireContext(), RecordingService::class.java)
            requireContext().startService(recordingServiceIntent)
            requireContext().bindService(
                recordingServiceIntent,
                connection,
                Context.BIND_ABOVE_CLIENT
            )
        } else {
            onServiceBound(service)
        }
    }

    private fun onStateChanged(state: RecordingService.State) {
        binding?.run {
            if (state.selectedCamera != currentCamera || preview == null) {
                when (state) {
                    is RecordingService.State.ReadyToRecord -> {
                        setUpPreview(state.resolution, state.surfaceRotation, this)
                        currentCamera = state.selectedCamera
                    }
                    is RecordingService.State.Recording -> {
                        setUpPreview(state.resolution, state.surfaceRotation, this)
                        currentCamera = state.selectedCamera
                    }
                }
            }
            btnFlash.setImageResource(
                when (state.flashOn) {
                    true -> R.drawable.ic_flash_on
                    false -> R.drawable.ic_flash_off
                }
            )
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
                    if (d.toHours() > 0) {
                        recordingTime.text = String.format(
                            "%02d:%02d:%02d",
                            d.toHours(),
                            d.toMinutes() % 60,
                            d.seconds % 60
                        )
                    } else {
                        recordingTime.text =
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
                            RecordingService.SelectedCamera.BACK -> R.drawable.ic_outline_camera_rear
                            RecordingService.SelectedCamera.FRONT -> R.drawable.ic_outline_camera_front
                        }
                    )
                    btnRecordVideo.visibility = View.VISIBLE
                    btnRecordVideo.setImageResource(R.drawable.ic_record_video)
                }
            }
        }
    }

    private fun setUpPreview(resolution: Size, surfaceRotation: Int, binding: VideoScreenBinding) =
        binding.run {
            Log.d(
                TAG,
                "Setting up preview. resolution=$resolution, surfaceRotation=$surfaceRotation"
            )
            val service = service
            if (service == null) {
                debugToast("service is null in setUpPreview")
                return
            }
            preview?.let { service.unbindUseCase(it) }
            val display =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    requireActivity().display!!
                } else {
                    requireActivity().windowManager.defaultDisplay
                }
            val preview = Preview.Builder()
                .setTargetRotation(display.rotation)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            viewFinder.scaleType = PreviewView.ScaleType.FIT_START
            this@VideoFragment.preview = preview
            service.bindUseCase(preview)
            preview.setSurfaceProvider(viewFinder.surfaceProvider)

            val scaleGestureDetector = ScaleGestureDetector(requireContext(), object :
                ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    service.scaleZoomRatio(detector.scaleFactor)
                    return true
                }
            })

            val gestureDetector = GestureDetector(requireContext(), object :
                GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    val factory = binding.viewFinder.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    service.startFocusAndMetering(action)
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

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        val service = service
        preview?.let { service?.unbindUseCase(it) }
        preview = null
        if (service == null) {
            return
        }
        if (service.state.value is RecordingService.State.Recording) {
            service.foreground()
        } else {
            service.stopSelf()
            this.service = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        binding = null
    }

    private fun debugToast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private var animationStarted = false
    private val dotBlinkAnimator = AlphaAnimation(0.0f, 1.0f).apply {
        duration = 1000
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
        interpolator = DecelerateInterpolator()
    }

    companion object {
        val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        fun allPermissionsGranted(context: Context) = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                context, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
