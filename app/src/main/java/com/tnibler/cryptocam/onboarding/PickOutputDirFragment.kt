package com.tnibler.cryptocam.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.preference.SettingsFragment
import com.tnibler.cryptocam.databinding.PickOutDirectoryBinding

class PickOutputDirFragment : Fragment() {
    lateinit var binding: PickOutDirectoryBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = PickOutDirectoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.run {
            val openDocumentTree = object : ActivityResultContracts.OpenDocumentTree() {
                override fun createIntent(context: Context, input: Uri?): Intent {
                    return super.createIntent(context, input)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
            }
            val pickOutputDir = registerForActivityResult(openDocumentTree) { uri ->
                uri ?: return@registerForActivityResult
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putString(SettingsFragment.PREF_OUTPUT_DIRECTORY, uri.toString())
                    commit()
                }
                (requireActivity() as MainActivity).nextOnboardingScreen(R.id.pickOutputDirFragment)
            }
            pickOutDirButton.setOnClickListener {
                pickOutputDir.launch(null)
            }
        }
    }
}