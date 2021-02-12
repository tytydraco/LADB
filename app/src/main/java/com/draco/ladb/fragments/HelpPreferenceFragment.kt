package com.draco.ladb.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.draco.ladb.R
import com.draco.ladb.utils.ADB
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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

            getString(R.string.developer_key) -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/tytydraco"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(requireView(), getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT)
                        .setAction(getString(R.string.dismiss)) {}
                        .show()
                }
            }

            getString(R.string.source_key) -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/tytydraco/LADB"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(requireView(), getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT)
                        .setAction(getString(R.string.dismiss)) {}
                        .show()
                }
            }

            getString(R.string.contact_key) -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:tylernij@gmail.com?subject=LADB"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(requireView(), getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT)
                        .setAction(getString(R.string.dismiss)) {}
                        .show()
                }
            }

            getString(R.string.licenses_key) -> {
                val intent = Intent(requireContext(), OssLicensesMenuActivity::class.java)
                startActivity(intent)
            }

            else -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(preference.title)
                    .setMessage(preference.summary)
                    .show()
            }
        }

        return super.onPreferenceTreeClick(preference)
    }
}