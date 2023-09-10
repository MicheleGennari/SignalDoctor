package com.example.signaldoctor.mapUtils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.example.signaldoctor.contracts.MsrsMap;

import org.osmdroid.api.IMapView;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.RectL;
import org.osmdroid.util.TileLooper;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;


public class SquaredZonesOverlay extends Overlay {

    private Context ctx;
    /**
     * Current tile source
     */

    protected final Paint mDebugPaint = new Paint();
    private final Rect mTileRect = new Rect();
    protected final RectL mViewPort = new RectL();

    protected Projection mProjection;


    private boolean horizontalWrapEnabled = true;
    private boolean verticalWrapEnabled = true;

    //Issue 133 night mode
    private ColorFilter currentColorFilter = null;
    final static float[] negate = {
            -1.0f, 0, 0, 0, 255,        //red
            0, -1.0f, 0, 0, 255,//green
            0, 0, -1.0f, 0, 255,//blue
            0, 0, 0, 1.0f, 0 //alpha
    };
    /**
     * provides a night mode like affect by inverting the map tile colors
     */
    public final static ColorFilter INVERT_COLORS = new ColorMatrixColorFilter(negate);


    private MsrsMap msrsMap = new MsrsMap();

    public SquaredZonesOverlay(final Context aContext) {
        this(aContext, true, true);
    }

    public SquaredZonesOverlay(final Context aContext, final MsrsMap msrsMap) {
        this(aContext, true, true);
        setMsrsMap(msrsMap);
    }

    public SquaredZonesOverlay(final Context aContext, boolean horizontalWrapEnabled, boolean verticalWrapEnabled) {
        super();
        this.ctx = aContext;
        setHorizontalWrapEnabled(horizontalWrapEnabled);
        setVerticalWrapEnabled(verticalWrapEnabled);
    }

    //sets the HashMap of tile indexes' measures averages
    public void setMsrsMap(final MsrsMap newMsrsMap){
        this.msrsMap = newMsrsMap;
    }

    @Override
    public void onDetach(final MapView pMapView) {
        ctx = null;
    }

    /**
     * Get the area we are drawing to
     *
     * @return true if the tiles are to be drawn
     * @since 6.0.0
     */
    protected boolean setViewPort(final Canvas pCanvas, final Projection pProjection) {
        setProjection(pProjection);
        getProjection().getMercatorViewPort(mViewPort);
        return true;
    }

    @Override
    public void draw(Canvas c, Projection pProjection) {

        if (Configuration.getInstance().isDebugTileProviders()) {
            Log.d(IMapView.LOGTAG, "onDraw");
        }

        if (!setViewPort(c, pProjection)) {
            return;
        }

        // Draw the tiles!
        drawTiles(c, getProjection(), getProjection().getZoomLevel(), mViewPort);
    }

    /**
     * This is meant to be a "pure" tile drawing function that doesn't take into account
     * osmdroid-specific characteristics (like osmdroid's canvas's having 0,0 as the center rather
     * than the upper-left corner). Once the tile is ready to be drawn, it is passed to
     * onTileReadyToDraw where custom manipulations can be made before drawing the tile.
     */
    public void drawTiles(final Canvas c, final Projection projection, final double zoomLevel, final RectL viewPort) {
        mProjection = projection;
        mTileLooper.loop(zoomLevel, viewPort, c);
    }

    /**
     * @since 6.0
     */
    protected class OverlaySquareLooper extends TileLooper {

        private Canvas mCanvas;

        public OverlaySquareLooper() {
            super();
        }

        public OverlaySquareLooper(boolean horizontalWrapEnabled, boolean verticalWrapEnabled) {
            super(horizontalWrapEnabled, verticalWrapEnabled);
        }

        public void loop(final double pZoomLevel, final RectL pViewPort, final Canvas pCanvas) {
            mCanvas = pCanvas;
            loop(pZoomLevel, pViewPort);
        }

        @Override
        public void initialiseLoop() {
            // make sure the cache is big enough for all the tiles
            final int width = mTiles.right - mTiles.left + 1;
            final int height = mTiles.bottom - mTiles.top + 1;
            final int numNeeded = height * width;
            super.initialiseLoop();
        }

