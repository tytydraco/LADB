package com.draco.ladb.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.draco.ladb.BuildConfig
import com.draco.ladb.R
import com.draco.ladb.utils.ADB
import com.draco.ladb.utils.DnsDiscover
import com.github.javiersantos.piracychecker.PiracyChecker
import com.github.javiersantos.piracychecker.piracyChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _outputText = MutableLiveData<String>()
    val outputText: LiveData<String> = _outputText

    val isPairing = MutableLiveData<Boolean>()

    private var checker: PiracyChecker? = null
    private val sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(application.applicationContext)

    val adb = ADB.getInstance(getApplication<Application>().applicationContext)
    val dnsDiscover =
        DnsDiscover.getInstance(
            application.applicationContext,
            application.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        )

    private val _viewModelHasStartedADB = MutableLiveData(false)
    val viewModelHasStartedADB: LiveData<Boolean> = _viewModelHasStartedADB

    init {
        startOutputThread()
        dnsDiscover.scanAdbPorts()
    }


    fun startADBServer(callback: ((Boolean) -> (Unit))? = null) {
        // Don't start if it's already started.
        if (_viewModelHasStartedADB.value == true || adb.running.value == true) return

        viewModelScope.launch(Dispatchers.IO) {
            val success = adb.initServer()
            if (success) {
                startShellDeathThread()
                _viewModelHasStartedADB.postValue(true)
            }
            callback?.invoke(success)
        }
    }

    /**
     * Start the piracy checker if it is not setup yet (release builds only)
     *
     * @param activity Activity to use when showing the error
     */
    fun piracyCheck(activity: Activity) {
        if (checker != null || !BuildConfig.ANTI_PIRACY)
            return

        val context = getApplication<Application>().applicationContext

        checker = activity.piracyChecker {
            enableGooglePlayLicensing("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoRTOoEZ/IFfA/JkBFIrZqLq7N66JtJFTn/5C2QMO2EIY6hG4yZ5YTA3JrbJuuGVzQE8j29s6Lwu+19KKZcITTkZjfgl2Zku8dWQKZFt46f7mh8s1spzzc6rmSWIBPZUxN6fIIz8ar+wzyZdu3z+Iiy31dUa11Pyh82oOsWH7514AYGeIDDlvB1vSfNF/9ycEqTv5UAOgHxqZ205C1VVydJyCEwWWVJtQ+Z5zRaocI6NGaYRopyZteCEdKkBsZ69vohk4zr2SpllM5+PKb1yM7zfsiFZZanp4JWDJ3jRjEHC4s66elWG45yQi+KvWRDR25MPXhdQ9+DMfF2Ao1NTrgQIDAQAB")
            saveResultToSharedPreferences(
                sharedPreferences,
                context.getString(R.string.pref_key_verified)
            )
        }

        val verified = sharedPreferences.getBoolean(context.getString(R.string.pref_key_verified), false)
        if (!verified)
            checker?.start()
    }

    /**
     * Continuously update shell output
     */
    private fun startOutputThread() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val out = readOutputFile(adb.outputBufferFile)
                val currentText = _outputText.value
                if (out != currentText)
                    _outputText.postValue(out)
                Thread.sleep(ADB.OUTPUT_BUFFER_DELAY_MS)
            }
        }
    }

    /**
     * Start a death listener to restart the shell once it dies
     */
    private fun startShellDeathThread() {
        viewModelScope.launch(Dispatchers.IO) {
            adb.waitForDeathAndReset()
        }
    }

    /**
     * Erase all shell text
     */
    fun clearOutputText() {
        adb.outputBufferFile.writeText("")
    }

    /**
     * Check if the user should be prompted to pair
     */
    fun needsToPair(): Boolean {
        val context = getApplication<Application>().applicationContext

        return !sharedPreferences.getBoolean(context.getString(R.string.paired_key), false) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    }

    fun setPairedBefore(value: Boolean) {
        val context = getApplication<Application>().applicationContext
        sharedPreferences.edit {
            putBoolean(context.getString(R.string.paired_key), value)
        }
    }

    /**
     * Read the content of the ABD output file
     */
    private fun readOutputFile(file: File): String {
        val out = ByteArray(adb.getOutputBufferSize())

        synchronized(file) {
            if (!file.exists())
                return ""

            file.inputStream().use {
                val size = it.channel.size()

                if (size <= out.size)
                    return String(it.readBytes())

                val newPos = (it.channel.size() - out.size)
                it.channel.position(newPos)
                it.read(out)
            }
        }

        return String(out)
    }
}