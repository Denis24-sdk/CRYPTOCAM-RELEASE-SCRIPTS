package com.tnibler.system.video

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalCameraFilter
import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class VideoKey : DefaultFragmentKey() {
    @OptIn(ExperimentalCameraFilter::class)
    override fun instantiateFragment(): Fragment = VideoFragment()
}