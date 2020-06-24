package com.tnibler.cryptocam.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.databinding.InstallOpenkeychainBinding

class InstallOpenKeychainFragment : Fragment() {
    lateinit var binding: InstallOpenkeychainBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = InstallOpenkeychainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.run {
            installOKButton.setOnClickListener {
                try {
                    val uri = Uri.parse("https://f-droid.org/en/packages/org.sufficientlysecure.keychain/")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                }
                catch (e: Exception) {
                    val uri = Uri.parse("https://f-droid.org/")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                }
            }

            installOKButtonCheckAgain.setOnClickListener {
                (activity as MainActivity).connectOpenPgp()
            }
        }
    }
}