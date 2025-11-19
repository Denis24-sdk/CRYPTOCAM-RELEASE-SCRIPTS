package com.tnibler.system.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.tnibler.system.MainActivity
import com.tnibler.system.R
import com.tnibler.system.databinding.PickOutDirectoryBinding
import com.tnibler.system.preference.SettingsFragment
import com.zhuinden.simplestackextensions.fragments.KeyedFragment

class PickOutputDirFragment : KeyedFragment(R.layout.pick_out_directory) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = PickOutDirectoryBinding.bind(view)
        with(binding) {
            val openDocumentTree = object : ActivityResultContracts.OpenDocumentTree() {
                override fun createIntent(context: Context, input: Uri?): Intent {
                    return super.createIntent(context, input)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
            }
            val pickOutputDir = registerForActivityResult(openDocumentTree) { uri ->
                uri ?: return@registerForActivityResult
                requireContext().contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putString(SettingsFragment.PREF_OUTPUT_DIRECTORY, uri.toString())
                    commit()
                }
                (requireActivity() as MainActivity).nextOnboardingScreen(getKey())
            }
            pickOutDirButton.setOnClickListener {
                pickOutputDir.launch(null)
            }
        }
    }
}