        @Override
        public void handleTile(final long pMapTileIndex, int pX, int pY) {
            if (mCanvas == null) { // in case we just want to have the tiles downloaded, not displayed
                return;
            }

            mProjection.getPixelFromTile(pX, pY, mTileRect);
            //function that draws the square zones
            onTileReadyToDraw(mCanvas, mTileRect, msrsMap.get(MapTileIndex.getZoom(pMapTileIndex)+"_"+MapTileIndex.getX(pMapTileIndex)+"_"+MapTileIndex.getY(pMapTileIndex)));
            //onTileReadyToDraw(mCanvas, mTileRect, msrsMap.get("7_10_12"));

            if (Configuration.getInstance().isDebugTileProviders()) {
                mProjection.getPixelFromTile(pX, pY, mTileRect);
                mCanvas.drawText(MapTileIndex.toString(pMapTileIndex), mTileRect.left + 1,
                        mTileRect.top + mDebugPaint.getTextSize(), mDebugPaint);
                mCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.right, mTileRect.top,
                        mDebugPaint);
                mCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.left, mTileRect.bottom,
                        mDebugPaint);
            }
        }
    }


    private final OverlaySquareLooper mTileLooper = new OverlaySquareLooper();
    private final Rect mIntersectionRect = new Rect();

    private Rect mCanvasRect;

    protected void setCanvasRect(final Rect pCanvasRect) {
        mCanvasRect = pCanvasRect;
    }

    protected Rect getCanvasRect() {
        return mCanvasRect;
    }

    protected void setProjection(final Projection pProjection) {
        mProjection = pProjection;
    }

    protected Projection getProjection() {
        return mProjection;
    }


    protected Paint getPaint(@ColorInt final int color, final Paint.Style style) {
        Paint gridPaint = new Paint();
        gridPaint.setColor(color);
        gridPaint.setStyle(style);
        gridPaint.setStrokeWidth(5);
        return gridPaint;
    }

    protected void onTileReadyToDraw(final Canvas c, final Rect tileRect, @NonNull final Long avg) {
        final Rect canvasRect = getCanvasRect();
        Paint paint = new Paint();
        if(avg!= 32){
            paint.setColor(Color.RED);
        }else if(avg == 84){
            paint.setColor(Color.BLUE);
        }else paint.setColor(Color.GREEN);

        paint.setAlpha(100);
        if (canvasRect == null) {
            //This line of code below creates the square zone
            c.drawRect(tileRect.left, tileRect.top, tileRect.right, tileRect.bottom, paint);
            return;
        }
        // Check to see if the drawing area intersects with the minimap area
        if (!mIntersectionRect.setIntersect(c.getClipBounds(), canvasRect)) {
            return;
        }
        // Save the current clipping bounds
        c.save();

        // Clip that area
        c.clipRect(mIntersectionRect);

        // Draw the tile, which will be appropriately clipped
        //currentMapTile.draw(c);
        //This line of code below creates the square zone
        c.drawRect(tileRect.left, tileRect.top, tileRect.right, tileRect.bottom, paint);

        c.restore();
    }


    /**
     * sets the current color filter, which is applied to tiles before being drawn to the screen.
     * Use this to enable night mode or any other tile rendering adjustment as necessary. use null to clear.
     * INVERT_COLORS provides color inversion for convenience and to support the previous night mode
     *
     * @param filter
     * @since 5.1
     */
    public void setColorFilter(ColorFilter filter) {

        this.currentColorFilter = filter;
    }

    public boolean isHorizontalWrapEnabled() {
        return horizontalWrapEnabled;
    }

    public void setHorizontalWrapEnabled(boolean horizontalWrapEnabled) {
        this.horizontalWrapEnabled = horizontalWrapEnabled;
        this.mTileLooper.setHorizontalWrapEnabled(horizontalWrapEnabled);
    }

    public boolean isVerticalWrapEnabled() {
        return verticalWrapEnabled;
    }

    public void setVerticalWrapEnabled(boolean verticalWrapEnabled) {
        this.verticalWrapEnabled = verticalWrapEnabled;
        this.mTileLooper.setVerticalWrapEnabled(verticalWrapEnabled);
    }

}