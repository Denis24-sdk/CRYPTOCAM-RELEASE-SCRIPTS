package com.tnibler.cryptocam.keys

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.ScanMode
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.VideoFragment
import com.tnibler.cryptocam.databinding.ScanKeyBinding
import com.zhuinden.simplestackextensions.fragmentsktx.backstack
import com.zhuinden.simplestackextensions.fragmentsktx.lookup

class ScannerFragment : Fragment() {
    private var codeScanner: CodeScanner? = null
    private val TAG = javaClass.simpleName
    private val vibrator by lazy { ContextCompat.getSystemService(requireContext(), Vibrator::class.java) as Vibrator }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.scan_key, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = ScanKeyBinding.bind(view)
        if (!VideoFragment.allPermissionsGranted(requireContext())) {
            val requestPermissions = ActivityResultContracts.RequestMultiplePermissions()
            registerForActivityResult(requestPermissions) { result ->
                if (VideoFragment.allPermissionsGranted(requireContext())) {
                    setupUi(binding)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                    ).show()
                    activity?.finish()
                }
            }.launch(VideoFragment.REQUIRED_PERMISSIONS)
        } else {
            setupUi(binding)
        }
    }

    private fun setupUi(binding: ScanKeyBinding) {
        with(binding) {
            val codeScanner = CodeScanner(requireActivity(), scannerView)
            this@ScannerFragment.codeScanner = codeScanner
            codeScanner.setDecodeCallback { result ->
                requireActivity().runOnUiThread {
                    val recipient = parseImportUri(result.text)
                    if (recipient != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(200, 128))
                        }
                        else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(200)
                        }
                        codeScanner.releaseResources()
                        lookup<OnKeyScannedListener>().onKeyScanned(recipient)
                        backstack.goBack()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.qr_scan_fail),
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            codeScanner.isAutoFocusEnabled = true
            codeScanner.scanMode = ScanMode.CONTINUOUS
            codeScanner.startPreview()
        }
    }

    override fun onResume() {
        super.onResume()
        codeScanner?.startPreview()
    }

    override fun onPause() {
        super.onPause()
        codeScanner?.releaseResources()
    }
}