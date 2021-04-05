package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.WebsiteInfoBinding
import com.tnibler.cryptocam.preference.SettingsFragment
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack

class WebsiteInfoFragment : KeyedFragment(R.layout.website_info) {
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
                backstack.goTo(InfoBackgroundRecordingKey())
            }
        }
    }
}