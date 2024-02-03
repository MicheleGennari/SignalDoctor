package com.example.signaldoctor.hiltModules

import android.content.Context
import android.location.Geocoder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.room.Room
import androidx.work.WorkManager
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.LocalHints
import com.example.signaldoctor.contracts.FirebaseContracts
import com.example.signaldoctor.localDatabase.IMsrsLocalDB
import com.example.signaldoctor.localDatabase.RoomDBImpl
import com.example.signaldoctor.onlineDatabase.RealtimeDBImpl
import com.example.signaldoctor.onlineDatabase.IMsrsOnlineDB
import com.example.signaldoctor.room.MsrsDB
import com.example.signaldoctor.utils.AppSettingsSerializer
import com.example.signaldoctor.utils.LocalHintsSerializer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.SettingsClient
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.osmdroid.bonuspack.location.GeocoderNominatim
import java.lang.annotation.Inherited
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeFirebase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultLocalDB

const val dataStoresDir = "datastore"
const val appSettingsDataStoreFileName = "AppSettings.pb"

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModules {

    @Singleton
    @Provides
    fun provideWorkManager(@ApplicationContext ctx : Context) : WorkManager =
        WorkManager.getInstance(ctx)


    @Singleton
    @Provides
    fun provideJsonConverter() : Gson {
        return Gson()
    }

    @Singleton
    @Provides
    fun provideFirebaseDB(): FirebaseDatabase {
        return Firebase.database(FirebaseContracts.ROOT.toString())
    }

    @Singleton
    @Provides
    fun provideUserSettingsDataStore(@ApplicationContext app : Context) : DataStore<AppSettings> {
        return DataStoreFactory.create(
            serializer = AppSettingsSerializer(),
            produceFile = { app.filesDir.resolve("$dataStoresDir/$appSettingsDataStoreFileName") },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                e.printStackTrace()
                AppSettings.getDefaultInstance()
            }
        )
    }

    @Singleton
    @Provides
    fun provideLocalHints(@ApplicationContext app : Context) : DataStore<LocalHints>{
        return DataStoreFactory.create(
            serializer = LocalHintsSerializer(),
            produceFile = { app.filesDir.resolve("$dataStoresDir/$localHintsFileName") },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                e.printStackTrace()
                LocalHints.getDefaultInstance()
            }
        )
    }

    @Singleton
    @Provides
    fun bindRoomDB(@ApplicationContext ctx: Context) : MsrsDB {
        return Room.databaseBuilder(ctx, MsrsDB::class.java, name = "signal-doctor-measurements")
            .fallbackToDestructiveMigration()
          /*  .addCallback(callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val msrsDB = db as MsrsDB
                    msrsDB.phoneMeasurementDAO().postMsr(
                        PhoneMeasurement(
                            baseMeasurementEntity =  BaseMeasurementEntity(),
                            isLTE = false
                        )
                    )
                }
            })
            */
            .build()
    }

    @Singleton
    @Provides
    fun provideLocationProvider(@ApplicationContext ctx : Context) : FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(ctx)
    }

    @Singleton
    @Provides
    fun provideFusedLocationProviderSettingsClient(@ApplicationContext ctx: Context) : SettingsClient {
        return LocationServices.getSettingsClient(ctx)
    }

    @Singleton
    @Provides
    fun provideNotificationManager(@ApplicationContext ctx : Context) : NotificationManagerCompat =
        NotificationManagerCompat.from(ctx)

}

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindModules{

    @RealtimeFirebase
    @Singleton
    @Binds
    abstract fun bindRealtimeDB( db : RealtimeDBImpl) : IMsrsOnlineDB

    @DefaultLocalDB
    @Singleton
    @Binds
    abstract fun bindRoomDB( db: RoomDBImpl) : IMsrsLocalDB


}