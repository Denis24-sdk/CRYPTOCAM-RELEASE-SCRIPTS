package com.tnibler.system.video

import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraDebugKey(private val noArgs: String = "") : DefaultFragmentKey() {
    override fun instantiateFragment() = CameraDebugFragment()
}