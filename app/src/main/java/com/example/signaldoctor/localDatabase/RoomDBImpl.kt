package com.example.signaldoctor.localDatabase

import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.room.MsrsDB
import com.example.signaldoctor.bin.MsrsMapEntry
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.example.signaldoctor.workers.MsrWorkersInputData
import kotlinx.coroutines.flow.Flow
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDBImpl @Inject constructor(
     private val msrsDB : MsrsDB
) : IMsrsLocalDB {


    override fun getMsrsAvgs(msrType: Measure) : Flow<MsrsMap> {
        TODO()
        /*
        return when(msrType){
            Measure.wifi-> msrsDB.wifiMeasurementDAO().getMsrsAvgs()
            Measure.sound-> msrsDB.soundMeasurementDAO().getMsrsAvgs()
            Measure.phone-> msrsDB.phoneMeasurementDAO().getMsrsAvgs()
        }
        */

    }
    override suspend fun postMsr(msr: Int, mapTileData: MsrWorkersInputData): Boolean {

        val tileSystem = MapView.getTileSystem()
        val maxZoom = TileSystem.getMaximumZoomLevel()
        val baseData = MeasurementBase(
            tileIndex = MapTileIndex.getTileIndex(
                maxZoom,
                tileSystem.getTileXFromLongitude(mapTileData.long, maxZoom),
                tileSystem.getTileYFromLatitude(mapTileData.lat, maxZoom)
            ),
            value = msr
        )

        when(mapTileData.msrType){
            Measure.phone.name -> consoledebug("${
                msrsDB.phoneMeasurementDAO().postMsr(
                    measurement = PhoneMeasurement( FibaseMeasurementEntity = baseData)
                )
            }")
            Measure.sound.name -> msrsDB.soundMeasurementDAO().postMsr(
                measurement = SoundMeasurement( baseMeasurementEntity = baseData)
            )
            Measure.wifi.name -> msrsDB.wifiMeasurementDAO().postMsr(
                measurement = WiFIMeasurement( baseMeasurementEntity = baseData)
            )
            else -> {}
        }
        return true
    }

    override fun close() {
         msrsDB.clearAllTables()
    }

    override fun clearAllTables() {
        msrsDB.clearAllTables()
    }

}