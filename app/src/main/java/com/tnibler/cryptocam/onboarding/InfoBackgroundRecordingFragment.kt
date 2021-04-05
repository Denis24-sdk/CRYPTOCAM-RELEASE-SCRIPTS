package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.InfoBackgroundRecordingBinding
import com.tnibler.cryptocam.preference.SettingsFragment
import com.zhuinden.simplestackextensions.fragments.KeyedFragment

class InfoBackgroundRecordingFragment : KeyedFragment(R.layout.info_background_recording) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = InfoBackgroundRecordingBinding.bind(view)
        with(binding) {
            backgroundRecordingOkButton.setOnClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean(SettingsFragment.SHOWED_BACKGROUND_RECORDING_INFO, true)
                    commit()
                }
                (requireActivity() as MainActivity).nextOnboardingScreen(getKey())
            }
        }
    }
}