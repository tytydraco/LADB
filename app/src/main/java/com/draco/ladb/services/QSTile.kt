package com.draco.ladb.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.draco.ladb.views.MainActivity

class QSTile : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // code added to not cause avoid deprecated method for newer android
        // and to avoid UnsupportedOperationException
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // ---------------------------------REVIEW NEEDED-----------------------------
            // pretty sure it should be getService call otherwise it should be getActivity
            PendingIntent.getService(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT
                )
        }else{
            // if sdk_int is less than UPSIDE_DOWN_CAKE
            startActivityAndCollapse(intent)
        }
        
    }
}