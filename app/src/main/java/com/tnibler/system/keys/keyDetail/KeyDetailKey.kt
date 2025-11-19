package com.tnibler.system.keys.keyDetail

import androidx.fragment.app.Fragment
import com.tnibler.system.keys.KeyManager
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import kotlinx.parcelize.Parcelize

@Parcelize
class KeyDetailKey(val recipient: KeyManager.X25519Recipient) : DefaultFragmentKey() {
    override fun instantiateFragment(): Fragment = KeyDetailFragment()
}