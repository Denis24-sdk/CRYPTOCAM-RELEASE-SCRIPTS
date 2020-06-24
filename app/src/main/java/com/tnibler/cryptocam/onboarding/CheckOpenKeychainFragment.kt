package com.tnibler.cryptocam.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tnibler.cryptocam.databinding.CheckOpenkeychainBinding

class CheckOpenKeychainFragment : Fragment() {
    lateinit var binding: CheckOpenkeychainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = CheckOpenkeychainBinding.inflate(inflater, container, false)
        return binding.root
    }
}