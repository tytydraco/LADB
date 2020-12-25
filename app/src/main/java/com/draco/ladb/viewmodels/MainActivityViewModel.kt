package com.draco.ladb.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {
    val commandString = MutableLiveData<String>()
    val outputString = MutableLiveData<String>()

    init {
        commandString.value = ""
        outputString.value = ""
    }
}