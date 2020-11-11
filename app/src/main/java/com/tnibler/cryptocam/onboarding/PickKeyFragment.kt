package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.PickKeyBinding
import com.tnibler.cryptocam.preference.SettingsFragment

class PickKeyFragment : Fragment() {
    lateinit var binding: PickKeyBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = PickKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val chooseKeyActivityResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            chooseKeyResultListener?.invoke(result)
        }

    // gross, but we have to register all activity result listeners now so we can't
    private var chooseKeyResultListener: ((ActivityResult) -> Unit)? = null
    private fun setChooseKeyResultListener(listener: (ActivityResult) -> Unit) {
        chooseKeyResultListener = listener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenResumed {
        }
        binding.pickKeyButton.setOnClickListener {
            (requireActivity() as MainActivity).openPgpKeyManager.chooseKey(
                chooseKeyActivityResult,
                ::setChooseKeyResultListener
            ) { ok, keyIds ->
                if (!ok) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pick_key_something_went_wrong),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
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