package com.tnibler.cryptocam.preference

import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.video.NotificationIcon
import com.tnibler.cryptocam.video.NotificationStyle

class CustomNotificationSettingsFragment : PreferenceFragmentCompat() {
    private val TAG = javaClass.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        rootKey?.let { screen.key = rootKey }

        val customizeNotificationPreference = CheckBoxPreference(context).apply {
            setDefaultValue(false)
            setTitle(R.string.enable_custom_notification)
            setSummary(R.string.enable_custom_notification_summary)
            key = SettingsFragment.PREF_CUSTOMIZE_NOTIFICATION
        }
        screen.addPreference(customizeNotificationPreference)

        val appNamePreference = EditTextPreference(context).apply {
            setDefaultValue("Cryptocam")
            setTitle(R.string.notification_app_name)
            key = SettingsFragment.PREF_CUSTOM_NOTIFICATION_APP_NAME
            setOnPreferenceChangeListener { preference, newValue ->
                summary = newValue.toString()
                true
            }
        }
        screen.addPreference(appNamePreference)
        appNamePreference.summary = appNamePreference.text

        val titlePreference = EditTextPreference(context).apply {
            setDefaultValue("Recording in the background")
            setTitle(R.string.pref_notification_title)
            key = SettingsFragment.PREF_CUSTOM_NOTIFICATION_TITLE
            setOnPreferenceChangeListener { preference, newValue ->
                summary = newValue as String
                true
            }
        }
        screen.addPreference(titlePreference)
        titlePreference.summary = titlePreference.text

        val textPreference = EditTextPreference(context).apply {
            setDefaultValue("Recording in the background")
            setTitle(R.string.pref_notification_text)
            key = SettingsFragment.PREF_CUSTOM_NOTIFICATION_TEXT
            setOnPreferenceChangeListener { preference, newValue ->
                summary = newValue as String
                true
            }
        }
        screen.addPreference(textPreference)
        textPreference.summary = textPreference.text

        val iconPreference = ListPreference(context).apply {
            setDefaultValue(NotificationIcon.Cloud.entryValue)
            setTitle(R.string.pref_notification_icon)
            entries = iconEntries
            entryValues = iconValues
            key = SettingsFragment.PREF_CUSTOM_NOTIFICATION_ICON
            setOnPreferenceChangeListener { preference, newValue ->
                setIcon(iconDrawable(newValue as String))
                true
            }
        }
        screen.addPreference(iconPreference)
        iconPreference.setIcon(iconDrawable(iconPreference.value))

        val stylePreference = ListPreference(context).apply {
            setDefaultValue("google_p2_10")
            setTitle(R.string.pref_notification_style)
            entries = styleEntries
            entryValues = styleValues
            key = SettingsFragment.PREF_CUSTOM_NOTIFICATION_STYLE
            setOnPreferenceChangeListener { preference, newValue ->
                val index = styleValues.indexOf(newValue)
                if (index >= 0) {
                    summary = styleEntries[index]
                }
                else {
                    Log.w(TAG, "Garbage value '$newValue' saved for ${SettingsFragment.PREF_CUSTOM_NOTIFICATION_STYLE}")
                }
                true
            }
        }
        screen.addPreference(stylePreference)
        val index = styleValues.indexOf(stylePreference.value)
        if (index >= 0) {
            stylePreference.summary = styleEntries[index]
        }
        else {
            Log.w(TAG, "Garbage value '${stylePreference.value}' loaded for ${SettingsFragment.PREF_CUSTOM_NOTIFICATION_STYLE}")
        }

        val allPrefs = arrayOf(appNamePreference, titlePreference, textPreference, iconPreference)
        customizeNotificationPreference.setOnPreferenceChangeListener { _, useCustomNotification ->
            allPrefs.forEach { preference ->
                preference.isEnabled = useCustomNotification as Boolean
            }
            true
        }
        allPrefs.forEach { preference ->
            preference.isEnabled = customizeNotificationPreference.isChecked
        }

        preferenceScreen = screen
    }

    companion object {
        val iconValues = NotificationIcon.values().map { it.entryValue }.toTypedArray()
        val iconEntries = NotificationIcon.values().map { it.entryName }.toTypedArray()

        val styleValues = NotificationStyle.values().map { it.entryValue }.toTypedArray()
        val styleEntries = NotificationStyle.values().map { it.entryName }.toTypedArray()

        @DrawableRes
        fun iconDrawable(iconName: String): Int =
            NotificationIcon.values().find { it.entryValue == iconName }?.drawable ?: R.drawable.notification_ic_none
    }
}
