package com.tnibler.cryptocam.preference

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class SettingsKey : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = SettingsFragment()
}