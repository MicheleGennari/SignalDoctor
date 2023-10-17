package com.example.signaldoctor.hiltModules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.HardwarePropertiesManager
import android.provider.MediaStore.Audio
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.FirebaseContracts
import com.example.signaldoctor.localDatabase.RoomDBImpl
import com.example.signaldoctor.onlineDatabase.RealtimeDBImpl
import com.example.signaldoctor.repositories.IMsrsLocalDB
import com.example.signaldoctor.repositories.IMsrsOnlineDB
import com.example.signaldoctor.workers.NoiseMsrWorker
import com.example.signaldoctor.workers.PhoneMsrWorker
import com.example.signaldoctor.workers.WifiMsrWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.SettingsClient
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.internal.modules.ApplicationContextModule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import java.lang.IllegalArgumentException
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeFirebase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RoomDatabase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapnikMap

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultTileMap

@Module
@InstallIn(ViewModelComponent::class)
abstract class viewModelBindModules {

    @RealtimeFirebase
    @ViewModelScoped
    @Binds
    abstract fun bindRealtimeDB( db : RealtimeDBImpl) : IMsrsOnlineDB

    @RoomDatabase
    @ViewModelScoped
    @Binds
    abstract fun bindRoomDB(db : RoomDBImpl) : IMsrsLocalDB




}

@Module
@InstallIn(ViewModelComponent::class)
class ViewModelProvideModules {

    @ViewModelScoped
    @Provides
    fun provideFirebaseDB(): FirebaseDatabase {
        return Firebase.database(FirebaseContracts.ROOT.toString())
    }

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
    fun provideLocationProvider(@ApplicationContext ctx : Context) : FusedLocationProviderClient{
        return LocationServices.getFusedLocationProviderClient(ctx)
    }

    @ViewModelScoped
    @Provides
    fun provideFusedLocationProviderSettingsClient(@ApplicationContext ctx: Context) : SettingsClient {
        return LocationServices.getSettingsClient(ctx)
    }


    @ViewModelScoped
    @Provides
    fun providePhoneService(@ApplicationContext ctx : Context) : TelephonyManager {

        return ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @ViewModelScoped
    @Provides
    fun provideAudioManager(@ApplicationContext ctx : Context) : AudioManager {

        return ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }


    @ViewModelScoped
    @Provides
    fun provideWorkManager(@ApplicationContext ctx : Context) : WorkManager =
        WorkManager.getInstance(ctx)


    @ViewModelScoped
    @Provides
    fun provideNotificationManager(@ApplicationContext ctx : Context) : NotificationManagerCompat =
        NotificationManagerCompat.from(ctx)

}