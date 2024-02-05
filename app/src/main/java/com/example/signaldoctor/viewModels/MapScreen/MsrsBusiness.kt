package com.example.signaldoctor.viewModels.MapScreen

import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.AppSettingsManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.utils.Loggers
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.inject.Inject

@ViewModelScoped
class MsrsBusiness @Inject constructor(
    private val msrsRepo: MsrsRepo,
    appSettingsManager: AppSettingsManager,
    locationProvider: FlowLocationProvider
) {

    val userLocation = locationProvider.userLocation

    val appSettings = appSettingsManager.appSettings

    fun getMsrAvgs(viewModelScope : CoroutineScope, msrType: Measure) = appSettings.flatMapLatest { settings ->
            if (settings.networkMode == NetworkMode.ONLINE)
                msrsRepo.run {
                    when(msrType){
                        Measure.wifi -> getWifiMergedAvgs(settings.wifiSettings)
                        Measure.sound -> getSoundMergedAvgs(settings.noiseSettings)
                        Measure.phone -> getPhoneMergedAvgs(settings.phoneSettings)
                    }
                }
            else
                msrsRepo.run {
                    when(msrType){
                        Measure.wifi -> getWifiLocalAvgs(settings.wifiSettings)
                        Measure.sound -> getSoundLocalAvgs(settings.noiseSettings)
                        Measure.phone -> getPhoneLocalAvgs(settings.phoneSettings)
                    }
                }
    }.onStart { Loggers.consoleDebug("$msrType msrsAvgs flow Started") }
        .onCompletion { Loggers.consoleDebug("$msrType flow ended") }
            .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = MsrsMap())


    fun areMsrsDated(
        viewModelScope: CoroutineScope,
        msrType: Measure,
        extraWork : (Flow<Boolean>.() -> Flow<Boolean>)? = null
    ) : StateFlow<Boolean> {
        val baseFlow = combineTransform(
            appSettings.map { it.networkMode }.distinctUntilChanged(),
            userLocation.filterNotNull()
        ) { networkMode, userLocation ->
            emitAll(
                if (networkMode == NetworkMode.ONLINE) {
                    msrsRepo.countMergedMeasurements(
                        msrType,
                        userLocation,
                        Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
                    )
                } else {
                    msrsRepo.countLocalMeasurements(
                        msrType,
                        userLocation,
                        Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli())
                    )
                }
            )
        }

        return (if(extraWork == null) baseFlow else baseFlow.extraWork()).stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = Duration.ofMinutes(5).toMillis()
            ),
            initialValue = false
        )

    }

}