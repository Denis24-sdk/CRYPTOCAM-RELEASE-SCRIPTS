package com.tnibler.cryptocam.preference

import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraIdSettingsKey(private val noArgs: String = "") : DefaultFragmentKey() {
    override fun instantiateFragment() = CameraIdSettingsFragment()
}