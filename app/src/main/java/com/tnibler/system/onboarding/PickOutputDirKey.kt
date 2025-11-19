package com.tnibler.system.onboarding

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class PickOutputDirKey : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = PickOutputDirFragment()
}