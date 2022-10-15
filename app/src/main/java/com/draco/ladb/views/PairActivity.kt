package com.draco.ladb.views

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityPairBinding
import com.google.android.material.snackbar.Snackbar

class PairActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairBinding

    private fun tryToLaunchIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(
                binding.root,
                getString(R.string.snackbar_intent_failed),
                Snackbar.LENGTH_SHORT
            )
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {}

        binding.help.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tutorial_url)))
            tryToLaunchIntent(intent)
        }

        binding.settings.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity"
                )
            )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

            tryToLaunchIntent(intent)
        }

        binding.pair.setOnClickListener {
            val port = binding.port.text.toString()
            val code = binding.code.text.toString()

            if (port.isEmpty() || code.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.snackbar_bad_input),
                    Snackbar.LENGTH_SHORT
                )
                    .show()
                return@setOnClickListener
            }

            val intent = Intent()
                .putExtra("port", port)
                .putExtra("code", code)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}