package com.example.signaldoctor.localDatabase

import android.location.Location
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.room.MsrsDB
import com.example.signaldoctor.mapUtils.CoordConversions.tileIndexFromLocation
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.RoomMeasurementEntity
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.screens.whenMsrType
import com.example.signaldoctor.utils.Loggers.consoledebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.osmdroid.views.MapView
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDBImpl @Inject constructor(
     private val msrsDB : MsrsDB
) : IMsrsLocalDB {


     override fun avgsMap(msrType: Measure, settings: MeasurementSettings) : Flow<MsrsMap> {

        return when(msrType){
            Measure.wifi-> msrsDB.wifiMeasurementDAO().getMsrsAvgs().map { it.toMap(MsrsMap()) }
            Measure.sound-> msrsDB.soundMeasurementDAO().getMsrsAvgs().map { it.toMap(MsrsMap()) }
            Measure.phone-> msrsDB.phoneMeasurementDAO().getMsrsAvgs().map { it.toMap(MsrsMap()) }
        }

    }

    override fun getPhoneMsrs(settings: MeasurementSettings): Flow<List<PhoneMeasurement?>> {
        return msrsDB.phoneMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)
    }

    override fun getSoundMsrs(settings: MeasurementSettings): Flow<List<SoundMeasurement?>> {
        return msrsDB.soundMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)
    }

    override fun getWifiMsrs(settings: MeasurementSettings): Flow<List<WiFIMeasurement?>> {
        return msrsDB.wifiMeasurementDAO().getMsrs().flowOn(Dispatchers.IO)
    }

    override fun postPhoneMsr(phoneMeasurement: PhoneMeasurement): Boolean {
        msrsDB.phoneMeasurementDAO().postMsr(phoneMeasurement)
        return true
    }

    override fun postSoundMsr(soundMeasurement: SoundMeasurement) : Boolean{
        msrsDB.soundMeasurementDAO().postMsr(soundMeasurement)
        return true
    }

    override fun postWifiMsr(wifiMeasurement: WiFIMeasurement) : Boolean {
        msrsDB.wifiMeasurementDAO().postMsr(wifiMeasurement)
        return true
    }

    override fun countMeasurements(msrType: Measure, userLocation : Location, limitDate : Date) : Flow<Boolean> {

        val currentTileIndex = MapView.getTileSystem().tileIndexFromLocation(userLocation)

        return when(msrType){
            Measure.phone -> msrsDB.phoneMeasurementDAO().countMeasures(currentTileIndex, limitDate)
            Measure.wifi -> msrsDB.wifiMeasurementDAO().countMeasures(currentTileIndex,limitDate)
            Measure.sound -> msrsDB.soundMeasurementDAO().countMeasures(currentTileIndex, limitDate)
        }.map { count ->
            count <1
        }
    }


    override fun getOldestDate(msrType: Measure) = msrsDB.run {
        whenMsrType(msrType,
            phone = phoneMeasurementDAO().getOldestDate(),
            sound = soundMeasurementDAO().getOldestDate(),
            wifi = wifiMeasurementDAO().getOldestDate()
            )
    }


    override fun close() {
         msrsDB.clearAllTables()
    }

    override fun clearAllTables() {
        msrsDB.clearAllTables()
        consoledebug("Database rows cancelled")
    }

}