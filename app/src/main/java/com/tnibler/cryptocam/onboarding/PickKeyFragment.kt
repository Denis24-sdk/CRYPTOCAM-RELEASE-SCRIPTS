package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.databinding.PickKeyBinding

class PickKeyFragment : Fragment() {
    lateinit var binding: PickKeyBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = PickKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
        }
        binding.pickKeyButton.setOnClickListener {
            (requireActivity() as MainActivity).openPgpKeyManager.chooseKey(this) { ok, keyIds ->
                if (!ok) {
                    Toast.makeText(requireContext(), getString(R.string.pick_key_something_went_wrong), Toast.LENGTH_LONG).show()
                }
                else {
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                        putStringSet(
                            SettingsFragment.PREF_OPENPGP_KEYIDS,
                            keyIds.map { it.toString() }.toSet()
                        )
                    }
                    (requireActivity() as MainActivity).nextOnboardingScreen(R.id.pickKeyFragment)
                }
            }
        }
    }
}