package com.tnibler.system

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalCameraFilter
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.tnibler.system.keys.KeyManager
import com.tnibler.system.keys.keyList.KeysKey
import com.tnibler.system.keys.parseImportUri
import com.tnibler.system.onboarding.PickKeyKey
import com.tnibler.system.onboarding.PickOutputDirKey
import com.tnibler.system.preference.SettingsFragment
import com.tnibler.system.preference.SettingsKey
import com.tnibler.system.video.VideoKey
import com.zhuinden.simplestack.SimpleStateChanger
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentStateChanger
import com.zhuinden.simplestackextensions.navigatorktx.backstack
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.zhuinden.simplestackextensions.servicesktx.lookup
import androidx.core.net.toUri
import com.tnibler.system.R
import com.tnibler.system.databinding.ActivityMainBinding

@SuppressLint("RestrictedApi")
@ExperimentalCameraFilter
class MainActivity : AppCompatActivity(), SimpleStateChanger.NavigationHandler {
    private val TAG = javaClass.simpleName
    private lateinit var binding: ActivityMainBinding
    private lateinit var fragmentStateChanger: DefaultFragmentStateChanger
    private lateinit var sharedPreferences: SharedPreferences
    private var isNavigationSetUp = false
    private val keyManager by lazy { (application as App).globalServices.get<KeyManager>() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        fragmentStateChanger = DefaultFragmentStateChanger(supportFragmentManager, R.id.container)
        handleIntent(intent)
    }

    //  Логика онбординга
    private fun regularStart(initialKeyOverride: DefaultFragmentKey? = null) {
        val keys = runBlocking { keyManager.availableKeys.first() }
        val outputDirSet = !sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null).isNullOrEmpty()

        val initialKey = initialKeyOverride ?: when {
            keys.isEmpty() -> PickKeyKey()
            !outputDirSet -> PickOutputDirKey()
            else -> KeysKey()
        }

        val initialHistory = listOf(initialKey)

        if (!isNavigationSetUp) {
            Navigator.configure()
                .setGlobalServices((application as App).globalServices)
                .setScopedServices(DefaultServiceProvider())
                .setStateChanger(SimpleStateChanger(this@MainActivity))
                .install(this@MainActivity, findViewById(R.id.container), initialHistory)
            isNavigationSetUp = true
        } else {
            backstack.setHistory(initialHistory, StateChange.REPLACE)
        }
    }

    override fun onNavigationEvent(stateChange: StateChange) {
        fragmentStateChanger.handleStateChange(stateChange)
    }

    override fun onBackPressed() {
        if (!Navigator.onBackPressed(this)) {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent()")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val data = intent?.data
        Log.d(TAG, "handleIntent() with action: $action, data: $data")

        when (action) {
            ApiConstants.ACTION_OPEN_SETTINGS -> {
                Log.d(TAG, "Settings screen action received")
                regularStart(SettingsKey())
                return
            }
            ApiConstants.ACTION_OPEN_KEYS -> {
                Log.d(TAG, "Keys screen action received")
                regularStart(KeysKey())
                return
            }
            ApiConstants.ACTION_OPEN_OUTPUT_PICKER,
            ApiConstants.ACTION_FORCE_OUTPUT_PICKER -> {
                Log.d(TAG, "Output picker screen action received")
                regularStart(PickOutputDirKey())
                return
            }
            ApiConstants.ACTION_CHECK_ENCRYPTION_KEY -> {
                Log.d(TAG, "Check encryption key action received")
                val keys = runBlocking { keyManager.availableKeys.first() }
                if (keys.isEmpty()) {
                    regularStart(PickKeyKey())
                } else {
                    finish()
                }
                return
            }
        }


        if (data != null && action == Intent.ACTION_VIEW && data.scheme == "cryptocam" && data.host == "import_key") {
            Log.d(TAG, "import key deep link")
            val recipient = parseImportUri(data.toString())
            if (recipient == null) {
                Log.d(TAG, "failed to parse import key uri")
                regularStart()
                return
            }
            val outputDirIsSet = !sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null).isNullOrEmpty()
            val firstKey = if (outputDirIsSet) VideoKey() else PickOutputDirKey()
            val initialHistory = listOf(firstKey, KeysKey(recipient))
            if (!isNavigationSetUp) {
                Navigator.configure()
                    .setGlobalServices((application as App).globalServices)
                    .setScopedServices(DefaultServiceProvider())
                    .setStateChanger(SimpleStateChanger(this@MainActivity))
                    .install(this@MainActivity, findViewById(R.id.container), initialHistory)
                isNavigationSetUp = true
            } else {
                backstack.setHistory(initialHistory, StateChange.REPLACE)
            }
        } else {
            if (isTaskRoot) {
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (backstack.canFindService(VolumeKeyPressListener::class.java.name)) {
                val listener: VolumeKeyPressListener = backstack.lookup()
                listener.onVolumeKeyDown()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun nextOnboardingScreen(currentDestination: DefaultFragmentKey) {
        Log.d(TAG, "nextOnboardingScreen(${currentDestination.javaClass.simpleName})")
        val outputDirIsSet = !sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null).isNullOrEmpty()
        when (currentDestination) {
            is PickKeyKey -> {
                if (!outputDirIsSet) {
                    backstack.goTo(PickOutputDirKey())
                } else {
                    finish()
                }
            }
            is PickOutputDirKey -> {
                finish()
            }
        }
    }

    private fun outputDirExists(): Boolean {
        return try {
            val savedUri = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null) ?: return false
            val df = DocumentFile.fromTreeUri(this, savedUri.toUri()) ?: return false
            df.exists() && df.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
    }
}