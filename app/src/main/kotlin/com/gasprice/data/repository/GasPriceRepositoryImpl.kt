package com.gasprice.data.repository

import com.gasprice.data.local.dao.GasPriceObservationDao
import com.gasprice.data.local.entity.toDomain
import com.gasprice.data.local.entity.toEntity
import com.gasprice.domain.model.GasPriceObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GasPriceRepositoryImpl @Inject constructor(
    private val dao: GasPriceObservationDao
) : GasPriceRepository {

    override fun observeAll(): Flow<List<GasPriceObservation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<GasPriceObservation>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): GasPriceObservation? =
        dao.getById(id)?.toDomain()

    override suspend fun save(observation: GasPriceObservation) =
        dao.insert(observation.toEntity())

    override suspend fun update(observation: GasPriceObservation) =
        dao.update(observation.toEntity())

    override suspend fun delete(id: String) =
        dao.deleteById(id)

    override suspend fun getRecentlyCapturedPlaceIds(sinceTimestamp: Long): Set<String> =
        dao.getRecentlyCapturedPlaceIds(sinceTimestamp).toSet()
}
