package com.draco.ladb.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import com.draco.ladb.BuildConfig
import com.draco.ladb.R
import com.draco.ladb.models.ProcessInfo
import com.draco.ladb.viewmodels.MainActivityViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintStream
import java.lang.Runnable
import java.util.concurrent.CountDownLatch

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainActivityViewModel

    /* UI components */
    private lateinit var command: TextInputEditText
    private lateinit var output: MaterialTextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var progress: ProgressBar

    /* Alert dialogs */
    private lateinit var helpDialog: MaterialAlertDialogBuilder
    private lateinit var pairDialog: MaterialAlertDialogBuilder

    /* Path to ADB binary */
    private lateinit var adbPath: String

    /* Shell objects */
    private lateinit var adbShellProcess: Process
    private lateinit var outputBufferFile: File

    /* Latch that gets decremented after user provides pairing port and code */
    private val pairingInfoLatch = CountDownLatch(1)

    /* Coroutines */
    private lateinit var outputThreadJob: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        command = findViewById(R.id.command)
        output = findViewById(R.id.output)
        outputScrollView = findViewById(R.id.output_scrollview)
        progress = findViewById(R.id.progress)

        viewModel.commandString.observe(this, Observer {
            command.setText(it)
        })

        viewModel.outputString.observe(this, Observer {
            output.text = it
        })

        helpDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.dismiss, null)
            .setNegativeButton(R.string.reset) { _, _ ->
                progress.visibility = View.VISIBLE
                command.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    outputBufferFile.writeText("")
                    debugMessage("Disconnecting all clients")
                    adb(false, "disconnect").waitFor()
                    debugMessage("Killing server")
                    adb(false, "kill-server").waitFor()
                    debugMessage("Clearing pairing memory")
                    with (getPreferences(MODE_PRIVATE).edit()) {
                        putBoolean("paired", false)
                        apply()
                    }
                    debugMessage("Erasing all ADB server files")
                    filesDir.deleteRecursively()
                    debugMessage("LADB reset complete!")
                    debugMessage("Exiting in three seconds")
                    Thread.sleep(3000)
                    finishAffinity()
                }
            }

        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setMessage(R.string.pair_message)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)

        adbPath = "${applicationInfo.nativeLibraryDir}/libadb.so"

        /* Store the buffer locally to avoid an OOM error */
        outputBufferFile = File.createTempFile("buffer", ".txt").apply {
            deleteOnExit()
        }

        command.setOnKeyListener { _, keyCode, event ->
            viewModel.commandString.value = command.text.toString()
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val text = viewModel.commandString.value
                viewModel.commandString.value = ""

                lifecycleScope.launch(Dispatchers.IO) {
                    /* Pipe commands directly to shell process */
                    PrintStream(adbShellProcess.outputStream).apply {
                        println(text)
                        flush()
                    }
                }

                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }

        /* Prepare client */
        initializeClient {
            /* If we started from a shell script, launch it after client init */
            if (intent.type == "text/plain" || intent.type == "text/x-sh")
                executeFromScript()
        }

        with (getPreferences(Context.MODE_PRIVATE)) {
            if (getBoolean("firstLaunch", true)) {
                helpDialog.show()

                with (edit()) {
                    putBoolean("firstLaunch", false)
                    apply()
                }
            }
        }
    }

    private fun executeFromScript() {
        val script = when (intent.type) {
            "text/x-sh" -> {
                val uri = Uri.parse(intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM).toString())
                contentResolver.openInputStream(uri)?.bufferedReader().use {
                    it?.readText()
                }
            }
            "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return

        /* Store script locally */
        val scriptPath = "${getExternalFilesDir(null)}/script.sh"
        val internalScript = File(scriptPath).apply {
            bufferedWriter().use {
                it.write(script)
            }
            deleteOnExit()
        }

        Snackbar.make(output, getString(R.string.snackbar_file_opened), Snackbar.LENGTH_SHORT)
            .setAction(getString(R.string.dismiss)) {}
            .show()

        /* Execute the script here */
        PrintStream(adbShellProcess.outputStream).apply {
            println("sh ${internalScript.absolutePath}")
            flush()
        }
    }

    private fun readEndOfFile(file: File): String {
        val out = ByteArray(ProcessInfo.MAX_OUTPUT_BUFFER_SIZE)
        file.inputStream().use {
            val size = it.channel.size()

            if (size <= out.size)
                return String(it.readBytes())

            val newPos = (it.channel.size() - out.size)
            it.channel.position(newPos)
            it.read(out)
        }

        return String(out)
    }

    private fun startOutputFeed() {
        outputThreadJob = lifecycleScope.launch(Dispatchers.IO) {
            while (outputBufferFile.exists()) {
                yield()
                val out = readEndOfFile(outputBufferFile)
                val currentText = viewModel.outputString.value
                if (out != currentText) {
                    viewModel.outputString.postValue(out)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                        runOnUiThread {
                            outputScrollView.post {
                                outputScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                    }
                }
                Thread.sleep(ProcessInfo.OUTPUT_BUFFER_DELAY_MS)
            }
        }
    }

    private fun initializeClient(callback: Runnable? = null) {
        progress.visibility = View.VISIBLE
        command.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            /* Begin forwarding output buffer text to output view */
            startOutputFeed()

            /* If we have not been paried yet, do so now */
            if (!getPreferences(MODE_PRIVATE).getBoolean("paired", false)) {
                /* SDK 30+ need to pair to the device using a new method */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    debugMessage("Requesting pairing information")
                    runOnUiThread {
                        handlePairing()
                    }

                    /* Wait for backend pairing to finish */
                    pairingInfoLatch.await()
                }
            }

            debugMessage("Waiting for device to accept connection. This part may take a while.")
            adb(false, "wait-for-device").waitFor()

            debugMessage("Connection established")
            with (getPreferences(MODE_PRIVATE).edit()) {
                putBoolean("paired", true)
                apply()
            }

            debugMessage("Shelling into device")
            adbShellProcess = adb(true, "shell")

            runOnUiThread {
                command.isEnabled = true
                progress.visibility = View.INVISIBLE
            }

            callback?.run()

            adbShellProcess.waitFor()
            debugMessage("Shell has died")

            runOnUiThread {
                command.isEnabled = false
            }
        }
    }

    private fun handlePairing() {
        pairDialog
            .create()
            .apply {
                setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay)) { _, _ ->
                    val port = findViewById<TextInputEditText>(R.id.port)!!.text.toString()
                    val code = findViewById<TextInputEditText>(R.id.code)!!.text.toString()

                    lifecycleScope.launch(Dispatchers.IO) {
                        debugMessage("Requesting additional pairing information")
                        val pairShell = adb(true, "pair", "localhost:$port")

                        /* Sleep to allow shell to catch up */
                        Thread.sleep(1000)

                        /* Pipe pairing code */
                        PrintStream(pairShell.outputStream).apply {
                            println(code)
                            flush()
                        }

                        /* Continue once finished pairing */
                        pairShell.waitFor()
                        pairingInfoLatch.countDown()
                    }
                }
            }
            .show()
    }

    private fun debugMessage(msg: String) {
        outputBufferFile.appendText("DEBUG: " + msg + System.lineSeparator())
    }

    private fun adb(redirect: Boolean, vararg command: String): Process {
        val commandList = ArrayList<String>().apply {
            add(adbPath)
            for (piece in command)
                add(piece)
        }

        return ProcessBuilder(commandList)
            .apply {
                if (redirect) {
                    redirectErrorStream(true)
                    redirectOutput(outputBufferFile)
                }

                environment().apply {
                    put("HOME", filesDir.path)
                    put("TMPDIR", cacheDir.path)
                }
            }
            .start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.help -> {
                helpDialog.show()
                true
            }
            R.id.share -> {
                try {
                    val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", outputBufferFile)
                    val intent = Intent(Intent.ACTION_SEND)
                    with (intent) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "file/*"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(output, getString(R.string.snackbar_intent_failed), Snackbar.LENGTH_SHORT)
                        .setAction(getString(R.string.dismiss)) {}
                        .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        outputThreadJob.cancel()
    }
}