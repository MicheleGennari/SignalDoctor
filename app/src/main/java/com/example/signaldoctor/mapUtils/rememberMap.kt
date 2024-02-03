package com.example.signaldoctor.mapUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.views.MapView
import com.example.signaldoctor.R


@Composable
fun rememberMap() : MapView {


    val context = LocalContext.current
    val map = remember {
        MapView(context).apply { id = R.id.map }
    }
    val mapObserver = rememberMapLifecycleObserver(mapView = map)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle){
        lifecycle.addObserver(mapObserver)
        onDispose {
            lifecycle.removeObserver(mapObserver)
        }
    }
    return map
}

@Composable
fun rememberMapLifecycleObserver(mapView: MapView) : LifecycleEventObserver {
    return remember(mapView){
        LifecycleEventObserver{ _, event ->
            when(event){
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
    }
}