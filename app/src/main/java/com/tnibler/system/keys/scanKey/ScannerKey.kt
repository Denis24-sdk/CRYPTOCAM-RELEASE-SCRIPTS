package com.tnibler.system.keys.scanKey

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class ScannerKey : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = ScannerFragment()

/*
    override fun getScopeTag(): String = fragmentTag

    override fun bindServices(serviceBinder: ServiceBinder) {
        with(serviceBinder) {
            rebind<OnKeyScannedListener>(lookup())
        }
    }
*/
}