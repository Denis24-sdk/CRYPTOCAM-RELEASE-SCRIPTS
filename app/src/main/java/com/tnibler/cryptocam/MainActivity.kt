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
import androidx.camera.core.ExperimentalCameraFilter
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.databinding.ActivityMainBinding
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.keyList.KeysKey
import com.tnibler.cryptocam.keys.parseImportUri
import com.tnibler.cryptocam.onboarding.PickKeyKey
import com.tnibler.cryptocam.onboarding.PickOutputDirKey
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.preference.SettingsKey
import com.tnibler.cryptocam.video.VideoKey
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

    // [ИСПРАВЛЕНО] Логика онбординга изменена
    private fun regularStart(initialKeyOverride: DefaultFragmentKey? = null) {
        val keys = runBlocking { keyManager.availableKeys.first() }
        val outputDirSet = !sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null).isNullOrEmpty()

        val initialKey = initialKeyOverride ?: when {
            keys.isEmpty() -> PickKeyKey()
            !outputDirSet -> PickOutputDirKey() // Проверяем только то, что настройка была сохранена
            else -> KeysKey() // Если все настроено, можно показать экран ключей
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
            // [ИЗМЕНЕНО] Упрощенная проверка
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
            // Если это не специальная команда, закрываем Activity, чтобы не было UI
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

    // Эта функция теперь не используется для определения онбординга, но может быть полезна в других местах.
    private fun outputDirExists(): Boolean {
        return try {
            val savedUri = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_DIRECTORY, null) ?: return false
            val df = DocumentFile.fromTreeUri(this, Uri.parse(savedUri)) ?: return false
            df.exists() && df.canWrite() // Добавлена проверка на запись
        } catch (e: Exception) {
            false
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
    }
}