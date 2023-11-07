package com.example.signaldoctor.hiltModules

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.example.signaldoctor.contracts.FirebaseContracts
import com.example.signaldoctor.onlineDatabase.RealtimeDBImpl
import com.example.signaldoctor.repositories.IMsrsOnlineDB
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModules {

    @Singleton
    @Provides
    fun provideFirebaseDB(): FirebaseDatabase {
        return Firebase.database(FirebaseContracts.ROOT.toString())
    }



}

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationBindModules{

    @RealtimeFirebase
    @Singleton
    @Binds
    abstract fun bindRealtimeDB( db : RealtimeDBImpl) : IMsrsOnlineDB

}