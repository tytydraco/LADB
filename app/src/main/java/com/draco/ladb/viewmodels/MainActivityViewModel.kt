package com.draco.ladb.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.draco.ladb.utils.ADB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CountDownLatch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    val adbReady = CountDownLatch(1)

    private val outputText = MutableLiveData<String>()
    fun getOutputText(): LiveData<String> = outputText

    private val context = getApplication<Application>().applicationContext
    val adb = ADB(context)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            adb.initializeClient()
            adbReady.countDown()
        }

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val out = readOutputFile(adb.outputBufferFile)
                val currentText = outputText.value
                if (out != currentText)
                    outputText.postValue(out)
                Thread.sleep(ADB.OUTPUT_BUFFER_DELAY_MS)
            }
        }
    }

    private fun readOutputFile(file: File): String {
        val out = ByteArray(ADB.MAX_OUTPUT_BUFFER_SIZE)
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
}