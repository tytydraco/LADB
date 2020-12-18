package com.draco.ladb

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import java.io.File
import java.io.PrintStream

class MainActivity : AppCompatActivity() {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 4
        const val OUTPUT_BUFFER_DELAY_MS = 100L
    }

    private lateinit var command: TextInputEditText
    private lateinit var output: MaterialTextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var progress: ProgressBar

    private lateinit var currentProcess: Process

    private lateinit var adbPath: String

    private lateinit var outputBuffer: File
    private lateinit var printStream: PrintStream

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        command = findViewById(R.id.command)
        output = findViewById(R.id.output)
        outputScrollView = findViewById(R.id.output_scrollview)
        progress = findViewById(R.id.progress)

        adbPath = "${applicationInfo.nativeLibraryDir}/libadb.so"

        /* Store the buffer locally to avoid an OOM error */
        outputBuffer = File.createTempFile("buffer", "txt").apply {
            deleteOnExit()
        }

        /* Kill any existing servers */
        initializeClient()

        command.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                Thread {
                    printStream.println(command.text.toString())
                    printStream.flush()
                }.start()

                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }
    }

    private fun readEndOfFile(file: File): String {
        val out = ByteArray(MAX_OUTPUT_BUFFER_SIZE)
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

    private fun updateOutputFeed() {
        val out = readEndOfFile(outputBuffer)
        val currentText = output.text.toString()
        if (out != currentText) {
            runOnUiThread {
                output.text = out
                outputScrollView.post {
                    outputScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    private fun startOutputFeed() {
        Thread {
            while (currentProcess.isAlive) {
                updateOutputFeed()
                Thread.sleep(OUTPUT_BUFFER_DELAY_MS)
            }
            updateOutputFeed()
        }.start()
    }

    private fun initializeClient() {
        progress.visibility = View.VISIBLE
        command.isEnabled = false
        command.text = null
        output.text = null

        Thread {
            /* Disconnect other connections */
            ProcessBuilder(
                adbPath, "disconnect"
            )
                .redirectErrorStream(true)
                .redirectOutput(outputBuffer)
                .apply {
                    environment().apply {
                        put("HOME", filesDir.path)
                        put("TMPDIR", cacheDir.path)
                    }
                }
                .start()
                .waitFor()

            /* Wait patiently for user to accept connection */
            ProcessBuilder(
                adbPath, "wait-for-device"
            )
                .redirectErrorStream(true)
                .redirectOutput(outputBuffer)
                .apply {
                    environment().apply {
                        put("HOME", filesDir.path)
                        put("TMPDIR", cacheDir.path)
                    }
                }
                .start()
                .waitFor()

            /* Boot up a shell instance */
            currentProcess = ProcessBuilder(
                adbPath, "shell"
            )
                .redirectErrorStream(true)
                .redirectOutput(outputBuffer)
                .apply {
                    environment().apply {
                        put("HOME", filesDir.path)
                        put("TMPDIR", cacheDir.path)
                    }
                }
                .start()
            printStream = PrintStream(currentProcess.outputStream)

            startOutputFeed()

            runOnUiThread {
                command.isEnabled = true
                progress.visibility = View.INVISIBLE
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.help -> {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.help_title)
                    setMessage(R.string.help_message)
                    setPositiveButton(R.string.dismiss, null)
                    show()
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
}