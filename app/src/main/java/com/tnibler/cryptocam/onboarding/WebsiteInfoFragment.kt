package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.WebsiteInfoBinding
import com.tnibler.cryptocam.preference.SettingsFragment

class WebsiteInfoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.website_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = WebsiteInfoBinding.bind(view)
        with(binding) {
            websiteLinkView.movementMethod = LinkMovementMethod.getInstance()
            websiteLinkView.setText(R.string.go_to_tutorial)
            websiteOkButton.setOnClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putBoolean(SettingsFragment.SHOWED_TUTORIAL_INFO, true)
                    commit()
                }
                (requireActivity() as MainActivity).nextOnboardingScreen(R.id.websiteInfoFragment)
            }
        }
    }
}