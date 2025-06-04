package com.draco.ladb.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
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

    private var lastCommand = ""

    private var bookmarkGetResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val text = it.data?.getStringExtra(Intent.EXTRA_TEXT) ?: return@registerForActivityResult
        binding.command.setText(text)
    }

    private fun setupUI() {
        /* Fix stupid Google edge-to-edge bullshit */
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { v, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBarsInsets.left
                bottomMargin = systemBarsInsets.bottom
                rightMargin = systemBarsInsets.right
                topMargin = systemBarsInsets.top
            }
            binding.statusBarBackground.updateLayoutParams {
                height = statusBarInsets.top
            }

            WindowInsetsCompat.CONSUMED
        }
        supportActionBar!!.elevation = 0f
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)
            .setPositiveButton(R.string.pair, null)
            .setNegativeButton(R.string.help, null)
            .setNeutralButton(R.string.skip, null)

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
        binding.commandContainer.hint =
            if (ready) getString(R.string.command_hint) else getString(R.string.command_hint_waiting)
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
                imm.showSoftInput(binding.command, InputMethodManager.SHOW_FORCED)
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
        viewModel.adb.running.observe(this) { started ->
            setReadyForInput(started == true)
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupDataListeners()

        /* Ensure we are not running this a second time around */
        if (viewModel.viewModelHasStartedADB.value != true) {
            if (viewModel.isPairing.value != true)
                pairAndStart()
        }

        viewModel.piracyCheck(this)
    }

    /**
     * Ask the user to pair
     */
    private fun askToPair(callback: ((Boolean) -> (Unit))? = null) {
        pairDialog
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val port = findViewById<TextInputEditText>(R.id.port)!!.text.toString()
                        val code = findViewById<TextInputEditText>(R.id.code)!!.text.toString()
                        dismiss()

                        lifecycleScope.launch(Dispatchers.IO) {
                            viewModel.adb.debug("Trying to pair...")
                            val success = viewModel.adb.pair(port, code)
                            callback?.invoke(success)
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tutorial_url)))
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Snackbar.make(
                                binding.output,
                                getString(R.string.snackbar_intent_failed),
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        PreferenceManager.getDefaultSharedPreferences(context).edit(true) {
                            putBoolean(getString(R.string.auto_shell_key), false)
                        }
                        dismiss()
                        callback?.invoke(true)
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