package com.example.signaldoctor.appComponents

import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.hiltModules.AppCoroutineScope
import com.example.signaldoctor.utils.settingsAsSharedFlow
import com.example.signaldoctor.utils.updateDSL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsManager @Inject constructor(
    private val settingsDataStore : DataStore<AppSettings>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {

    val appSettings = settingsDataStore.data.flowOn(Dispatchers.IO)
        .stateIn(appCoroutineScope, SharingStarted.WhileSubscribed(2000), AppSettings.getDefaultInstance())

    suspend fun updateSettings(updater : AppSettings.Builder.() -> Unit) = settingsDataStore.updateDSL(updater)

}