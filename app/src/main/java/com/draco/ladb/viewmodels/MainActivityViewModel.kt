package com.draco.ladb.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {
    private val commandString = MutableLiveData<String>()
    fun getCommandString(): LiveData<String> = commandString

    private val outputString = MutableLiveData<String>()
    fun getOutputString(): LiveData<String> = outputString

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