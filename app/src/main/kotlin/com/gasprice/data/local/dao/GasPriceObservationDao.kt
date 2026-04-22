package com.gasprice.data.local.dao

import androidx.room.*
import com.gasprice.data.local.entity.GasPriceObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GasPriceObservationDao {

    @Query("SELECT * FROM gas_price_observations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GasPriceObservationEntity>>

    @Query("SELECT * FROM gas_price_observations ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<GasPriceObservationEntity>>

    @Query("SELECT * FROM gas_price_observations WHERE id = :id")
    suspend fun getById(id: String): GasPriceObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GasPriceObservationEntity)

    @Update
    suspend fun update(entity: GasPriceObservationEntity)

    @Delete
    suspend fun delete(entity: GasPriceObservationEntity)

    @Query("DELETE FROM gas_price_observations WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Get place IDs captured in the last N minutes — used for station ranking to
     * deprioritize already-captured stations in this session.
     */
    @Query("""
        SELECT DISTINCT stationPlaceId FROM gas_price_observations 
        WHERE stationPlaceId IS NOT NULL 
        AND createdAt > :sinceTimestamp
    """)
    suspend fun getRecentlyCapturedPlaceIds(sinceTimestamp: Long): List<String>
}
