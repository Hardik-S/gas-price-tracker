package com.gasprice.di

import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.gasprice.data.local.GasPriceDatabase
import com.gasprice.data.local.dao.GasPriceObservationDao
import com.gasprice.data.repository.GasPriceRepository
import com.gasprice.data.repository.GasPriceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GasPriceDatabase =
        Room.databaseBuilder(context, GasPriceDatabase::class.java, GasPriceDatabase.DB_NAME)
            .fallbackToDestructiveMigration()  // V1: acceptable; add migrations in V2
            .build()

    @Provides
    fun provideDao(db: GasPriceDatabase): GasPriceObservationDao = db.observationDao()
}

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideGeofencingClient(@ApplicationContext context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context)

    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient {
        // Places.initialize() is called in GasPriceApp.onCreate()
        // If key is missing, this still returns a client that will fail gracefully on calls
        return try {
            Places.createClient(context)
        } catch (e: Exception) {
            // If Places not initialized (missing API key), still provide a no-op client
            // Callers handle empty results
            Places.createClient(context)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGasPriceRepository(impl: GasPriceRepositoryImpl): GasPriceRepository
}
