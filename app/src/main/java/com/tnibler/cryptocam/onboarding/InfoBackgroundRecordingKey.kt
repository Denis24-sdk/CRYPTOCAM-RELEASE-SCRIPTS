package com.tnibler.cryptocam.onboarding

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class InfoBackgroundRecordingKey : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = InfoBackgroundRecordingFragment()
}