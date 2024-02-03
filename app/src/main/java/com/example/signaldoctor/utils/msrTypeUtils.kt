package com.example.signaldoctor.utils

import android.content.pm.ServiceInfo
import android.os.Build
import com.example.signaldoctor.R
import com.example.signaldoctor.contracts.Measure

fun <T> whenMsrType(
    currentMsrType : Measure,
    phone : T,
    sound : T,
    wifi : T
) : T {
    return when(currentMsrType){
        Measure.sound -> sound
        Measure.wifi -> wifi
        Measure.phone -> phone
    }
}

fun <T> T.msrTypeWhen(
    currentMsrType: Measure,
    phone : T.() -> Unit,
    sound : T.() -> Unit,
    wifi : T.() -> Unit
)  {
    return when(currentMsrType){
        Measure.phone -> phone()
        Measure.wifi -> wifi()
        Measure.sound -> sound()
    }
}

fun <T,R> T.msrTypeWhen(
    currentMsrType: Measure,
    phone : T.() -> R,
    sound : T.() -> R,
    wifi : T.() -> R
) : R {
    return when(currentMsrType){
        Measure.phone -> phone()
        Measure.wifi -> wifi()
        Measure.sound -> sound()
    }
}

fun msrTypeWhen(
    currentMsrType: Measure,
    phone : () -> Unit,
    sound : () -> Unit,
    wifi : () -> Unit
){
    when(currentMsrType){
        Measure.phone -> phone()
        Measure.wifi -> wifi()
        Measure.sound -> sound()
    }
}

suspend fun msrTypeWhenSuspend(
    currentMsrType: Measure,
    phone : suspend  () -> Unit,
    sound : suspend () -> Unit,
    wifi : suspend () -> Unit
){
    when(currentMsrType){
        Measure.phone -> phone()
        Measure.wifi -> wifi()
        Measure.sound -> sound()
    }
}

fun getMsrTypeSmallIcon(msrType : Measure) = whenMsrType(msrType,
    phone = R.drawable.phone_icon_notification_bitmap,
    sound = R.drawable.ear_icon_notification_bitmap,
    wifi = R.drawable.wifi_icon_notification_bitmap
)

fun getServiceContentTitle(msrType: Measure) = whenMsrType(msrType,
    phone = R.string.phone_measurement_notification_content_title,
    sound = R.string.noise_measurement_notification_content_title,
    wifi = R.string.wifi_measurement_notification_content_title
)

fun getServiceType(msrType: Measure) : Int {

    val locationType = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    return whenMsrType(
        msrType,
        phone = locationType,
        wifi = locationType,
        sound = locationType +
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
}