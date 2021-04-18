package com.tnibler.cryptocam.keys.keyList

import androidx.fragment.app.Fragment
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.scanKey.OnKeyScannedListener
import com.tnibler.cryptocam.onboarding.PickKeyViewModel
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.simplestackextensions.servicesktx.rebind
import kotlinx.parcelize.Parcelize

@Parcelize
class KeysKey(val importRecipient: KeyManager.X25519Recipient? = null) : DefaultFragmentKey(),
    DefaultServiceProvider.HasServices {
    override fun instantiateFragment(): Fragment = KeysFragment()

    override fun getScopeTag(): String = fragmentTag

    override fun bindServices(serviceBinder: ServiceBinder) {
        with(serviceBinder) {
            add(PickKeyViewModel())
            rebind<OnKeyScannedListener>(lookup<PickKeyViewModel>())
        }
    }
}