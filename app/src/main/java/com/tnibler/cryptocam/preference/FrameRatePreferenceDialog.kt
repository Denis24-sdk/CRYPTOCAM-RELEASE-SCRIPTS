package com.tnibler.cryptocam.preference

import android.icu.text.MessagePattern
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import com.tnibler.cryptocam.R
import kotlinx.android.synthetic.main.preference_framerate.*
import kotlinx.android.synthetic.main.preference_framerate.view.*
import kotlinx.android.synthetic.main.preference_framerate.view.preferenceFpsRadioGroup

class FrameRatePreferenceDialog : ListPreferenceDialogFragmentCompat() {
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder?) {
        super.onPrepareDialogBuilder(builder)
    }

    companion object {
        fun newInstance(key: String): FrameRatePreferenceDialog {
            val args = Bundle().apply {
                putString(ARG_KEY, key)
            }

            val fragment = FrameRatePreferenceDialog()
            fragment.arguments = args
            return fragment
        }
    }
}