package com.gasprice.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gasprice.domain.model.EntrySource
import com.gasprice.domain.model.GasPriceObservation
import com.gasprice.domain.model.ParsingStatus

@Entity(tableName = "gas_price_observations")
data class GasPriceObservationEntity(
    @PrimaryKey val id: String,
    val stationPlaceId: String?,
    val stationName: String,
    val stationAddress: String?,
    val latitude: Double,
    val longitude: Double,
    val detectionTimestamp: Long,
    val entrySource: String,   // EntrySource.name()
    val rawTranscript: String?,
    val parsedPrice: Double?,
    val parsingStatus: String, // ParsingStatus.name()
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

fun GasPriceObservationEntity.toDomain() = GasPriceObservation(
    id = id,
    stationPlaceId = stationPlaceId,
    stationName = stationName,
    stationAddress = stationAddress,
    latitude = latitude,
    longitude = longitude,
    detectionTimestamp = detectionTimestamp,
    entrySource = EntrySource.valueOf(entrySource),
    rawTranscript = rawTranscript,
    parsedPrice = parsedPrice,
    parsingStatus = ParsingStatus.valueOf(parsingStatus),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GasPriceObservation.toEntity() = GasPriceObservationEntity(
    id = id,
    stationPlaceId = stationPlaceId,
    stationName = stationName,
    stationAddress = stationAddress,
    latitude = latitude,
    longitude = longitude,
    detectionTimestamp = detectionTimestamp,
    entrySource = entrySource.name,
    rawTranscript = rawTranscript,
    parsedPrice = parsedPrice,
    parsingStatus = parsingStatus.name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)
