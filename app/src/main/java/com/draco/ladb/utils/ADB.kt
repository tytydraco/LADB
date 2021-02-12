package com.draco.ladb.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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

    private val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"
    private val scriptPath = "${context.getExternalFilesDir(null)}/script.sh"

    private val ready = MutableLiveData<Boolean>()
    fun getReady(): LiveData<Boolean> = ready

    private lateinit var shellProcess: Process

    val outputBufferFile: File = File.createTempFile("buffer", ".txt").also {
        it.deleteOnExit()
    }

    fun initializeClient() {
        if (ready.value == true)
            return

        if (!File(adbPath).exists()) {
            debug("Failed to find ADB server binary at $adbPath")
            return
        }

        debug("Waiting for device to accept connection. This part may take a while.")
        adb(false, listOf("wait-for-device"))?.waitFor()

        debug("Shelling into device")
        val process = adb(true, listOf("shell"))
        if (process == null) {
            debug("Failed to open shell connection")
            return
        }
        shellProcess = process
        ready.postValue(true)

        shellDeathListener()
    }

    private fun shellDeathListener() {
        GlobalScope.launch(Dispatchers.IO) {
            shellProcess.waitFor()
            ready.postValue(false)
            debug("Shell has died")
        }
    }

    fun reset() {
        ready.postValue(false)
        outputBufferFile.writeText("")
        debug("Disconnecting all clients")
        adb(false, listOf("disconnect"))?.waitFor()
        debug("Killing server")
        adb(false, listOf("kill-server"))?.waitFor()
        debug("Clearing pairing memory")
        debug("Erasing all ADB server files")
        context.filesDir.deleteRecursively()
        debug("LADB reset complete, please restart the client")
    }

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

    private fun adb(redirect: Boolean, command: List<String>): Process? {
        val commandList = command.toMutableList().apply {
            add(0, adbPath)
        }
        return shell(redirect, commandList)
    }

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

    fun sendToShellProcess(msg: String) {
        PrintStream(shellProcess.outputStream).apply {
            println(msg)
            flush()
        }
    }

    fun debug(msg: String) {
        outputBufferFile.appendText("DEBUG: $msg" + System.lineSeparator())
    }
}