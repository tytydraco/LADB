package com.draco.ladb.fragments

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.draco.ladb.R
import com.draco.ladb.utils.ADB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HelpPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.help, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            getString(R.string.reset_key) -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    with(ADB.getInstance(requireContext())) {
                        reset()
                    }
                }
                activity?.finish()
            }
        }

        return super.onPreferenceTreeClick(preference)
    }
}