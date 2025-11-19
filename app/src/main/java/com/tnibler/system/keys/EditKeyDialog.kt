package com.tnibler.system.keys

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.tnibler.system.R
import com.tnibler.system.databinding.EditKeyBinding
import org.apache.commons.codec.binary.Hex

class EditKeyDialog(
    private val recipient: KeyManager.X25519Recipient,
    val onCancel: (() -> Unit)? = null,
    val onKeySaved: (KeyManager.X25519Recipient) -> Unit,
) : DialogFragment() {
    private val TAG = javaClass.simpleName
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.edit_key, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = EditKeyBinding.bind(view)
        dialog?.setCanceledOnTouchOutside(false)
        with(binding) {
            editKeyPublicKeyView.text = recipient.publicKey
            editKeyFingerprintView.text = Hex.encodeHexString(recipient.fingerprint)
            editKeyNameEdit.setText(recipient.name, TextView.BufferType.EDITABLE)
            editKeyButtonCancel.setOnClickListener {
                dismiss()
                onCancel?.invoke()
            }
            editKeyButtonSave.setOnClickListener {
                val name = editKeyNameEdit.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.name_empty_error),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onKeySaved(recipient.copy(name = name))
                    dismiss()
                }
            }
        }
    }
}