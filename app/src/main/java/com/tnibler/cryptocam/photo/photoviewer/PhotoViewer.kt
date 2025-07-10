package com.tnibler.cryptocam.photo.photoviewer

import android.os.Bundle
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import androidx.core.math.MathUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.PhotoViewerBinding
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PhotoViewer : KeyedFragment(R.layout.photo_viewer), OnTouchListener {
    private val photoProvider: PhotoProvider by lazy { lookup() }
    private lateinit var scaleGestureDetector : ScaleGestureDetector
    private lateinit var gestureDetector : GestureDetector

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = PhotoViewerBinding.bind(view)
        val photoView = binding.photoView

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                photoProvider.photo.collect {
                    if (it === null) {
                        photoView.setImageResource(android.R.color.transparent)
                        backstack.goBack()
                    } else {
                        photoView.setImageBitmap(it)
                    }
                }
            }
        }

        var isScaling = false
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : OnScaleGestureListener {
            var lastFocusX: Float = 0f
            var lastFocusY: Float = 0f
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var newScale = photoView.scaleX * detector.scaleFactor
                newScale = MathUtils.clamp(newScale, 0.25f, 50f)
                val deltaScale = newScale / photoView.scaleX

                photoView.scaleX = newScale
                photoView.scaleY = newScale

                var newTransationX = detector.focusX + (photoView.translationX - detector.focusX) * deltaScale
                newTransationX += (detector.focusX - lastFocusX)
                var newTranslationY = detector.focusY + (photoView.translationY - detector.focusY) * deltaScale
                newTranslationY += (detector.focusY - lastFocusY)

                photoView.translationX = newTransationX
                photoView.translationY = newTranslationY

                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })
        ViewConfiguration.get(requireContext())


        gestureDetector = GestureDetector(context, object : OnGestureListener {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScaling)
                    return true

                photoView.translationX -= distanceX
                photoView.translationY -= distanceY
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
            override fun onShowPress(e: MotionEvent) = Unit
            override fun onSingleTapUp(e: MotionEvent): Boolean = true
            override fun onLongPress(e: MotionEvent) = Unit
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = true
        })

        binding.frameLayout.setOnTouchListener(this)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}