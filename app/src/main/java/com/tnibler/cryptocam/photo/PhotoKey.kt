package com.tnibler.cryptocam.photo

import androidx.fragment.app.Fragment
import com.tnibler.cryptocam.VolumeKeyPressListener
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.simplestackextensions.servicesktx.rebind
import kotlinx.parcelize.Parcelize

@Parcelize
class PhotoKey : DefaultFragmentKey(), DefaultServiceProvider.HasServices {
    override fun instantiateFragment(): Fragment = PhotoFragment()

    override fun getScopeTag(): String = fragmentTag

    override fun bindServices(serviceBinder: ServiceBinder) {
        with(serviceBinder) {
            add(PhotoViewModel())
            rebind<VolumeKeyPressListener>(lookup<PhotoViewModel>())
        }
    }
}