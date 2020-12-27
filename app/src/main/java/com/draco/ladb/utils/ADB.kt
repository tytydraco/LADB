package com.draco.ladb.utils

import android.content.Context
import java.io.File
import java.io.PrintStream

class ADB(private val context: Context) {
    companion object {
        const val MAX_OUTPUT_BUFFER_SIZE = 1024 * 4
        const val OUTPUT_BUFFER_DELAY_MS = 100L
    }

    private val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"

    lateinit var shellProcess: Process

    val outputBufferFile: File = File.createTempFile("buffer", ".txt").also {
        it.deleteOnExit()
    }

    fun initializeClient() {
        debug("Waiting for device to accept connection. This part may take a while.")
        send(false, "wait-for-device").waitFor()

        debug("Shelling into device")
        shellProcess = send(true, "shell")
    }

    fun reset() {
        outputBufferFile.writeText("")
        debug("Disconnecting all clients")
        send(false, "disconnect").waitFor()
        debug("Killing server")
        send(false, "kill-server").waitFor()
        debug("Clearing pairing memory")
        debug("Erasing all ADB server files")
        context.filesDir.deleteRecursively()
        debug("LADB reset complete!")
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