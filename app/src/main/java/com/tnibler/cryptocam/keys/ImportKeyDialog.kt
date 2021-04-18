package com.tnibler.cryptocam.keys

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.ImportKeyBinding
import com.zhuinden.simplestackextensions.fragmentsktx.lookup

class ImportKeyDialog : DialogFragment() {
    override fun onStart() {
        super.onStart()
        // match_parent as layout_width in xml isn't working for some reason
        dialog?.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.import_key, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(false)
        val binding = ImportKeyBinding.bind(view)
        with(binding) {
            importKeyButtonSave.setOnClickListener {
                importKeyNameErrorView.visibility = View.INVISIBLE
                importKeyErrorView.visibility = View.INVISIBLE
                val name = importKeyNameEdit.text.toString()
                if (name.isBlank()) {
                    importKeyNameErrorView.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val publicKey = importKeyPublicKeyEdit.text.toString().trim()
                val recipient = KeyManager.parseRecipient(name, publicKey)
                if (recipient == null) {
                    importKeyErrorView.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val keyManager = lookup<KeyManager>()
                keyManager.importRecipient(recipient)
                keyManager.setRecipientSelected(recipient, true)
                dismiss()
            }
            importKeyButtonCancel.setOnClickListener {
                dismiss()
            }
        }
    }
}