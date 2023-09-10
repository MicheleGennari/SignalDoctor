package com.example.signaldoctor.hiltModules

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telephony.TelephonyManager
import androidx.work.WorkManager
import com.example.signaldoctor.contracts.FirebaseContracts
import com.example.signaldoctor.localDatabase.RoomDBImpl
import com.example.signaldoctor.onlineDatabase.RealtimeDBImpl
import com.example.signaldoctor.repositories.IMsrsLocalDB
import com.example.signaldoctor.repositories.IMsrsOnlineDB
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    abstract fun provideRoomDB(db : RoomDBImpl) : IMsrsLocalDB





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

    @ViewModelScoped
    @Provides
    fun provideLocationProvider(@ApplicationContext ctx : Context) : FusedLocationProviderClient{
        return LocationServices.getFusedLocationProviderClient(ctx)
    }

    @ViewModelScoped
    @Provides
    fun provideMicService(@ApplicationContext ctx : Context) : TelephonyManager {
        return ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @ViewModelScoped
    @Provides
    fun provideWorkManager(@ApplicationContext ctx : Context) : WorkManager {
       return WorkManager.getInstance(ctx)
    }

}