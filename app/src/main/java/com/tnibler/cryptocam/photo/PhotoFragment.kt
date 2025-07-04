package com.tnibler.cryptocam.photo


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
import android.view.animation.Animation.AnimationListener
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.Orientation
import com.tnibler.cryptocam.OutputFileManager
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.SelectedCamera
import com.tnibler.cryptocam.video.VideoKey
import com.tnibler.cryptocam.databinding.PhotoScreenBinding
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.photo.photoviewer.PhotoViewerKey
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.preference.SettingsKey
import com.tnibler.cryptocam.util.ByteBufferInputStream
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import java.io.ByteArrayOutputStream

class PhotoFragment : KeyedFragment(R.layout.photo_screen) {
    private val TAG = javaClass.simpleName
    private var imageCapture: ImageCapture? = null
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val keyManager: KeyManager by lazy { lookup() }
    private val outputFileManager: OutputFileManager by lazy { OutputFileManager(
        outputLocation = Uri.parse(sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)),
        recipients = keyManager.selectedRecipients.value,
        context = requireContext(),
        contentResolver = requireContext().contentResolver,
        sharedPreferences = sharedPreferences
    ) }
    private var surfaceRotation = Surface.ROTATION_0
    private val orientationEventListener by lazy { buildOrientationEventListener() }
    private var lastHandledOrientation: Orientation? = null
    private var camera: Camera? = null
    private val focusDrawable: Drawable by lazy { ContextCompat.getDrawable(requireContext(),
        R.drawable.ic_focus
    )!! }
    private var focusCircleView: View? = null
    private val vibrator by lazy { ContextCompat.getSystemService(requireContext(), Vibrator::class.java)!! }
    private val viewModel: PhotoViewModel by lazy { lookup() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var binding: PhotoScreenBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = PhotoScreenBinding.bind(view)
        with (binding) {
            this@PhotoFragment.binding = binding
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                this@PhotoFragment.cameraProvider = cameraProvider
                setUpCamera(cameraProvider, binding)
            }, ContextCompat.getMainExecutor(requireContext()))

            lastPhotoBtn.setOnClickListener {
                backstack.goTo(PhotoViewerKey())
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.lastPhoto.collect {
                        if (it === null) {
                            binding.lastPhotoBtn.setImageResource(android.R.color.transparent)
                            binding.lastPhotoBtn.visibility = View.GONE
                        } else {
                            binding.lastPhotoBtn.setImageBitmap(it)
                            binding.lastPhotoBtn.visibility = View.VISIBLE
                        }
                    }
                }
            }

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
                viewModel.flashMode.value = when(viewModel.flashMode.value) {
                    PhotoViewModel.FlashMode.AUTO -> PhotoViewModel.FlashMode.ON
                    PhotoViewModel.FlashMode.ON -> PhotoViewModel.FlashMode.OFF
                    PhotoViewModel.FlashMode.OFF -> PhotoViewModel.FlashMode.AUTO
                }
            }
            photoBtnToggleCamera.setOnClickListener {
                viewModel.selectedCamera = viewModel.selectedCamera.other()
                val cameraProvider = cameraProvider
                if (cameraProvider != null) {
                    setUpCamera(cameraProvider, binding)
                    photoBtnToggleCamera.setImageResource(when(viewModel.selectedCamera) {
                        SelectedCamera.FRONT -> R.drawable.ic_outline_camera_rear
                        SelectedCamera.BACK -> R.drawable.ic_outline_camera_front
                    })
                }
            }
            photoBtnToggleCamera.setImageResource(when(viewModel.selectedCamera) {
                SelectedCamera.FRONT -> R.drawable.ic_outline_camera_rear
                SelectedCamera.BACK -> R.drawable.ic_outline_camera_front
            })
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.flashMode.collect { flashMode ->
                        photoBtnFlash.setImageResource(when (flashMode) {
                            PhotoViewModel.FlashMode.AUTO -> R.drawable.ic_flash_auto
                            PhotoViewModel.FlashMode.ON -> R.drawable.ic_flash_on
                            PhotoViewModel.FlashMode.OFF -> R.drawable.ic_flash_off
                        })
                        imageCapture?.flashMode = mapFlashMode(viewModel.flashMode.value)
                    }
                }
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.volumeKeyPressed.collect {
                        Log.d(TAG, "volume key pressed")
                        takePhoto(binding)
                    }
                }
            }
            setupPhotoFeedback(binding)
            if (sharedPreferences.getBoolean(SettingsFragment.PREF_OVERLAY, false)) {
                lifecycleScope.launchWhenResumed {
                    while (true) {
                        photoOverlayText.visibility = when (photoOverlayText.visibility) {
                            View.VISIBLE -> View.INVISIBLE
                            else -> View.VISIBLE
                        }
                        delay(1000)
                    }
                }
            } else {
                photoOverlayText.visibility = View.GONE
            }
        }
    }

    private fun setUpCamera(cameraProvider: ProcessCameraProvider, binding: PhotoScreenBinding) {
        with(binding) {
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
                .setFlashMode(mapFlashMode(viewModel.flashMode.value))
                .build()
            val cameraSelector = when (viewModel.selectedCamera) {
                SelectedCamera.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                SelectedCamera.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            }
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this@PhotoFragment, cameraSelector, preview, imageCapture)
            this@PhotoFragment.camera = camera
            setUpZoomAndFocus(camera, binding)
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

    private val photoFeedbackAnimation = AlphaAnimation(1.0f, 0.0f).apply {
        duration = 500
        fillAfter = true
        interpolator = DecelerateInterpolator()
    }

    private fun setupPhotoFeedback(binding: PhotoScreenBinding) {
        photoFeedbackAnimation.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                binding.photoFeedbackView.visibility = View.INVISIBLE
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.isTakingPhoto.filter { it }.collect {
                    binding.btnTakePhoto.isEnabled = false
                    binding.btnTakePhoto.alpha = 0.3f
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.isTakingPhoto
                    .drop(1) // ignore the initial value set in viewmodel
                    .filter { !it }.collect {
                        binding.photoFeedbackView.visibility = View.VISIBLE
                        binding.photoFeedbackView.startAnimation(photoFeedbackAnimation)

                        if (sharedPreferences.getBoolean(SettingsFragment.PREF_VIBRATE_ON_PHOTO, true)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(20, 80))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(20)
                            }
                        }

                        binding.btnTakePhoto.isEnabled = true
                        binding.btnTakePhoto.alpha = 1f
                    }
            }
        }
    }

    private fun takePhoto(binding: PhotoScreenBinding) {
        Log.d(TAG, "takePhoto()")
        if (viewModel.isTakingPhoto.value) {
            Log.d(TAG, "Already taking a photo in takePhoto")
            return
        }

        val imageCapture = imageCapture
        if (imageCapture == null) {
            Log.w(TAG, "ImageCapture UseCase is null in takePhoto")
            return
        }

        val callback = object : ImageCapture.OnImageCapturedCallback() {
            @ExperimentalGetImage
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                viewModel.isTakingPhoto.value = false
                val imageFile = outputFileManager.newImageFile()

                // TODO do this asynchronously
                val image = imageProxy.image ?: return
                if (image.format != ImageFormat.JPEG) {
                    throw IllegalArgumentException("Image format ${image.format} not supported")
                }
                val removeExif =
                    sharedPreferences.getBoolean(SettingsFragment.PREF_REMOVE_EXIF, false)
                val imageBuffer = image.planes[0].buffer

                val outputBytes: ByteArray
                if (removeExif) {
                    val exifRewriter = ExifRewriter()
                    val bufferInput = ByteBufferInputStream(imageBuffer)
                    val out = ByteArrayOutputStream(imageBuffer.remaining())
                    exifRewriter.removeExifMetadata(bufferInput, out)
                    outputBytes = out.toByteArray();
                }
                else {
                    val buf = ByteArray(imageBuffer.remaining())
                    imageBuffer.get(buf)
                    outputBytes = buf
                }

                imageFile.write(outputBytes)
                val bitmap = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
                viewModel.lastPhoto.value = bitmap

                imageFile.close()
                imageProxy.close()
                return
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.isTakingPhoto.value = false
                Toast.makeText(requireContext(), "$exception", Toast.LENGTH_SHORT).show()
            }
        }
        imageCapture.flashMode = mapFlashMode(viewModel.flashMode.value)
        imageCapture.targetRotation = surfaceRotation
        // we can not use any of the ImageCapture methods that write to files
        // or to a provided OutputStream like this one
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:camera/camera-core/src/main/java/androidx/camera/core/ImageCapture.java;drc=b61f0e09fd1f9c20f1575e78d4a2a8a15fe5c825;l=811
        // In https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:camera/camera-core/src/main/java/androidx/camera/core/ImageSaver.java;l=85;drc=ded69ff18456e1501b418d281c562bc4bb215937
        // the image is written to a temporary file on disk which must never happen
        viewModel.isTakingPhoto.value = true;
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), callback)
    }

    private fun mapFlashMode(mode: PhotoViewModel.FlashMode): Int {
        return when (mode) {
            PhotoViewModel.FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            PhotoViewModel.FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            PhotoViewModel.FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
        camera = null
        cameraProvider = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun rotateUiTo(currentOrientation: Orientation) {
        val degrees = when (currentOrientation) {
            Orientation.LAND_LEFT -> 90.also { Log.d(TAG, "land left") }
            Orientation.LAND_RIGHT -> (-90).also { Log.d(TAG, "land right") }
            Orientation.PORTRAIT -> 0.also { Log.d(TAG, "portrait") }
        }
        binding?.photoOverlayText?.rotation = when (currentOrientation) {
            Orientation.LAND_RIGHT -> -90f
            Orientation.LAND_LEFT -> 90f
            Orientation.PORTRAIT -> 0f
        }
        binding?.run {
            listOf(photoBtnFlash, photoBtnToggleCamera, photoBtnSettings, photoBtnFlash, photoBtnVideo)
                .forEach { v ->
                    v.animate().rotation(degrees.toFloat()).start()
                }
        }
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
                    in 75..134 -> Orientation.LAND_RIGHT
                    in 224..289 -> Orientation.LAND_LEFT
                    else -> Orientation.PORTRAIT
                }
                if (currentOrientation != lastHandledOrientation) {
                    rotateUiTo(currentOrientation)
                }
                surfaceRotation = when (currentOrientation) {
                    Orientation.PORTRAIT -> Surface.ROTATION_0
                    Orientation.LAND_RIGHT -> Surface.ROTATION_270
                    Orientation.LAND_LEFT -> Surface.ROTATION_90
                }
                lastHandledOrientation = currentOrientation
            }
        }
    }

    companion object {
        private const val KEY_FLASH_MODE = "flashMode"
    }
}