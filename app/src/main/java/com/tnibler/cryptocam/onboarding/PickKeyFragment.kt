package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.PickKeyBinding
import com.tnibler.cryptocam.keys.EditKeyDialog
import com.tnibler.cryptocam.keys.ImportKeyDialog
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.scanKey.ScannerKey
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PickKeyFragment : KeyedFragment(R.layout.pick_key) {
    private val viewModel: PickKeyViewModel by lazy { lookup() }
    private val keyManager: KeyManager by lazy { lookup() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = PickKeyBinding.bind(view)
        with(binding) {
            pickKeyScanButton.setOnClickListener {
                backstack.goTo(ScannerKey())
            }
            pickKeyImportAsTextButton.setOnClickListener {
                val dialog = ImportKeyDialog() { recipient ->
                    goNext()
                }
                dialog.show(childFragmentManager, null)
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.keyScanned.collect { recipient ->
                        val dialog = EditKeyDialog(recipient) { recipient ->
                            val success = keyManager.importRecipient(recipient)
                            if (!success) {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.import_key_fail),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                goNext()
                            }
                        }
                        dialog.show(this@PickKeyFragment.childFragmentManager, null)
                    }
                    keyManager.availableKeys.collect { keys ->
                        if (keys.isNotEmpty()) {
                            goNext()
                        }
                    }
                }
            }
        }
    }

    private fun goNext() {
        (requireActivity() as MainActivity).nextOnboardingScreen(getKey())
    }
}