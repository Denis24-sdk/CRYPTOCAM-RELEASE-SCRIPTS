package com.tnibler.cryptocam.photo.photoviewer

import androidx.fragment.app.Fragment
import com.tnibler.cryptocam.photo.PhotoViewModel
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.simplestackextensions.servicesktx.rebind
import kotlinx.parcelize.Parcelize

@Parcelize
class PhotoViewerKey : DefaultFragmentKey(), DefaultServiceProvider.HasServices {
    override fun instantiateFragment(): Fragment = PhotoViewer()

    override fun getScopeTag(): String = fragmentTag

    override fun bindServices(serviceBinder: ServiceBinder) {
        with(serviceBinder) {
            rebind<PhotoProvider>(lookup<PhotoViewModel>())
        }
    }
}