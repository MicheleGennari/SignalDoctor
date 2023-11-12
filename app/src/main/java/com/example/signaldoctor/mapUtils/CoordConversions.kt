package com.example.signaldoctor.mapUtils

import android.location.Location
import android.service.quicksettings.Tile
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView

object CoordConversions {


    fun TileSystem.tileIndexFromLocation(location : Location) = MapTileIndex.getTileIndex(

        TileSystem.getMaximumZoomLevel(),
        getTileXFromLongitude(location.longitude, TileSystem.getMaximumZoomLevel()),
        getTileYFromLatitude(location.latitude, TileSystem.getMaximumZoomLevel())
    )

}


