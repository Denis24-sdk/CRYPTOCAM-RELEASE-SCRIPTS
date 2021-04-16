package com.tnibler.cryptocam.keys

import androidx.fragment.app.Fragment
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class KeyDetailKey(val recipient: KeyManager.X25519Recipient) : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = KeyDetailFragment()
}