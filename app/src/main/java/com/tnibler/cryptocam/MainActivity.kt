package com.tnibler.cryptocam

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.ActivityMainBinding
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.keyList.KeysKey
import com.tnibler.cryptocam.keys.parseImportUri
import com.tnibler.cryptocam.onboarding.InfoBackgroundRecordingKey
import com.tnibler.cryptocam.onboarding.PickKeyKey
import com.tnibler.cryptocam.onboarding.PickOutputDirKey
import com.tnibler.cryptocam.onboarding.WebsiteInfoKey
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.video.VideoKey
import com.zhuinden.simplestack.SimpleStateChanger
import com.zhuinden.simplestack.StateChange
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentKey
import com.zhuinden.simplestackextensions.fragments.DefaultFragmentStateChanger
import com.zhuinden.simplestackextensions.navigatorktx.backstack
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.get
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@SuppressLint("RestrictedApi")
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

    private fun regularStart() {
        val keys = runBlocking { keyManager.availableKeys.first() }
        val shouldShowTutorialInfo =
            !sharedPreferences.getBoolean(SettingsFragment.SHOWED_TUTORIAL_INFO, false)
        val shouldShowBackgroundRecordingInfo =
            !sharedPreferences.getBoolean(SettingsFragment.SHOWED_BACKGROUND_RECORDING_INFO, false)
        val initialKey = when {
            shouldShowTutorialInfo -> WebsiteInfoKey()
            shouldShowBackgroundRecordingInfo -> InfoBackgroundRecordingKey()
            keys.isEmpty() -> PickKeyKey()
            !outputDirExists() -> PickOutputDirKey()
            else -> VideoKey()
        }
        val initialHistory = listOf(initialKey)
        Navigator.configure()
            .setGlobalServices((application as App).globalServices)
            .setScopedServices(DefaultServiceProvider())
            .setStateChanger(SimpleStateChanger(this@MainActivity))
            .install(this@MainActivity, findViewById(R.id.container), initialHistory)
        isNavigationSetUp = true
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
        Log.d(TAG, "action $action, data $data")
        if (data != null && action == Intent.ACTION_VIEW && data.scheme == "cryptocam" && data.host == "import_key") {
            Log.d(TAG, "import key deep link")
            // import key from deep link qr code
            val recipient = parseImportUri(data.toString())
            if (recipient == null) {
                Log.d(TAG, "failed to parse import key uri")
                regularStart()
                return
            }
            val firstKey = if (outputDirExists()) {
                VideoKey()
            } else {
                PickOutputDirKey()
            }
            val initialHistory = listOf(firstKey, KeysKey(recipient))
            Log.d(TAG, "initial history: $initialHistory")
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
            regularStart()
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            Log.d(TAG, "Volume key pressed")
            if (backstack.canFindService(VolumeKeyPressListener::class.java.name)) {
                Log.d(TAG, "Can find VolumeKeyPressListener")
                val listener: VolumeKeyPressListener = backstack.lookup()
                listener.onVolumeKeyDown()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun nextOnboardingScreen(currentDestination: DefaultFragmentKey) {
        Log.d(TAG, "nextOnboardingScreen(${currentDestination.javaClass.simpleName})")
        when (currentDestination) {
            is PickKeyKey -> {
                if (!outputDirExists()) {
                    // remove PickKey from history
                    val history = backstack.getHistory<DefaultFragmentKey>()
                    backstack.setHistory(history.drop(1) + PickOutputDirKey(), StateChange.FORWARD)
                } else {
                    backstack.setHistory(
                        listOf<DefaultFragmentKey>(VideoKey()),
                        StateChange.FORWARD
                    )
                }
            }
            is PickOutputDirKey -> {
                backstack.setHistory(listOf<DefaultFragmentKey>(VideoKey()), StateChange.FORWARD)
            }
            is InfoBackgroundRecordingKey -> {
                when {
                    keyManager.availableKeys.value.isEmpty() -> backstack.goTo(PickKeyKey())
                    !outputDirExists() -> backstack.goTo(PickOutputDirKey())
                    else -> backstack.setHistory(listOf(VideoKey()), StateChange.FORWARD)
                }
            }
        }
    }

    private fun outputDirExists(): Boolean {
        try {
            val savedUri = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null)
                ?: return false
            val df = DocumentFile.fromTreeUri(this, Uri.parse(savedUri)) ?: return false
            return df.exists()
        } catch (e: Exception) {
            return false
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
    }
}
