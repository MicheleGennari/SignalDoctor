package com.example.signaldoctor.hiltModules

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import javax.inject.Qualifier

const val localHintsFileName = "localHints.pb"


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapnikMap

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultTileMap

@Module
@InstallIn(ViewModelComponent::class)
abstract class viewModelBindModules {
/*
    @RealtimeFirebase
    @ViewModelScoped
    @Binds
    abstract fun bindRealtimeDB( db : RealtimeDBImpl) : IMsrsOnlineDB
*/



}

@Module
@InstallIn(ViewModelComponent::class)
class ViewModelProvideModules {

    /*
    @ViewModelScoped
    @Provides
    fun provideFirebaseDB(): FirebaseDatabase {
        return Firebase.database(FirebaseContracts.ROOT.toString())
    }

*/
    @MapnikMap
    @ViewModelScoped
    @Provides
    fun provideMapnikMap(@ApplicationContext ctx: Context): MapView {
        return MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }
    }

    @DefaultTileMap
    @ViewModelScoped
    @Provides
    fun provideDefaultMap(@ApplicationContext ctx: Context): MapView {
        return MapView(ctx).apply {
            setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        }

    }


    @ViewModelScoped
    @Provides
    fun provideTelephonyManager(@ApplicationContext ctx : Context) : TelephonyManager {

        return ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @ViewModelScoped
    @Provides
    fun provideWifiManager(@ApplicationContext ctx: Context) : WifiManager {

        return ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @ViewModelScoped
    @Provides
    fun provideConnectivityManager(@ApplicationContext ctx : Context) : ConnectivityManager {

        return ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /*
    @ViewModelScoped
    @Provides
    fun provideAudioManager(@ApplicationContext ctx : Context) : AudioManager {

        return ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
*/


}