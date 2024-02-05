package com.example.signaldoctor.viewModels

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.utils.msrTypeWhenSuspend
import com.example.signaldoctor.utils.whenMsrType
import com.example.signaldoctor.services.BackgroundMeasurementsManager
import com.example.signaldoctor.services.BackgroundMeasurementsService
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.asSharedFlow
import com.example.signaldoctor.utils.catchIOException
import com.example.signaldoctor.utils.noiseSettings
import com.example.signaldoctor.utils.phoneSettings
import com.example.signaldoctor.utils.settingAsStateFlow
import com.example.signaldoctor.utils.settingsAsFlow
import com.example.signaldoctor.utils.updateDSL
import com.example.signaldoctor.utils.wifiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject


@HiltViewModel
class SettingsScreenVM @Inject constructor(
    private val settingsDataStore: DataStore<AppSettings>,
    private val backgroundMeasurementsManager : BackgroundMeasurementsManager,
    private val locationProvider: FlowLocationProvider,
    private val permissionsChecker: PermissionsChecker
) : ViewModel() {


    private val _isUserLocationAvailable = MutableStateFlow(false)
    private var userLocationJob : Job = Job()
    val isUserLocationAvailable = _isUserLocationAvailable.asStateFlow()

    fun locationUpdatesOn() {
            userLocationJob = locationProvider.requestLocationUpdates().onEach { newLocation ->
                //consoleDebug("SettingsScreenVM location updates are on")
                _isUserLocationAvailable.update { newLocation != null }
            }.onCompletion { e ->
                _isUserLocationAvailable.update { false }
                consoleDebug("user location flow ended")
                e?.printStackTrace()
            }.launchIn(viewModelScope)
    }

    fun locationUpdatesOff(){
        if(userLocationJob.job.isActive)
            userLocationJob.cancel()
    }

    /*
    private val noiseBackgroundMsrManager = startBackgroundMeasurementsManager(Measure.sound)
    private val phoneBackgroundMsrManager = startBackgroundMeasurementsManager(Measure.phone)
    private val wifiBackgroundMsrManager = startBackgroundMeasurementsManager(Measure.wifi)
    */





    val appSettings = settingsDataStore.asSharedFlow(viewModelScope)


    val noiseSettings = appSettings.settingAsStateFlow(viewModelScope,initialValue = MeasurementSettings.getDefaultInstance()){
        noiseSettings
    }
    /*
    val noiseUseMsrsToTake = appSettings.settingAsStateFlow(initialValue = false){ noiseSettings.useMsrsToTake }
    val noiseMsrsToTake = appSettings.settingAsStateFlow(initialValue = Int){noiseSettings.msrsToTake}
    val noiseIsBackgroundMsrOn = appSettings.settingAsStateFlow(initialValue = false){ noiseSettings.isBackgroundMsrOn}
    val noisePeriodicity = appSettings.settingAsStateFlow(initialValue =  15){noiseSettings.periodicity}
    val noiseFreshness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ noiseSettings.freshness}
    val noiseOldness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ noiseSettings.oldness}
    */


    val phoneSettings = appSettings.settingAsStateFlow(viewModelScope,initialValue = MeasurementSettings.getDefaultInstance()){
        phoneSettings
    }
    /*
    val phoneUseMsrsToTake = appSettings.settingAsStateFlow(initialValue = false){ phoneSettings.useMsrsToTake }
    val phoneMsrsToTake = appSettings.settingAsStateFlow(initialValue = Int){phoneSettings.msrsToTake}
    val phoneIsBackgroundMsrOn = appSettings.settingAsStateFlow(initialValue = false){ phoneSettings.isBackgroundMsrOn}
    val phonePeriodicity = appSettings.settingAsStateFlow(initialValue =  15){phoneSettings.periodicity}
    val phoneFreshness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ phoneSettings.freshness}
    val phoneOldness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ phoneSettings.oldness}
    */

    val wifiSettings = appSettings.settingAsStateFlow(viewModelScope,initialValue = MeasurementSettings.getDefaultInstance()){
        wifiSettings
    }
    /*
    val wifiUseMsrsToTake = appSettings.settingAsStateFlow(initialValue = false){ wifiSettings.useMsrsToTake }
    val wifiMsrsToTake = appSettings.settingAsStateFlow(initialValue = Int){wifiSettings.msrsToTake}
    val wifiIsBackgroundMsrOn = appSettings.settingAsStateFlow(initialValue = false){ wifiSettings.isBackgroundMsrOn}
    val wifiPeriodicity = appSettings.settingAsStateFlow(initialValue =  15){wifiSettings.periodicity}
    val wifiFreshness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ wifiSettings.freshness}
    val wifiOldness = appSettings.settingAsStateFlow(initialValue = Instant.now()){ wifiSettings.oldness}
    */

    val isPhoneBackgroundOn = appSettings.settingsAsFlow { phoneSettings.isBackgroundMsrOn }.map{
        permissionsChecker.isBaseMeasurementPermitted()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())
    val phonePeriodicity = appSettings.settingsAsFlow { phoneSettings.periodicity }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val isNoiseBackgroundOn = appSettings.settingsAsFlow { noiseSettings.isBackgroundMsrOn }.map{
        permissionsChecker.isNoiseMeasurementPermitted()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())
    val noisePeriodicity = appSettings.settingsAsFlow { noiseSettings.periodicity }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val isWifiBackgroundOn = appSettings.settingsAsFlow { wifiSettings.periodicity }.map{
        permissionsChecker.isBaseMeasurementPermitted()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())
    val wifiPeriodicity = appSettings.settingsAsFlow { wifiSettings.periodicity }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())


    fun phoneManager() = combine(isPhoneBackgroundOn, phonePeriodicity, isUserLocationAvailable){ isBackgroundOn, periodicity, isUserLocationAvailable ->
        backgroundMeasurementsManager.start(Measure.phone, periodicity)
        if(isBackgroundOn and isUserLocationAvailable) backgroundMeasurementsManager.start(Measure.phone, periodicity)
        else backgroundMeasurementsManager.stop(Measure.phone)
    } .launchIn(viewModelScope)

    fun noiseManager() = combine(isNoiseBackgroundOn, noisePeriodicity, isUserLocationAvailable){ isBackgroundOn, periodicity, isUserLocationAvailable ->
        if(isBackgroundOn and isUserLocationAvailable) backgroundMeasurementsManager.start(Measure.sound, periodicity)
        else backgroundMeasurementsManager.stop(Measure.sound)
    } .launchIn(viewModelScope)

    fun wifiManager() = combine(isWifiBackgroundOn, wifiPeriodicity, isUserLocationAvailable){ isBackgroundOn, periodicity, isUserLocationAvailable ->
        if(isBackgroundOn and isUserLocationAvailable) backgroundMeasurementsManager.start(Measure.wifi, periodicity)
        else backgroundMeasurementsManager.stop(Measure.wifi)
    } .launchIn(viewModelScope)


    private val _currentSettingsList = MutableStateFlow(Measure.sound)
    val currentSettingsList = _currentSettingsList.asStateFlow()

    fun changeCurrentSettingsList(msrType: Measure){
        _currentSettingsList.value = msrType
    }

    fun updateSettings(updater : suspend AppSettings.Builder.() -> Unit){

        viewModelScope.launch {
            settingsDataStore.updateDSL(updater)
        }
        /*
        viewModelScope.launch {
            settingsDataStore.updateData { oldSettingsSnap ->
                oldSettingsSnap.updateAppSettings(updater)
            }
        }.invokeOnCompletion { e : Throwable? ->
            e?.printStackTrace()
        }
         */
    }

      fun updateMeasureSettings(msrType : Measure, updater : suspend MeasurementSettings.Builder.() -> Unit) {

        updateSettings {
            msrTypeWhenSuspend(msrType,
                phone = { phoneSettings(updater)},
                sound = { noiseSettings(updater)},
                wifi = { wifiSettings(updater)}

            )
        }

    }

    fun updateNoiseSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.sound, updater = updater)
    }

    fun updatePhoneSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.phone, updater = updater)
    }

    fun updateWifiSettings(updater: MeasurementSettings.Builder.() -> Unit){
        updateMeasureSettings(msrType = Measure.wifi, updater = updater)
    }

    //value in this stateFlow tells whether service is active or not
    private fun startBackgroundMeasurementsManager(msrType: Measure) =
            settingsDataStore.shouldRunBackGroundMeasurement(msrType){
                permissionsChecker.run { if(msrType == Measure.sound) isNoiseMeasurementPermitted() else isBaseMeasurementPermitted() } &&
                locationProvider.getCurrentLocation(FlowLocationProvider.defaultLocationUpdateSettings.priority) != null
            }.onEach{ isBackgroundMsrOn ->

            if(isBackgroundMsrOn)
                backgroundMeasurementsManager.start(BackgroundMeasurementsService.run {
                    when (msrType) {
                        Measure.sound -> START_NOISE_ACTION
                        Measure.phone -> START_PHONE_ACTION
                        Measure.wifi -> START_WIFI_ACTION
                    }
                })

        }.catch {
            consoleDebug("ERROR DURING ON BACKGROUND MEASUREMENT FLOW!!!!")
            it.printStackTrace()
        }.shareIn(viewModelScope, SharingStarted.Eagerly)


    fun checkLocationSettings(mainActivity: MainActivity){
        viewModelScope.launch{
            locationProvider.checkLocationSettings(FlowLocationProvider.defaultLocationUpdateSettings, mainActivity)
        }
    }

    private fun DataStore<AppSettings>.shouldRunBackGroundMeasurement(msrType: Measure, condition : suspend AppSettings.() -> Boolean) =
        data.catchIOException().map { appSettings ->
            appSettings.run {

                whenMsrType(msrType,
                    phone = phoneSettings,
                    sound = noiseSettings,
                    wifi = wifiSettings
                ).isBackgroundMsrOn.takeIf { condition() } ?: false

            }
        }.conflate().distinctUntilChanged().onEach {
            consoleDebug("$msrType isBackGroundOn is flowing")
        }


    override fun onCleared() {
        consoleDebug("the Settings Screen ViewModel is now cleared")
        super.onCleared()
    }

    init {

        consoleDebug("A Settings Screen ViewModel is now created")

    }
}

