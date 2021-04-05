package com.tnibler.cryptocam

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class VideoKey : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = VideoFragment()
}