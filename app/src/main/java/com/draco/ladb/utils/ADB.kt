package com.draco.ladb.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.draco.ladb.BuildConfig
import com.draco.ladb.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.lang.NumberFormatException
import java.util.concurrent.TimeUnit

class ADB(private val context: Context) {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 16
        const val OUTPUT_BUFFER_DELAY_MS = 100L

        @SuppressLint("StaticFieldLeak")
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
     * Returns the user buffer size if valid, else the default
     */
    fun getOutputBufferSize(): Int {
        val userValue = sharedPrefs.getString(context.getString(R.string.buffer_size_key), "16384")!!
        return try {
            Integer.parseInt(userValue)
        } catch (_: NumberFormatException) {
            MAX_OUTPUT_BUFFER_SIZE
        }
    }

    /**
     * Decide how to initialize the shellProcess variable
     */
    fun initializeClient() {
        if (_ready.value == true)
            return

        val autoShell = sharedPrefs.getBoolean(context.getString(R.string.auto_shell_key), true)
        val autoPair = sharedPrefs.getBoolean(context.getString(R.string.auto_pair_key), true)
        val autoWireless = sharedPrefs.getBoolean(context.getString(R.string.auto_wireless_key), true)
        val startupCommand = sharedPrefs.getString(context.getString(R.string.startup_command_key), "echo 'Success! ※\\(^o^)/※'")!!

        initializeADBShell(autoShell, autoPair, autoWireless, startupCommand)
    }

    /**
     * Scan and make a connection to a wireless device
     */
    private fun initializeADBShell(autoShell: Boolean, autoPair: Boolean, autoWireless: Boolean, startupCommand: String) {
        val secureSettingsGranted =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        if (autoWireless) {
            debug("Enabling wireless debugging")
            if (secureSettingsGranted) {
                Settings.Global.putInt(
                    context.contentResolver,
                    "adb_wifi_enabled",
                    1
                )
                debug("Waiting a few moments...")
                Thread.sleep(3_000)
            } else {
                debug("NOTE: Secure settings permission not granted yet")
                debug("NOTE: After first pair, it will auto-grant")
            }
        }

        if (autoPair) {
            debug("Starting ADB client")
            adb(false, listOf("start-server"))?.waitFor()
            debug("Waiting for device respond (max 5m)")
            adb(false, listOf("wait-for-device"))?.waitFor()
        }

        debug("Shelling into device")
        val process = if (autoShell && autoPair) {
            val argList = if (Build.SUPPORTED_ABIS[0] == "arm64-v8a")
                listOf("-t", "1", "shell")
            else
                listOf("shell")
            adb(true, argList)
        } else
            shell(true, listOf("sh", "-l"))

        if (process == null) {
            debug("Failed to open shell connection")
            return
        }
        shellProcess = process

        sendToShellProcess("alias adb=\"$adbPath\"")

        if (autoWireless && !secureSettingsGranted) {
            sendToShellProcess("echo 'NOTE: Granting secure settings permission for next time'")
            sendToShellProcess("pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS &> /dev/null")
        }

        if (autoShell && autoPair)
            sendToShellProcess("echo 'NOTE: Dropped into ADB shell automatically'")
        else
            sendToShellProcess("echo 'NOTE: In unprivileged shell, not ADB shell'")

        if (startupCommand.isNotEmpty())
            sendToShellProcess(startupCommand)

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
        Thread.sleep(5000)

        /* Pipe pairing code */
        PrintStream(pairShell?.outputStream).apply {
            println(pairingCode)
            flush()
        }

        /* Continue once finished pairing (or 10s elapses) */
        pairShell?.waitFor(10, TimeUnit.SECONDS)
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
        if (shellProcess == null || shellProcess?.outputStream == null)
            return
        PrintStream(shellProcess!!.outputStream!!).apply {
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