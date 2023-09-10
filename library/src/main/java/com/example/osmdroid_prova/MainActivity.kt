package com.example.osmdroid_prova

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView


class MainActivity : AppCompatActivity() {
    private lateinit var map : MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_main)
        map = findViewById<MapView>(R.id.map)
        map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            overlays.apply {
                    add(com.example.signaldoctor.SquaredZonesOverlay(this@MainActivity))
            }
        }
        val startPoint = GeoPoint(48.8583, 2.2944);
        val controller = map.controller
        controller.setZoom(9.5)
        controller.setCenter(startPoint)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    fun boundingBox(builder : BoundingBox.() -> Unit) : BoundingBox{
        val bbox = BoundingBox()
        bbox.builder()
        return bbox
    }

    fun tile2lon(x: Int, z: Int): Double {
        return x / Math.pow(2.0, z.toDouble()) * 360.0 - 180
    }

    fun tile2lat(y: Int, z: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y / Math.pow(2.0, z.toDouble())
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }
}