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

class MainActivity : AppCompatActivity() {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 4
        const val OUTPUT_BUFFER_DELAY_MS = 100L
    }

    private lateinit var command: TextInputEditText
    private lateinit var output: MaterialTextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var progress: ProgressBar

    private var currentProcess: Process? = null

    private lateinit var adbPath: String

    private lateinit var outputBuffer: File

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
        reset()

        command.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val thisCommand = command.text.toString()
                output.text = null
                command.isEnabled = false

                val processBuilder = ProcessBuilder(
                    adbPath,
                    "-s", "localhost",
                    "shell",
                    thisCommand
                )
                    .redirectErrorStream(true)
                    .redirectOutput(outputBuffer)

                processBuilder.environment().apply {
                    put("HOME", filesDir.path)
                    put("TMPDIR", cacheDir.path)
                }

                if (currentProcess != null) currentProcess!!.destroy()

                progress.visibility = View.VISIBLE
                currentProcess = processBuilder.start()

                Thread {
                    /* Until finished, update the output */
                    while (currentProcess!!.isAlive) {
                        val out = readEndOfFile(outputBuffer)
                        runOnUiThread {
                            output.text = out
                        }
                        outputScrollView.post {
                            outputScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                        Thread.sleep(OUTPUT_BUFFER_DELAY_MS)
                    }

                    /* Hide the progress and update the final output */
                    val out = readEndOfFile(outputBuffer)
                    runOnUiThread {
                        progress.visibility = View.INVISIBLE
                        runOnUiThread {
                            output.text = out
                        }
                        outputScrollView.post {
                            outputScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                        command.isEnabled = true
                    }
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

    private fun reset() {
        progress.visibility = View.VISIBLE
        command.isEnabled = false
        command.text = null
        output.text = null

        Thread {
            ProcessBuilder(
                adbPath,
                "disconnect"
            ).apply {
                environment().apply {
                    put("HOME", filesDir.path)
                    put("TMPDIR", cacheDir.path)
                }
                start().waitFor()
            }

            ProcessBuilder(
                adbPath,
                "connect",
                "localhost"
            ).apply {
                environment().apply {
                    put("HOME", filesDir.path)
                    put("TMPDIR", cacheDir.path)
                }
                start().waitFor()
            }

            runOnUiThread {
                command.isEnabled = true
                progress.visibility = View.INVISIBLE
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.reset -> {
                reset()
                true
            }
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