package com.example.signaldoctor.hiltModules

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModules {

   /* @MapnikMap
    @Singleton
    @Provides
    fun provideMapnikMap(@ApplicationContext ctx : Context) : MapView {
        return MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }
    }*/

}