package com.draco.ladb.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.CountDownLatch

class MainActivityViewModel : ViewModel() {
    private val commandString = MutableLiveData<String>()
    fun getCommandString(): LiveData<String> = commandString

    private val outputString = MutableLiveData<String>()
    fun getOutputString(): LiveData<String> = outputString

    var pairingLatch = CountDownLatch(0)
    fun setPairing() {
        pairingLatch = CountDownLatch(1)
    }
    fun donePairing() {
        pairingLatch.countDown()
    }

    fun setCommandString(string: String) {
        commandString.value = string
    }

    fun setOutputString(string: String) {
        outputString.value = string
    }

    init {
        commandString.value = ""
        outputString.value = ""
    }
}