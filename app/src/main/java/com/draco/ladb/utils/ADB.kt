package com.draco.ladb.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.draco.ladb.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.PrintStream

class ADB(private val context: Context) {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 8
        const val OUTPUT_BUFFER_DELAY_MS = 100L

        @Volatile private var instance: ADB? = null
        fun getInstance(context: Context): ADB = instance ?: synchronized(this) {
            instance ?: ADB(context).also { instance = it }
        }
    }

    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"
    private val scriptPath = "${context.getExternalFilesDir(null)}/script.sh"

    /**
     * Is the shell ready to handle commands?
     */
    private val _ready = MutableLiveData<Boolean>()
    val ready: LiveData<Boolean> = _ready

    /**
     * Is the shell closed for any reason?
     */
    private val _closed = MutableLiveData<Boolean>()
    val closed: LiveData<Boolean> = _closed

    /**
     * Where shell output is stored
     */
    val outputBufferFile: File = File.createTempFile("buffer", ".txt").also {
        it.deleteOnExit()
    }

    /**
     * Single shell instance where we can pipe commands to
     */
    private var shellProcess: Process? = null

    /**
     * Decide how to initialize the shellProcess variable
     */
    fun initializeClient() {
        if (_ready.value == true)
            return

        val autoShell = sharedPrefs.getBoolean(context.getString(R.string.auto_shell_key), true)
        if (autoShell)
            initializeADBShell()
        else
            initializeShell()
    }

    /**
     * Scan and make a connection to a wireless device
     */
    private fun initializeADBShell() {
        debug("Starting ADB client")
        adb(false, listOf("start-server"))?.waitFor()
        debug("Waiting for device to be found")
        adb(false, listOf("wait-for-device"))?.waitFor()

        debug("Shelling into device")
        val process = adb(true, listOf("-t", "1", "shell"))
        if (process == null) {
            debug("Failed to open shell connection")
            return
        }
        shellProcess = process
        sendToShellProcess("echo 'Success! ※\\(^o^)/※'")
        _ready.postValue(true)

        startShellDeathThread()
    }

    /**
     * Make a local shell instance
     */
    private fun initializeShell() {
        debug("Shelling into device")
        val process = shell(true, listOf("sh", "-l"))
        if (process == null) {
            debug("Failed to open shell connection")
            return
        }
        shellProcess = process
        sendToShellProcess("alias adb=\"$adbPath\"")
        sendToShellProcess("echo 'Success! ※\\(^o^)/※'")
        _ready.postValue(true)

        startShellDeathThread()
    }

    /**
     * Start a death listener to restart the shell once it dies
     */
    private fun startShellDeathThread() {
        GlobalScope.launch(Dispatchers.IO) {
            shellProcess?.waitFor()
            _ready.postValue(false)
            debug("Shell is dead, resetting")
            delay(1_000)
            adb(false, listOf("kill-server"))?.waitFor()
            initializeClient()
        }
    }

    /**
     * Completely reset the ADB client
     */
    fun reset() {
        _ready.postValue(false)
        outputBufferFile.writeText("")
        debug("Destroying shell process")
        shellProcess?.destroyForcibly()
        debug("Disconnecting all clients")
        adb(false, listOf("disconnect"))?.waitFor()
        debug("Killing ADB server")
        adb(false, listOf("kill-server"))?.waitFor()
        debug("Erasing all ADB server files")
        with (sharedPrefs.edit()) {
            putBoolean(context.getString(R.string.paired_key), false)
            apply()
        }
        context.filesDir.deleteRecursively()
        context.cacheDir.deleteRecursively()
        _closed.postValue(true)
    }

    /**
     * Ask the device to pair on Android 11 phones
     */
    fun pair(port: String, pairingCode: String) {
        val pairShell = adb(true, listOf("pair", "localhost:$port"))

        /* Sleep to allow shell to catch up */
        Thread.sleep(1000)

        /* Pipe pairing code */
        PrintStream(pairShell?.outputStream).apply {
            println(pairingCode)
            flush()
        }

        /* Continue once finished pairing */
        pairShell?.waitFor()
    }

    /**
     * Send a raw ADB command
     */
    private fun adb(redirect: Boolean, command: List<String>): Process? {
        val commandList = command.toMutableList().also {
            it.add(0, adbPath)
        }
        return shell(redirect, commandList)
    }

    /**
     * Send a raw shell command
     */
    private fun shell(redirect: Boolean, command: List<String>): Process? {
        val processBuilder = ProcessBuilder(command)
            .directory(context.filesDir)
            .apply {
                if (redirect) {
                    redirectErrorStream(true)
                    redirectOutput(outputBufferFile)
                }

                environment().apply {
                    put("HOME", context.filesDir.path)
                    put("TMPDIR", context.cacheDir.path)
                }
            }

        return try {
            processBuilder.start()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Execute a script
     */
    fun sendScript(code: String) {
        /* Store script locally */
        val internalScript = File(scriptPath).apply {
            bufferedWriter().use {
                it.write(code)
            }
            deleteOnExit()
        }

        /* Execute the script here */
        sendToShellProcess("sh ${internalScript.absolutePath}")
    }

    /**
     * Send commands directly to the shell process
     */
    fun sendToShellProcess(msg: String) {
        PrintStream(shellProcess?.outputStream).apply {
            println(msg)
            flush()
        }
    }

    /**
     * Write a debug message to the user
     */
    fun debug(msg: String) {
        synchronized(outputBufferFile) {
            if (outputBufferFile.exists())
                outputBufferFile.appendText(">>> $msg" + System.lineSeparator())
        }
    }
}