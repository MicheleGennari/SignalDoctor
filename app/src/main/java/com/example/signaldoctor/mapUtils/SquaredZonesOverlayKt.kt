package com.example.signaldoctor.mapUtils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.annotation.ColorInt
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.repositories.MsrsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapView
import org.osmdroid.config.Configuration
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.RectL
import org.osmdroid.util.TileLooper
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import javax.inject.Inject
import kotlin.properties.Delegates

class SquaredZonesOverlayKt @JvmOverloads constructor(
    private var ctx: Context?,
    horizontalWrapEnabled: Boolean = true,
    verticalWrapEnabled: Boolean = true,
    private val msrType : Measure
) :
    Overlay() {
    /**
     * Current tile source
     */
    protected val mDebugPaint = Paint()
    private val mTileRect = Rect()
    protected val mViewPort = RectL()
    protected var projection: Projection? = null
    var isHorizontalWrapEnabled = true
        set(horizontalWrapEnabled) {
            field = horizontalWrapEnabled
            mTileLooper.isHorizontalWrapEnabled = horizontalWrapEnabled
        }
    var isVerticalWrapEnabled = true
        set(verticalWrapEnabled) {
            field = verticalWrapEnabled
            mTileLooper.isVerticalWrapEnabled = verticalWrapEnabled
        }

    //Issue 133 night mode
    private var currentColorFilter: ColorFilter? = null

    @Inject lateinit var msrsRepo: MsrsRepo
    private val cs = CoroutineScope(Dispatchers.IO)



    override fun onDetach(pMapView: MapView) {
        ctx = null
    }

    /**
     * Get the area we are drawing to
     *
     * @return true if the tiles are to be drawn
     * @since 6.0.0
     */
    protected fun setViewPort(pCanvas: Canvas?, pProjection: Projection?): Boolean {
        projection = pProjection
        projection!!.getMercatorViewPort(mViewPort)
        return true
    }

    override fun draw(c: Canvas, pProjection: Projection) {
        if (Configuration.getInstance().isDebugTileProviders) {
            Log.d(IMapView.LOGTAG, "onDraw")
        }
        if (!setViewPort(c, pProjection)) {
            return
        }

        // Draw the tiles!
        drawTiles(c, projection, projection!!.zoomLevel, mViewPort)
    }

    /**
     * This is meant to be a "pure" tile drawing function that doesn't take into account
     * osmdroid-specific characteristics (like osmdroid's canvas's having 0,0 as the center rather
     * than the upper-left corner). Once the tile is ready to be drawn, it is passed to
     * onTileReadyToDraw where custom manipulations can be made before drawing the tile.
     */
    fun drawTiles(c: Canvas?, projection: Projection?, zoomLevel: Double, viewPort: RectL?) {
        this.projection = projection
        mTileLooper.loop(zoomLevel, viewPort, c)
    }

    /**
     * @since 6.0
     */
    protected inner class OverlaySquareLooper : TileLooper {
        private var mCanvas: Canvas? = null

        constructor() : super()
        constructor(horizontalWrapEnabled: Boolean, verticalWrapEnabled: Boolean) : super(
            horizontalWrapEnabled,
            verticalWrapEnabled
        )

        fun loop(pZoomLevel: Double, pViewPort: RectL?, pCanvas: Canvas?) {
            mCanvas = pCanvas
            loop(pZoomLevel, pViewPort)
        }

        override fun initialiseLoop() {
            // make sure the cache is big enough for all the tiles
            val width = mTiles.right - mTiles.left + 1
            val height = mTiles.bottom - mTiles.top + 1
            val numNeeded = height * width
            super.initialiseLoop()
        }

        override fun handleTile(pMapTileIndex: Long, pX: Int, pY: Int) {
            if (mCanvas == null) { // in case we just want to have the tiles downloaded, not displayed
                return
            }
            projection!!.getPixelFromTile(pX, pY, mTileRect)
            var avg by Delegates.notNull<Int>()


            //function that draws the square zones, the last parameter is the avg value of the measurement for that tile
            onTileReadyToDraw(mCanvas!!, mTileRect, avg)
            //onTileReadyToDraw(mCanvas, mTileRect, msrsMap.get("7_10_12"));
            if (Configuration.getInstance().isDebugTileProviders) {
                projection!!.getPixelFromTile(pX, pY, mTileRect)
                mCanvas!!.drawText(
                    MapTileIndex.toString(pMapTileIndex), (mTileRect.left + 1).toFloat(),
                    mTileRect.top + mDebugPaint.textSize, mDebugPaint
                )
                mCanvas!!.drawLine(
                    mTileRect.left.toFloat(),
                    mTileRect.top.toFloat(),
                    mTileRect.right.toFloat(),
                    mTileRect.top.toFloat(),
                    mDebugPaint
                )
                mCanvas!!.drawLine(
                    mTileRect.left.toFloat(),
                    mTileRect.top.toFloat(),
                    mTileRect.left.toFloat(),
                    mTileRect.bottom.toFloat(),
                    mDebugPaint
                )
            }
        }
    }

    private val mTileLooper = OverlaySquareLooper()
    private val mIntersectionRect = Rect()
    protected var canvasRect: Rect? = null

    init {
        isHorizontalWrapEnabled = horizontalWrapEnabled
        isVerticalWrapEnabled = verticalWrapEnabled
    }

    protected fun getPaint(@ColorInt color: Int, style: Paint.Style?): Paint {
        val gridPaint = Paint()
        gridPaint.color = color
        gridPaint.style = style
        gridPaint.strokeWidth = 5f
        return gridPaint
    }

    protected fun onTileReadyToDraw(c: Canvas, tileRect: Rect, avg: Int) {
        val canvasRect = canvasRect
        val paint = Paint()
        if (avg == 32) {
            paint.color = Color.RED
        } else if (avg == 84) {
            paint.color = Color.BLUE
        } else paint.color = Color.GREEN
        paint.alpha = 100
        if (canvasRect == null) {
            //This line of code below creates the square zone
            c.drawRect(
                tileRect.left.toFloat(),
                tileRect.top.toFloat(),
                tileRect.right.toFloat(),
                tileRect.bottom.toFloat(),
                paint
            )
            return
        }
        // Check to see if the drawing area intersects with the minimap area
        if (!mIntersectionRect.setIntersect(c.clipBounds, canvasRect)) {
            return
        }
        // Save the current clipping bounds
        c.save()

        // Clip that area
        c.clipRect(mIntersectionRect)

        // Draw the tile, which will be appropriately clipped
        //currentMapTile.draw(c);
        //This line of code below creates the square zone
        c.drawRect(
            tileRect.left.toFloat(),
            tileRect.top.toFloat(),
            tileRect.right.toFloat(),
            tileRect.bottom.toFloat(),
            paint
        )
        c.restore()
    }

    /**
     * sets the current color filter, which is applied to tiles before being drawn to the screen.
     * Use this to enable night mode or any other tile rendering adjustment as necessary. use null to clear.
     * INVERT_COLORS provides color inversion for convenience and to support the previous night mode
     *
     * @param filter
     * @since 5.1
     */
    fun setColorFilter(filter: ColorFilter?) {
        currentColorFilter = filter
    }

    companion object {
        val negate = floatArrayOf(
            -1.0f, 0f, 0f, 0f, 255f,  //red
            0f, -1.0f, 0f, 0f, 255f,  //green
            0f, 0f, -1.0f, 0f, 255f,  //blue
            0f, 0f, 0f, 1.0f, 0f //alpha
        )

        /**
         * provides a night mode like affect by inverting the map tile colors
         */
        val INVERT_COLORS: ColorFilter = ColorMatrixColorFilter(negate)
    }
}
/*
fun Number.msrColor(msrType: Measure)= when(msrType){
    Measure.phone->
    Measure.wifi->
    Measure.sound->
}*/

