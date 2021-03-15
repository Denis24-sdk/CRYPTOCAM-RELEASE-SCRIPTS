package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.InfoBackgroundRecordingBinding
import com.tnibler.cryptocam.preference.SettingsFragment

class InfoBackgroundRecordingFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.info_background_recording, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = InfoBackgroundRecordingBinding.bind(view)
        with(binding) {
            backgroundRecordingOkButton.setOnClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean(SettingsFragment.SHOWED_BACKGROUND_RECORDING_INFO, true)
                    commit()
                }
                (requireActivity() as MainActivity).nextOnboardingScreen(R.id.infoBackgroundRecordingFragment)
            }
        }
    }
}