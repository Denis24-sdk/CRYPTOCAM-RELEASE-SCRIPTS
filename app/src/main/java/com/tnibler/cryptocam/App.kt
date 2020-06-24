package com.tnibler.cryptocam

import android.app.Application
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection

class App : Application() {
    var openPgpServiceConnection: OpenPgpServiceConnection? = null
    var openPgpApi: OpenPgpApi? = null
    override fun onCreate() {
        super.onCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
        openPgpServiceConnection?.unbindFromService()
    }
}