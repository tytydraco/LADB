package com.draco.ladb.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.draco.ladb.BuildConfig
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityMainBinding
import com.draco.ladb.viewmodels.MainActivityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var pairDialog: MaterialAlertDialogBuilder
    private lateinit var badAbiDialog: MaterialAlertDialogBuilder
    private var lastCommand = ""

    private var bookmarkGetResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val text = it.data?.getStringExtra(Intent.EXTRA_TEXT) ?: return@registerForActivityResult
        binding.command.setText(text)
    }

    private fun setupUI() {
        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)
            .setNeutralButton(R.string.help, null)

        badAbiDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bad_abi_title)
            .setMessage(R.string.bad_abi_message)
            .setPositiveButton(R.string.dismiss, null)

        binding.command.setOnKeyListener { _, keyCode, keyEvent ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN) {
                sendCommandToADB()
                return@setOnKeyListener true
            } else {
                return@setOnKeyListener false
            }
        }

        binding.command.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommandToADB()
                return@setOnEditorActionListener true
            } else {
                return@setOnEditorActionListener false
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

    private fun setReadyForInput(ready: Boolean) {
        binding.command.isEnabled = ready
        binding.commandContainer.hint = if (ready) getString(R.string.command_hint) else getString(R.string.command_hint_waiting)
        binding.progress.visibility = if (ready) View.INVISIBLE else View.VISIBLE
    }

    private fun setupDataListeners() {
        /* Update the output text */
        viewModel.outputText.observe(this) { newText ->
            binding.output.text = newText
            binding.outputScrollview.post {
                binding.outputScrollview.fullScroll(ScrollView.FOCUS_DOWN)
                binding.command.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.command, InputMethod.SHOW_EXPLICIT)
            }
        }

        /* Restart the activity on reset */
        viewModel.adb.closed.observe(this) { closed ->
            if (closed == true) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                exitProcess(0)
            }
        }

        /* Prepare progress bar, pairing latch, and script executing */
        viewModel.adb.started.observe(this) { started ->
            if (started == true) {
                lifecycleScope.launch(Dispatchers.IO) {
                    runOnUiThread { setReadyForInput(true) }
                    executeScriptFromIntent()
                }
            } else {
                runOnUiThread { setReadyForInput(false) }
                return@observe
            }
        }
    }

    private fun pairAndStart() {
        if (viewModel.needsToPair()) {
            viewModel.adb.debug("Requesting pairing information")
            askToPair { thisPairSuccess ->
                if (thisPairSuccess) {
                    viewModel.setPairedBefore(true)
                    viewModel.startADBServer()
                } else {
                    /* Failed; try again! */
                    viewModel.adb.debug("Failed to pair! Trying again...")
                    runOnUiThread { pairAndStart() }
                }
            }
        } else {
            viewModel.startADBServer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupDataListeners()
        pairAndStart()

        if (viewModel.isAbiUnsupported()) {
            badAbiDialog.show()
        }

        viewModel.piracyCheck(this)
    }

    /**
     * Execute a script from the main intent if one was given
     */
    private fun executeScriptFromIntent() {
        if (viewModel.getScriptFromIntent(intent) == null)
            return

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
    private fun askToPair(callback: ((Boolean) -> (Unit))? = null) {
        val createdPairDialog = pairDialog
            .create()

        createdPairDialog
            .setOnShowListener {
                val neutralButton = createdPairDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                neutralButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tutorial_url)))
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Snackbar.make(binding.output, getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        createdPairDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay)) { _, _ ->
            val port = createdPairDialog.findViewById<TextInputEditText>(R.id.port)!!.text.toString()
            val code = createdPairDialog.findViewById<TextInputEditText>(R.id.code)!!.text.toString()

            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.adb.debug("Trying to pair...")
                val success = viewModel.adb.pair(port, code)
                callback?.invoke(success)
            }
        }
        createdPairDialog.show()
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
            R.id.more -> {
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
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .setType("file/*")
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }
}