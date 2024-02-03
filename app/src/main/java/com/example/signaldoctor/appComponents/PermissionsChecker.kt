package com.example.signaldoctor.appComponents

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.lifecycle.LifecycleOwner
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.contracts.Measure
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsChecker @Inject constructor(
     @ApplicationContext private val ctx : Context,
     private val settingsDataStore: DataStore<AppSettings>,
     val locationProvider: FlowLocationProvider
) {

    fun isRecordingGranted() =
        ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isLocationGranted()=
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ||
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isPostingNotificationGranted()=
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU)
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else
            true


    fun isBaseMeasurementPermitted() = isLocationGranted() && isPostingNotificationGranted()

    fun isNoiseMeasurementPermitted() = isBaseMeasurementPermitted() && isRecordingGranted()

}




fun Context.isLocationPermissionGranted() =
    checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ||
    checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED