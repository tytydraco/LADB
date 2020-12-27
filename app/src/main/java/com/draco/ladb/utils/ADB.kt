package com.draco.ladb.utils

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintStream

class ADB(private val context: Context) {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 4
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

    lateinit var shellProcess: Process

    val outputBufferFile: File = File.createTempFile("buffer", ".txt").also {
        it.deleteOnExit()
    }

    fun initializeClient() {
        if (ready.value == true)
            return

        debug("Waiting for device to accept connection. This part may take a while.")
        send(false, "wait-for-device").waitFor()

        debug("Shelling into device")
        shellProcess = send(true, "shell")
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
        send(false, "disconnect").waitFor()
        debug("Killing server")
        send(false, "kill-server").waitFor()
        debug("Clearing pairing memory")
        debug("Erasing all ADB server files")
        context.filesDir.deleteRecursively()
        debug("LADB reset complete, please restart the client.")
    }

    fun pair(port: String, pairingCode: String) {
        val pairShell = send(true, "pair", "localhost:$port")

        /* Sleep to allow shell to catch up */
        Thread.sleep(1000)

        /* Pipe pairing code */
        PrintStream(pairShell.outputStream).apply {
            println(pairingCode)
            flush()
        }

        /* Continue once finished pairing */
        pairShell.waitFor()
    }

    fun send(redirect: Boolean, vararg command: String): Process {
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
                    put("HOME", context.filesDir.path)
                    put("TMPDIR", context.cacheDir.path)
                }
            }
            .start()
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
        sendToAdbShellProcess("sh ${internalScript.absolutePath}")
    }

    fun sendToAdbShellProcess(msg: String) {
        PrintStream(shellProcess.outputStream).apply {
            println(msg)
            flush()
        }
    }

    fun debug(msg: String) {
        outputBufferFile.appendText("DEBUG: $msg" + System.lineSeparator())
    }
}