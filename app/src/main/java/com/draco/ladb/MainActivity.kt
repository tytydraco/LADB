package com.draco.ladb

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    private lateinit var command: TextInputEditText
    private lateinit var output: TextInputEditText
    private lateinit var progress: ProgressBar

    private var currentProcess: Process? = null

    private lateinit var adbPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        command = findViewById(R.id.command)
        output = findViewById(R.id.output)
        progress = findViewById(R.id.progress)

        adbPath = "${applicationInfo.nativeLibraryDir}/libadb.so"

        /* Kill any existing servers */
        reset()

        command.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val thisCommand = command.text.toString()
                command.hint = thisCommand
                command.text = null

                val processBuilder = ProcessBuilder(
                    adbPath,
                    "-s", "localhost",
                    "shell",
                    thisCommand
                ).redirectErrorStream(true)

                processBuilder.environment().apply {
                    put("HOME", filesDir.path)
                    put("TMPDIR", cacheDir.path)
                }

                if (currentProcess != null) currentProcess!!.destroy()

                progress.visibility = View.VISIBLE
                currentProcess = processBuilder.start()

                output.text = null
                Thread {
                    currentProcess!!.waitFor()

                    try {
                        val out = currentProcess!!.inputStream.bufferedReader().readText()
                        if (out.isNotBlank()) runOnUiThread {
                            output.setText(out)
                            progress.visibility = View.INVISIBLE
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()

                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }
    }

    private fun reset() {
        progress.visibility = View.VISIBLE
        command.isEnabled = false
        command.text = null
        command.hint = null
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