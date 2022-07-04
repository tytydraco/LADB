package com.draco.ladb.views

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.draco.ladb.BuildConfig
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityMainBinding
import com.draco.ladb.viewmodels.MainActivityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    /* View Model */
    private val viewModel: MainActivityViewModel by viewModels()

    /* View Binding */
    private lateinit var binding: ActivityMainBinding

    /* Alert dialogs */
    private lateinit var pairDialog: MaterialAlertDialogBuilder
    private lateinit var badAbiDialog: MaterialAlertDialogBuilder

    /* Held when pairing */
    private var pairingLatch = CountDownLatch(0)

    private var lastCommand = ""
    private lateinit var sharedPrefs: SharedPreferences

    var bookmarkGetResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val text = it.data?.getStringExtra(Intent.EXTRA_TEXT) ?: return@registerForActivityResult
        binding.command.setText(text)
    }

    private fun setupUI() {
        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)

        badAbiDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bad_abi_title)
            .setMessage(R.string.bad_abi_message)
            .setPositiveButton(R.string.dismiss, null)

        binding.command.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendCommandToADB()
                return@setOnKeyListener true
            } else {
                return@setOnKeyListener false
            }
        }
    }

    private fun sendCommandToADB() {
        val text = binding.command.text.toString()
        lastCommand = text
        binding.command.text = null
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.adb.sendToShellProcess(text)
        }
    }

    private fun setupDataListeners() {
        /* Update the output text */
        viewModel.outputText.observe(this, Observer {
            binding.output.text = it
            binding.outputScrollview.post {
                binding.outputScrollview.fullScroll(ScrollView.FOCUS_DOWN)
                binding.command.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.command, InputMethod.SHOW_EXPLICIT)
            }
        })

        /* Restart the activity on reset */
        viewModel.adb.closed.observe(this, Observer {
            if (it == true) {
                val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                finishAffinity()
                startActivity(intent)
                exitProcess(0)
            }
        })

        /* Prepare progress bar, pairing latch, and script executing */
        viewModel.adb.ready.observe(this, Observer {
            if (it != true) {
                runOnUiThread {
                    binding.command.isEnabled = false
                    binding.progress.visibility = View.VISIBLE
                }
                return@Observer
            }

            lifecycleScope.launch(Dispatchers.IO) {
                pairingLatch.await()

                runOnUiThread {
                    binding.command.isEnabled = true
                    binding.progress.visibility = View.INVISIBLE
                }

                if (viewModel.getScriptFromIntent(intent) != null)
                    executeFromScript()
            }
        })
    }

    private fun pairIfNecessary() {
        if (viewModel.shouldWePair(sharedPrefs)) {
            pairingLatch = CountDownLatch(1)
            viewModel.adb.debug("Requesting pairing information")
            askToPair {
                with(sharedPrefs.edit()) {
                    putBoolean(getString(R.string.paired_key), true)
                    apply()
                }
                pairingLatch.countDown()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        setupUI()
        setupDataListeners()
        pairIfNecessary()

        if (viewModel.isAbiUnsupported()) {
            badAbiDialog.show()
        }

        viewModel.piracyCheck(this)
    }

    /**
     * Execute a script from the main intent
     */
    private fun executeFromScript() {
        val code = viewModel.getScriptFromIntent(intent) ?: return

        /* Invalidate intent */
        intent.type = ""

        Snackbar.make(binding.output, getString(R.string.snackbar_file_opened), Snackbar.LENGTH_SHORT)
            .setAction(getString(R.string.dismiss)) {}
            .show()

        viewModel.adb.sendScript(code)
    }

    /**
     * Ask the user to pair
     */
    private fun askToPair(callback: Runnable? = null) {
        pairDialog
            .create()
            .apply {
                setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay)) { _, _ ->
                    val port = findViewById<TextInputEditText>(R.id.port)!!.text.toString()
                    val code = findViewById<TextInputEditText>(R.id.code)!!.text.toString()

                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.adb.debug("Requesting additional pairing information")
                        viewModel.adb.pair(port, code)

                        callback?.run()
                    }
                }
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.bookmarks -> {
                val intent = Intent(this, BookmarksActivity::class.java)
                    .putExtra(Intent.EXTRA_TEXT, binding.command.text.toString())
                bookmarkGetResult.launch(intent)
                true
            }
            R.id.last_command -> {
                binding.command.setText(lastCommand)
                binding.command.setSelection(lastCommand.length)
                true
            }
            R.id.help -> {
                val intent = Intent(this, HelpActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.share -> {
                try {
                    val uri = FileProvider.getUriForFile(
                            this,
                            BuildConfig.APPLICATION_ID + ".provider",
                            viewModel.adb.outputBufferFile
                    )
                    val intent = Intent(Intent.ACTION_SEND)
                    with(intent) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "file/*"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(binding.output, getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT)
                            .setAction(getString(R.string.dismiss)) {}
                            .show()
                }
                true
            }
            R.id.clear -> {
                viewModel.clearOutputText()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }
}