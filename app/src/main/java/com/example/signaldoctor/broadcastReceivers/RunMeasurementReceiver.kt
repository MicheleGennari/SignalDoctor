package com.example.signaldoctor.broadcastReceivers

import android.app.usage.UsageEvents.Event
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MsrsWorkManager
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.room.MeasurementBase
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.security.Permission
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp


const val RUN_MEASUREMENT_ACTION = "RUN_MEASUREMENT_ACTION"
@AndroidEntryPoint
class RunMeasurementReceiver @Inject constructor(
    //private val onReceiveOverride : (context : Context?, intent : Intent?) -> Unit,
) : BroadcastReceiver() {


    @Inject  lateinit var workManager: MsrsWorkManager
    @Inject  lateinit var gson: Gson
    val coroutineScope : CoroutineScope = CoroutineScope(Dispatchers.Default)
    override fun onReceive(context: Context?, intent: Intent?) {
        if((intent != null) && (intent.action == RUN_MEASUREMENT_ACTION)){

            val msrType = try {
                gson.fromJson(
                    intent.getStringExtra(MeasurementBase.MSR_TYPE_KEY),
                    Measure::class.java
                ) ?: return
            } catch(e : JsonSyntaxException){
                e.printStackTrace()
                return
            }

            if(
                msrType == Measure.sound
                &&
                context?.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) return

            coroutineScope.launch(Dispatchers.IO){
                workManager.runMeasurement(msrType = msrType)
            }

        }
    }
}


//this was an attempt to remember and register the receiver in composables, not usable yet
@Composable
fun BroadCastReceiver(
    onReceiveOverride : (context : Context?, intent : Intent?) -> Unit,
    intentFilter: IntentFilter,
    exported : Boolean
){
    val applicationContext = LocalContext.current.applicationContext
    val receiver = remember {
        object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                onReceiveOverride(context, intent)
            }

        }
    }

    DisposableEffect(receiver){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            ContextCompat.registerReceiver(
                applicationContext,
                receiver,
                intentFilter,
                if(exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            applicationContext.registerReceiver(
                receiver,
                IntentFilter(
                    IntentFilter(RUN_MEASUREMENT_ACTION)
                )
            )
        }

        onDispose {

        }
    }
}
