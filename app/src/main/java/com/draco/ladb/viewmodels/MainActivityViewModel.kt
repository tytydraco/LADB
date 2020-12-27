package com.draco.ladb.viewmodels

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.draco.ladb.utils.ADB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val adbReady = MutableLiveData<Boolean>()
    fun getAdbReady(): LiveData<Boolean> = adbReady

    private val outputText = MutableLiveData<String>()
    fun getOutputText(): LiveData<String> = outputText

    private val context = getApplication<Application>().applicationContext
    val adb = ADB(context)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            adb.initializeClient()
            adbReady.postValue(true)
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

    fun shouldWePair(sharedPreferences: SharedPreferences): Boolean {
        with (sharedPreferences) {
            if (!getBoolean("paired", false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    return true
            }
        }

        return false
    }

    fun getScriptFromIntent(intent: Intent): String? {
        return when (intent.type) {
            "text/x-sh" -> {
                val uri = Uri.parse(intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM).toString())
                context.contentResolver.openInputStream(uri)?.bufferedReader().use {
                    it?.readText()
                }
            }
            "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
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