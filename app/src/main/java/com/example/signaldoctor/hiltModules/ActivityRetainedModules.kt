package com.example.signaldoctor.hiltModules

import android.content.Context
import android.location.Geocoder
import com.example.signaldoctor.mapUtils.FlowGeocoder
import com.example.signaldoctor.mapUtils.FlowOsmGeocoder
import com.example.signaldoctor.mapUtils.IFlowGeocoder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.osmdroid.bonuspack.location.GeocoderNominatim
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AndroidGeocoder

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OsmGeocoder

@Module
@InstallIn(ActivityRetainedComponent::class)
class ActivityRetainedModules {

    @ActivityRetainedScoped
    @Provides
    fun provideAndroidGeocoder(@ApplicationContext ctx : Context)= Geocoder(ctx)

    @ActivityRetainedScoped
    @Provides
    fun provideOsmGeocoder()= GeocoderNominatim("Signal_Doctor")


}

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class ActivityRetainedBindModules{

    @AndroidGeocoder
    @ActivityRetainedScoped
    @Binds
    abstract fun bindAndroidGeocoder( flowGeocoder : FlowGeocoder ) : IFlowGeocoder

    @OsmGeocoder
    @ActivityRetainedScoped
    @Binds
    abstract fun bindOsmGeocoder( osmGeocoder: FlowOsmGeocoder ) : IFlowGeocoder

}

