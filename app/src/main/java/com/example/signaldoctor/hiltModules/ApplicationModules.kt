package com.example.signaldoctor.hiltModules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import com.example.signaldoctor.Settings
import com.example.signaldoctor.contracts.FirebaseContracts
import com.example.signaldoctor.localDatabase.IMsrsLocalDB
import com.example.signaldoctor.localDatabase.RoomDBImpl
import com.example.signaldoctor.onlineDatabase.RealtimeDBImpl
import com.example.signaldoctor.onlineDatabase.IMsrsOnlineDB
import com.example.signaldoctor.room.MsrsDB
import com.example.signaldoctor.utils.SettingsSerializer
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealtimeFirebase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultLocalDB

const val dataStoresDir = "datastore"
const val settingsDataStoreFileName = "Settings.pb"

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModules {

    @Singleton
    @Provides
    fun provideFirebaseDB(): FirebaseDatabase {
        return Firebase.database(FirebaseContracts.ROOT.toString())
    }

    @Singleton
    @Provides
    fun provideSettingsDataStore(@ApplicationContext app : Context) : DataStore<Settings> {
        return DataStoreFactory.create(
            serializer = SettingsSerializer(),
            produceFile = { app.filesDir.resolve("$dataStoresDir/$settingsDataStoreFileName") }
        )
    }

    @Singleton
    @Provides
    fun bindRoomDB(@ApplicationContext ctx: Context) : MsrsDB {
        return Room.databaseBuilder(ctx, MsrsDB::class.java, name = "signal-doctor-measurements")
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