package com.tnibler.cryptocam.keys.keyList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.addRepeatingJob
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.KeyListBinding
import com.tnibler.cryptocam.keys.EditKeyDialog
import com.tnibler.cryptocam.keys.ImportKeyDialog
import com.tnibler.cryptocam.keys.keyDetail.KeyDetailKey
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.scanKey.ScannerKey
import com.tnibler.cryptocam.onboarding.PickKeyViewModel
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

class KeysFragment : KeyedFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.key_list, container, false)

    private val keyManager: KeyManager by lazy { lookup() }
    private val viewModel: PickKeyViewModel by lazy { lookup() }
    private val TAG = javaClass.simpleName

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = KeyListBinding.bind(view)
        with(binding) {
            val layoutManager = LinearLayoutManager(root.context).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            keysRecycler.layoutManager = layoutManager
            val onKeyItemClicked = { keyItem: KeyItem ->
                backstack.goTo(KeyDetailKey(keyItem.recipient))
            }
            val onKeyItemChecked = { keyItem: KeyItem, isChecked: Boolean ->
                keyManager.setRecipientSelected(keyItem.recipient, isChecked)
                if (keyManager.selectedRecipients.value.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.must_select_one_key), Toast.LENGTH_SHORT).show()
                }
            }
            val adapter = (keysRecycler.adapter as? KeyAdapter) ?: KeyAdapter(onKeyItemClicked, onKeyItemChecked)
            keysRecycler.adapter = adapter
            lifecycleScope.launchWhenResumed {
                keyManager.availableKeys
                    .zip(keyManager.availableKeys) { a, b -> a to b }
                    .map { (recipients, selected) ->
                        recipients.map { recipient -> keyManager.toDisplayItem(recipient) }
                    }
                    .collect { keyItems ->
                        adapter.submitList(keyItems)
                    }
            }

            setHasOptionsMenu(true)
            keysToolbar.inflateMenu(R.menu.key_list)
            keysToolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.keyListImportFromString) {
                    val dialog = ImportKeyDialog()
                    dialog.show(childFragmentManager, null)
                    true
                }
                false
            }

            keysFab.setOnClickListener {
                backstack.goTo(ScannerKey())
            }
            viewLifecycleOwner.addRepeatingJob(Lifecycle.State.STARTED) {
                viewModel.keyScanned.collect { recipient ->
                    showImportDialog(recipient, onCancel = {}) {
                        adapter.submitList(keyManager.availableKeys.value.map { r ->
                            keyManager.toDisplayItem(
                                r
                            )
                        })
                    }
                }
            }
            val key = getKey<KeysKey>()
            if (key.importRecipient != null) {
                showImportDialog(key.importRecipient,
                    onCancel = {
                        if (keyManager.availableKeys.value.isEmpty()) {
                            requireActivity().finish()
                        }
                    },
                    onSuccess = {
                        adapter.submitList(keyManager.availableKeys.value.map { r ->
                            keyManager.toDisplayItem(
                                r
                            )
                        })
                    })
            }
        }
    }

    private fun showImportDialog(
        recipient: KeyManager.X25519Recipient,
        onCancel: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val dialog = EditKeyDialog(recipient, onCancel) { recipient ->
            val success = keyManager.importRecipient(recipient)
            if (!success) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_key_fail),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onSuccess()
            }
        }
        dialog.show(this@KeysFragment.childFragmentManager, null)
    }
}