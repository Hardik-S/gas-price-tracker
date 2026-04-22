package com.gasprice.data.repository

import com.gasprice.domain.model.GasPriceObservation
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface — abstracts storage from domain logic.
 * Sync/cloud layer can be added behind this interface in V2.
 */
interface GasPriceRepository {
    fun observeAll(): Flow<List<GasPriceObservation>>
    fun observeRecent(limit: Int = 50): Flow<List<GasPriceObservation>>
    suspend fun getById(id: String): GasPriceObservation?
    suspend fun save(observation: GasPriceObservation)
    suspend fun update(observation: GasPriceObservation)
    suspend fun delete(id: String)
    suspend fun getRecentlyCapturedPlaceIds(sinceTimestamp: Long): Set<String>
}
