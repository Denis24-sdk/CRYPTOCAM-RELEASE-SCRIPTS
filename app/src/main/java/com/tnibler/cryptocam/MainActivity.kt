package com.tnibler.cryptocam

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.ActivityMainBinding
import com.tnibler.cryptocam.preference.SettingsFragment
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.InputStream

//TODO licenses for images
@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private val TAG = javaClass.simpleName
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    lateinit var openPgpServiceConnection: OpenPgpServiceConnection
    lateinit var openPgpApi: OpenPgpApi
    val openPgpKeyManager = OpenPgpManager()
    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        navController = Navigation.findNavController(this, R.id.navHostFragment)
        val app = (application as App)
        if (app.openPgpServiceConnection == null) {
            connectOpenPgp()
        }
        else {
            openPgpServiceConnection = app.openPgpServiceConnection!!
            openPgpApi = app.openPgpApi!!
            openPgpKeyManager.api = openPgpApi
            navController.popBackStack(R.id.checkOpenKeychainFragment, true)
            nextOnboardingScreen(R.id.checkOpenKeychainFragment)
        }
    }

    fun nextOnboardingScreen(@IdRes currentDestination: Int) {
        when (currentDestination) {
            R.id.checkOpenKeychainFragment -> {
                if (!keyExists()) {
                    navController.navigate(R.id.pickKeyFragment)
                }
                else if (!outputDirExists()) {
                    navController.navigate(R.id.pickOutputDirFragment)
                }
                else {
                    if (navController.currentDestination?.id != R.id.cameraFragment) {
                        navController.popBackStack(R.id.pickKeyFragment, true)
                        navController.navigate(R.id.cameraFragment)
                    }
                }
            }
            R.id.pickKeyFragment -> {
                if (!outputDirExists()) {
                    navController.navigate(R.id.pickOutputDirFragment)
                }
                else {
                    if (navController.currentDestination?.id != R.id.cameraFragment) {
                        navController.popBackStack(R.id.pickKeyFragment, true)
                        navController.navigate(R.id.cameraFragment)
                    }
                }
            }
            R.id.pickOutputDirFragment -> {
                if (navController.currentDestination?.id != R.id.cameraFragment) {
                    navController.popBackStack(R.id.pickKeyFragment, true)
                    navController.navigate(R.id.cameraFragment)
                }
            }
        }
    }

    private fun keyExists(): Boolean {
        try {
            val savedKey =
                sharedPreferences.getStringSet(SettingsFragment.PREF_OPENPGP_KEYIDS, setOf())
                    ?.map { it.toLong() } ?: return false
            if (savedKey.isEmpty()) {
                return false
            }
            return true
        }
        catch (e: Exception) {
            return false
        }
//        return openPgpKeyManager.checkKeyIdIsValid(savedKey)
    }

    private fun outputDirExists(): Boolean {
        try {
            val savedUri = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
                ?: return false
            val df = DocumentFile.fromTreeUri(this, Uri.parse(savedUri)) ?: return false
            df.listFiles()
        }
        catch (e: Exception) {
            return false
        }
        return true
    }

    fun connectOpenPgp() {
        val app = (application as App)
        val conn =
            OpenPgpServiceConnection(this, "org.sufficientlysecure.keychain", object : OpenPgpServiceConnection.OnBound {
                override fun onError(e: Exception?) {
//                    Toast.makeText(this@MainActivity, R.string.error_openpgp_connection, Toast.LENGTH_LONG).show()
                    navController.navigate(R.id.action_checkOpenKeychainFragment_to_installOpenKeychainFragment)
                }

                override fun onBound(service: IOpenPgpService2?) {
                    Log.d(TAG, "OpenPGP service bound.")
                    openPgpApi = OpenPgpApi(app, openPgpServiceConnection.service)
                    app.openPgpApi = openPgpApi
                    openPgpKeyManager.api = openPgpApi
                    val result = openPgpApi.executeApi(Intent().apply {
                        action = OpenPgpApi.ACTION_CHECK_PERMISSION
                    }, null as InputStream?, null)
                    when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                        OpenPgpApi.RESULT_CODE_SUCCESS -> {
                            openPgpKeyManager.status = OpenPgpManager.OpenPgpStatus.BOUND
                            navController.popBackStack(R.id.checkOpenKeychainFragment, true)
                            nextOnboardingScreen(R.id.checkOpenKeychainFragment)
                        }
                        OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                            val pi = result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT)
                            val intentSenderRequest = IntentSenderRequest.Builder(pi).build()
                            val interaction = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                                if (result.resultCode != Activity.RESULT_OK) {
                                    Toast.makeText(this@MainActivity, getString(R.string.error_openkeychain_permissions), Toast.LENGTH_LONG).show()
                                    finish()
                                }
                                navController.popBackStack(R.id.checkOpenKeychainFragment, true)
                                nextOnboardingScreen(R.id.checkOpenKeychainFragment)
                            }
                            try {
                                interaction.launch(intentSenderRequest)
                            }
                            catch (e: IntentSender.SendIntentException) {
                                Log.e(TAG, "SendIntentException", e)
                                Toast.makeText(this@MainActivity, "Error starting OpenKeychain intent.", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                        OpenPgpApi.RESULT_CODE_ERROR -> {
                            val error = result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)
                            Log.e(TAG, "error checking OpenPgp permissions: $error")
                            Toast.makeText(this@MainActivity, R.string.error_getting_openpgp_permission, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            })
        openPgpServiceConnection = conn
        app.openPgpServiceConnection = conn
        conn.bindToService()
    }

    override fun onDestroy() {
        super.onDestroy()
        openPgpKeyManager.status = OpenPgpManager.OpenPgpStatus.NOT_BOUND
    }

    companion object {
        private const val REQUEST_CODE_PERMISSION = 848
    }
}
