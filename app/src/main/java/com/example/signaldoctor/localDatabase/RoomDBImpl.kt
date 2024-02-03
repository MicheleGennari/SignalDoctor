package com.example.signaldoctor.localDatabase

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.room.MsrsDB
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.PhoneMeasurementDAO
import com.example.signaldoctor.room.RoomMeasurementDAO
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.whenMsrType
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.google.firebase.database.GenericTypeIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDBImpl @Inject constructor(
    private val msrsDB : MsrsDB
) : IMsrsLocalDB {


     override fun avgsMap(msrType: Measure, settings: MeasurementSettings) : Flow<MsrsMap> {
        return when(msrType){
            Measure.wifi-> msrsDB.wifiMeasurementDAO().getMsrsAvgs()
            Measure.sound-> msrsDB.soundMeasurementDAO().getMsrsAvgs()
            Measure.phone-> msrsDB.phoneMeasurementDAO().getMsrsAvgs()
        }.flowOn(Dispatchers.IO).map { it.toMap(MsrsMap()) }

    }

    override fun getPhoneMsrs(settings: MeasurementSettings): Flow<Map<String,PhoneMeasurement>> =
        msrsDB.phoneMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)

    override fun getSoundMsrs(settings: MeasurementSettings): Flow<Map<String,SoundMeasurement>> =
        msrsDB.soundMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)

    override fun getWifiMsrs(settings: MeasurementSettings): Flow<Map<String,WiFIMeasurement>> =
        msrsDB.wifiMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)

    override suspend fun postPhoneMsr(phoneMeasurement: PhoneMeasurement): Boolean {
        msrsDB.phoneMeasurementDAO().postMsr(phoneMeasurement)
        return true
    }

    override suspend fun postSoundMsr(soundMeasurement: SoundMeasurement) : Boolean{
        msrsDB.soundMeasurementDAO().postMsr(soundMeasurement)
        return true
    }

    override suspend fun postWifiMsr(wifiMeasurement: WiFIMeasurement) : Boolean {
        msrsDB.wifiMeasurementDAO().postMsr(wifiMeasurement)
        return true
    }

    override fun areMsrsDated(msrType: Measure, userLocation : Location, limitDate : Date) : Flow<Boolean> {

        val currentTileIndex = MapView.getTileSystem().tileIndexFromLocation(userLocation)

        return when(msrType){
            Measure.phone -> msrsDB.phoneMeasurementDAO().countMeasures(currentTileIndex, limitDate)
            Measure.wifi -> msrsDB.wifiMeasurementDAO().countMeasures(currentTileIndex,limitDate)
            Measure.sound -> msrsDB.soundMeasurementDAO().countMeasures(currentTileIndex, limitDate)
        }.flowOn(Dispatchers.IO).map { count ->
            count <1
        }
    }


    override fun getOldestDate(msrType: Measure) = msrsDB.run {
        whenMsrType(msrType,
            phone = phoneMeasurementDAO().getOldestDate(),
            sound = soundMeasurementDAO().getOldestDate(),
            wifi = wifiMeasurementDAO().getOldestDate()
            ).flowOn(Dispatchers.IO).map { SimpleDateFormat.getDateTimeInstance().parse(it) }
    }


    override fun close() {
         msrsDB.clearAllTables()
    }

    override fun clearAllTables() {
        msrsDB.clearAllTables()
        consoleDebug("Database rows cancelled")
    }


}