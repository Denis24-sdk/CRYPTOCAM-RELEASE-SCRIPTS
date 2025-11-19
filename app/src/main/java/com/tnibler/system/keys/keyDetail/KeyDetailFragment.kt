package com.tnibler.system.keys.keyDetail

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.tnibler.system.R
import com.tnibler.system.databinding.KeyDetailBinding
import com.tnibler.system.keys.KeyManager
import com.zhuinden.simplestackextensions.fragments.KeyedFragment
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup
import org.apache.commons.codec.binary.Hex

class KeyDetailFragment : KeyedFragment(R.layout.key_detail) {
    private val TAG = javaClass.simpleName
    private val clipboardManager: ClipboardManager by lazy { ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java) as ClipboardManager }
    private val recipient by lazy { getKey<KeyDetailKey>().recipient }
    private val keyManager by lazy { lookup<KeyManager>() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = KeyDetailBinding.bind(view)
        with(binding) {
            setHasOptionsMenu(true)
            keyDetailToolbar.title = recipient.name
            keyDetailToolbar.inflateMenu(R.menu.key_detail)
            keyDetailToolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.keyDetailDelete) {
                        AlertDialog.Builder(requireContext())
                            .setCancelable(true)
                            .setTitle(getString(R.string.confirm_delete_key, recipient.name))
                            .setNegativeButton(android.R.string.cancel) { dialog, which ->
                                dialog.dismiss()
                            }
                            .setPositiveButton(android.R.string.ok) { dialog, which ->
                                keyManager.deleteRecipient(recipient)
                                dialog.dismiss()
                                backstack.goBack()
                            }
                            .show()
                    true
                }
                false
            }


            keyDetailKeyView.text = recipient.publicKey
            keyDetailFingerprintView.text = Hex.encodeHexString(recipient.fingerprint)

            keyDetailCopyButton.setOnClickListener {
                val clip = ClipData.newPlainText("Public key", recipient.publicKey)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(requireContext(), getString(R.string.copied_public_key), Toast.LENGTH_SHORT).show()
            }

            keyDetailQrCodeView.post {
                val bitmap = createQrCode(recipient, keyDetailQrCodeView.width)
                if (bitmap != null) {
                    keyDetailQrCodeView.setImageBitmap(bitmap)
                }
            }
            keyDetailQrCodeView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }
}
