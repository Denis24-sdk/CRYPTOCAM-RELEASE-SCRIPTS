package com.tnibler.cryptocam

import android.graphics.*
import android.graphics.drawable.Drawable
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.addRepeatingJob
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.PhotoScreenBinding
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.preference.SettingsKey
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.parcelize.Parcelize

class PhotoFragment : KeyedFragment(R.layout.photo_screen) {
    private val TAG = javaClass.simpleName
    private var imageCapture: ImageCapture? = null
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val keyManager: KeyManager by lazy { lookup() }
    private val outputFileManager: OutputFileManager by lazy { OutputFileManager(
        outputLocation = Uri.parse(sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)),
        recipients = keyManager.selectedRecipients.value,
        context = requireContext(),
        contentResolver = requireContext().contentResolver
    ) }
    private var surfaceRotation = Surface.ROTATION_0
    private val orientationEventListener by lazy { buildOrientationEventListener() }
    private var camera: Camera? = null
    private val focusDrawable: Drawable by lazy { ContextCompat.getDrawable(requireContext(), R.drawable.ic_focus)!! }
    private var focusCircleView: View? = null
    private var flashMode: MutableStateFlow<FlashMode> = MutableStateFlow(FlashMode.AUTO)
    private val vibrator by lazy { ContextCompat.getSystemService(requireContext(), Vibrator::class.java)!! }
    private val viewModel: PhotoViewModel by lazy { lookup() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(KEY_FLASH_MODE, flashMode.value)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = PhotoScreenBinding.bind(view)
        if (savedInstanceState != null) {
            flashMode.value = savedInstanceState.getSerializable(KEY_FLASH_MODE) as FlashMode? ?: FlashMode.AUTO
        }
        with (binding) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(viewFinder.display.rotation)
                    .build()
                    .apply {
                        setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this@PhotoFragment, cameraSelector, preview, imageCapture)
                this@PhotoFragment.camera = camera
                setUpZoomAndFocus(camera, binding)
            }, ContextCompat.getMainExecutor(requireContext()))
            photoBtnSettings.setOnClickListener {
                backstack.goTo(SettingsKey())
            }
            btnTakePhoto.setOnClickListener {
                takePhoto(binding)
            }
            photoBtnVideo.setOnClickListener {
                backstack.setHistory(listOf(VideoKey()), StateChange.REPLACE)
            }
            photoBtnFlash.setOnClickListener {
                flashMode.value = when(flashMode.value) {
                    FlashMode.AUTO -> FlashMode.ON
                    FlashMode.ON -> FlashMode.OFF
                    FlashMode.OFF -> FlashMode.AUTO
                }
            }
            viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
                flashMode.collect { flashMode ->
                    photoBtnFlash.setImageResource(when (flashMode) {
                        FlashMode.AUTO -> R.drawable.ic_flash_auto
                        FlashMode.ON -> R.drawable.ic_flash_on
                        FlashMode.OFF -> R.drawable.ic_flash_off
                    })
                }
            }
            viewLifecycleOwner.addRepeatingJob(Lifecycle.State.RESUMED) {
                viewModel.volumeKeyPressed.collect {
                    Log.d(TAG, "volume key pressed")
                    takePhoto(binding)
                }
            }
        }
    }

    private fun setUpZoomAndFocus(camera: Camera, binding: PhotoScreenBinding) {
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                Log.d(TAG, "zoomRatio=${detector.scaleFactor}")
                val zoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: return false
                camera.cameraControl.setZoomRatio(detector.scaleFactor * zoomRatio)
                return true
            }
        })

        val gestureDetector = GestureDetector(requireContext(), object :
            GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                Log.d(TAG, "onSingleTap")
                focusCircleView?.animation = null
                binding.viewFinder.removeView(focusCircleView)
                val circleView = ImageView(requireContext()).apply {
                    setImageDrawable(focusDrawable)
                    x = event.x - focusDrawable.intrinsicHeight / 2
                    y = event.y - focusDrawable.intrinsicHeight / 2
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                fade.reset()
                fade.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationEnd(animation: Animation?) {
                        focusCircleView = null
                        binding.viewFinder.removeView(circleView)
                    }

                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationStart(animation: Animation?) {}
                })
                circleView.startAnimation(fade)
                focusCircleView = circleView
                binding.viewFinder.addView(circleView)
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera.cameraControl.startFocusAndMetering(action)
                return true
            }
        })

        binding.viewFinder.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                Log.d(TAG, "tap")
                return@setOnTouchListener true
            }
            if (scaleGestureDetector.onTouchEvent(event)) {
                Log.d(TAG, "zoom")
                return@setOnTouchListener true
            }
            false
        }
    }

    private fun takePhoto(binding: PhotoScreenBinding) {
        Log.d(TAG, "takePhoto()")
        val imageCapture = imageCapture
        if (imageCapture == null) {
            Log.w(TAG, "ImageCapture UseCase is null in takePhoto")
            return
        }
        if (sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_ON_PHOTO, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, 80))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        }
        val imageFile = outputFileManager.newImageFile()
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            @ExperimentalGetImage
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val image = imageProxy.image ?: return
                if (image.format != ImageFormat.JPEG) {
                    throw IllegalArgumentException("Image format ${image.format} not supported")
                }
                val imageBuffer = image.planes[0].buffer
                val buf = ByteArray(imageBuffer.remaining())
                imageBuffer.get(buf)
                imageFile.write(buf)
                imageFile.close()
                imageProxy.close()
                binding.photoFeedbackView.visibility = View.GONE
                return
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(requireContext(), "$exception", Toast.LENGTH_SHORT).show()
            }
        }
        imageCapture.flashMode = when (flashMode.value) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture.targetRotation = surfaceRotation
        // we can not use any of the ImageCapture methods that write to files
        // or to a provided OutputStream like this one
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:camera/camera-core/src/main/java/androidx/camera/core/ImageCapture.java;drc=b61f0e09fd1f9c20f1575e78d4a2a8a15fe5c825;l=811
        // In https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:camera/camera-core/src/main/java/androidx/camera/core/ImageSaver.java;l=85;drc=ded69ff18456e1501b418d281c562bc4bb215937
        // the image is written to a temporary file on disk which must never happen
        binding.photoFeedbackView.visibility = View.VISIBLE
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), callback)
    }


    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
        camera = null
    }

    private val fade = AlphaAnimation(1.0f, 0.0f).apply {
        startOffset = 1000
        duration = 500
        interpolator = DecelerateInterpolator()
    }

    private fun buildOrientationEventListener(): OrientationEventListener {
        return object : OrientationEventListener(requireContext(), SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
//                Log.d(TAG, "onOrientationChanged: $orientation")
                val currentOrientation = when (orientation) {
                    in 75..134 -> RecordingService.Orientation.LAND_RIGHT
                    in 224..289 -> RecordingService.Orientation.LAND_LEFT
                    else -> RecordingService.Orientation.PORTRAIT
                }
                surfaceRotation = when (currentOrientation) {
                    RecordingService.Orientation.PORTRAIT -> Surface.ROTATION_0
                    RecordingService.Orientation.LAND_RIGHT -> Surface.ROTATION_270
                    RecordingService.Orientation.LAND_LEFT -> Surface.ROTATION_90
                }
            }
        }
    }

    companion object {
        private const val KEY_FLASH_MODE = "flashMode"
    }

    private enum class FlashMode {
        AUTO, ON, OFF
    }
}