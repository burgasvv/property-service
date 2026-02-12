package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.sql.Connection
import java.time.format.DateTimeFormatter
import java.util.*

fun AdvertisementEntity.insert(advertisementRequest: AdvertisementRequest) {
    this.title = advertisementRequest.title ?: throw IllegalArgumentException("Advertisement title is null")
    this.description =
        advertisementRequest.description ?: throw IllegalArgumentException("Advertisement description is null")
    this.property = PropertyEntity.findById(
        advertisementRequest.propertyId ?: throw IllegalArgumentException("Advertisement Request propertyId is null")
    ) ?: throw IllegalArgumentException("Advertisement not found")
    this.price = advertisementRequest.price ?: throw IllegalArgumentException("Advertisement price is null")
    this.date = advertisementRequest.date ?: throw IllegalArgumentException("Advertisement date is null")
}

fun AdvertisementEntity.update(advertisementRequest: AdvertisementRequest) {
    this.title = advertisementRequest.title ?: this.title
    this.description = advertisementRequest.description ?: this.description
    this.property = PropertyEntity.findById(advertisementRequest.propertyId ?: UUID(0, 0)) ?: this.property
    this.price = advertisementRequest.price ?: this.price
    this.date = advertisementRequest.date ?: this.date
}

fun AdvertisementEntity.toAdvertisementShortResponse(): AdvertisementShortResponse {
    return AdvertisementShortResponse(
        id = this.id.value,
        title = this.title,
        description = this.description,
        price = this.price,
        date = this.date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
    )
}

fun AdvertisementEntity.toAdvertisementFullResponse(): AdvertisementFullResponse {
    return AdvertisementFullResponse(
        id = this.id.value,
        title = this.title,
        description = this.description,
        property = this.property.toPropertyWithCategoryResponse(),
        price = this.price,
        date = this.date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
    )
}

class AdvertisementService {

    private val redis = DatabaseFactory.redis
    private val advertisementKey = "advertisementFullResponse::%s"
    private val propertyKey = "propertyFullResponse::%s"
    private val identityKey = "identityFullResponse::%s"

    suspend fun create(advertisementRequest: AdvertisementRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        AdvertisementEntity.new { this.insert(advertisementRequest) }
            .load(AdvertisementEntity::property)
            .toAdvertisementFullResponse()
    }

    suspend fun findAll() = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        AdvertisementEntity.all().with(AdvertisementEntity::property).map { it.toAdvertisementShortResponse() }
    }

    suspend fun findById(advertisementId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val advertisementKey = advertisementKey.format(advertisementId)
        val advertisementString = redis.get(advertisementKey)

        if (advertisementString != null) {
            Json.decodeFromString<AdvertisementFullResponse>(advertisementString)
        } else {
            val advertisementFullResponse = (AdvertisementEntity.findById(advertisementId)
                ?: throw IllegalArgumentException("Advertisement not found"))
                .load(AdvertisementEntity::property)
                .toAdvertisementFullResponse()
            redis.set(advertisementKey, Json.encodeToString(advertisementFullResponse))
            advertisementFullResponse
        }
    }

    suspend fun update(advertisementRequest: AdvertisementRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (advertisementRequest.id == null) throw IllegalArgumentException("Advertisement Request id is null")

        val advertisementEntity =
            (AdvertisementEntity.findByIdAndUpdate(advertisementRequest.id) { it.update(advertisementRequest) }
                ?: throw IllegalArgumentException("Advertisement not found and not updated"))
        handleCaches(advertisementEntity)
    }

    suspend fun delete(advertisementId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val advertisementEntity =
            AdvertisementEntity.findById(advertisementId) ?: throw IllegalArgumentException("Advertisement not found")
        handleCaches(advertisementEntity)
        advertisementEntity.delete()
    }

    suspend fun rentPropertyByAdvertisement(rentPropertyRequest: RentPropertyRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.find { IdentityTable.id eq rentPropertyRequest.tenantId }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull() ?: throw IllegalArgumentException("Identity tenant not found")

        val advertisementEntity = AdvertisementEntity.find { AdvertisementTable.id eq rentPropertyRequest.advertisementId }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull() ?: throw IllegalArgumentException("Advertisement not found")

        val propertyEntity = advertisementEntity.property
        propertyEntity.apply {
            this.tenant = identityEntity
        }
        val identityKey = identityKey.format(identityEntity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)
        handleCaches(advertisementEntity)
    }

    private fun handleCaches(advertisementEntity: AdvertisementEntity) {
        val advertisementKey = advertisementKey.format(advertisementEntity.id.value)
        if (redis.exists(advertisementKey)) redis.del(advertisementKey)

        val propertyKey = propertyKey.format(advertisementEntity.property.id.value)
        if (redis.exists(propertyKey)) redis.del(propertyKey)
    }
}