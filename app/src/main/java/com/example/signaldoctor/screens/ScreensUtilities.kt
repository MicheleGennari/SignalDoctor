package com.example.signaldoctor.screens

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.utils.Loggers
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
fun MultiplePermissionsState.checkPermissions(locationSettingsRequest : (() -> Unit)? = null) {

    val isLocationRequestPresent = permissions.any {
        it.permission == android.Manifest.permission.ACCESS_FINE_LOCATION ||
        it.permission == android.Manifest.permission.ACCESS_COARSE_LOCATION
    }

    locationSettingsRequest.apply {
        if (!allPermissionsGranted)
            launchMultiplePermissionRequest()
        else if(locationSettingsRequest != null && isLocationRequestPresent)
            locationSettingsRequest()
    }
}
@OptIn(ExperimentalPermissionsApi::class)
fun MultiplePermissionsState.askRunMeasurementPermission(mainActivity : MainActivity) {


    if (!allPermissionsGranted)
        launchMultiplePermissionRequest()

    val isLocationPermissionGranted = permissions.any { permissionState ->
        permissionState.permission == android.Manifest.permission.ACCESS_FINE_LOCATION && permissionState.status.isGranted
    }


}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberLocationPermissionState(onResult : ((Boolean) -> Unit)? = {}) = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION, onResult ?: {})

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberRecordPermissionState(onResult : ((Boolean) -> Unit)? = {}) = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO, onResult ?: {})
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberSendNotificationsPermissionState(onResult : ((Boolean) -> Unit)? = {}) = rememberPermissionState(permission =
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.POST_NOTIFICATIONS
    else
        Manifest.permission.ACCESS_WIFI_STATE
, onResult ?: {})

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberBaseMeasurementsPermissionState(onResult : ((Map<String, Boolean>) -> Unit)? = {}) = rememberMultiplePermissionsState(permissions = listOfNotNull(
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.POST_NOTIFICATIONS
    else null,
    Manifest.permission.ACCESS_FINE_LOCATION,
), onResult ?: {})
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberNoiseMeasurementPermissionState(onResult : ((Map<String, Boolean>) -> Unit)? = {}) = rememberMultiplePermissionsState(permissions = listOfNotNull(
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.POST_NOTIFICATIONS
    else null,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.RECORD_AUDIO
), onResult ?: {})