package com.gasprice.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gasprice.data.local.dao.GasPriceObservationDao
import com.gasprice.data.local.entity.GasPriceObservationEntity

@Database(
    entities = [GasPriceObservationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class GasPriceDatabase : RoomDatabase() {
    abstract fun observationDao(): GasPriceObservationDao

    companion object {
        const val DB_NAME = "gas_price_tracker.db"
    }
}
