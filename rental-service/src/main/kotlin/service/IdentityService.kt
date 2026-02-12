package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*

fun IdentityEntity.insert(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: throw IllegalArgumentException("Identity authority is null")
    this.username = identityRequest.username ?: throw IllegalArgumentException("Identity username is null")
    this.password = if (!identityRequest.password.isNullOrEmpty()) BCrypt.hashpw(
        identityRequest.password, BCrypt.gensalt()
    ) else throw IllegalArgumentException("Identity password is null or empty")
    this.email = identityRequest.email ?: throw IllegalArgumentException("Identity email is null")
    this.enabled = identityRequest.enabled ?: true
    this.firstname = identityRequest.firstname ?: throw IllegalArgumentException("Identity firstname is null")
    this.lastname = identityRequest.lastname ?: throw IllegalArgumentException("Identity lastname is null")
    this.patronymic = identityRequest.patronymic ?: throw IllegalArgumentException("Identity patronymic is null")
}

fun IdentityEntity.update(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: this.authority
    this.username = identityRequest.username ?: this.username
    this.email = identityRequest.email ?: this.email
    this.enabled = identityRequest.enabled ?: this.enabled
    this.firstname = identityRequest.firstname ?: this.firstname
    this.lastname = identityRequest.lastname ?: this.lastname
    this.patronymic = identityRequest.patronymic ?: this.patronymic
}

fun IdentityEntity.toIdentityShortResponse(): IdentityShortResponse {
    return IdentityShortResponse(
        id = this.id.value,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        image = this.image?.toImageResponse()
    )
}

fun IdentityEntity.toIdentityFullResponse(): IdentityFullResponse {
    return IdentityFullResponse(
        id = this.id.value,
        username = this.username,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        image = this.image?.toImageResponse(),
        ownerProperties = this.ownerProperties.map { it.toPropertyWithCategoryResponse() },
        tenantProperties = this.tenantProperties.map { it.toPropertyWithCategoryResponse() }
    )
}

class IdentityService {

    private val redis = DatabaseFactory.redis
    private val identityKey = "identityFullResponse::%s"
    private val propertyKey = "propertyFullResponse::%s"

    val imageService = ImageService()

    suspend fun create(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        IdentityEntity.new { this.insert(identityRequest) }
            .load(IdentityEntity::image, IdentityEntity::ownerProperties, IdentityEntity::tenantProperties)
            .toIdentityFullResponse()
    }

    suspend fun findAll() = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.all().with(IdentityEntity::image).map { it.toIdentityShortResponse() }
    }

    suspend fun findById(identityId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val identityKey = identityKey.format(identityId)
        val identityString = redis.get(identityKey)

        if (identityString != null) {
            Json.decodeFromString<IdentityFullResponse>(identityString)
        } else {
            val identityFullResponse =
                (IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                    .load(IdentityEntity::image, IdentityEntity::ownerProperties, IdentityEntity::tenantProperties)
                    .toIdentityFullResponse()
            redis.set(identityKey, Json.encodeToString(identityFullResponse))
            identityFullResponse
        }
    }

    suspend fun update(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityRequest.id == null) throw IllegalArgumentException("Identity Request id is null")

        val identityEntity = (IdentityEntity.findByIdAndUpdate(identityRequest.id) { it.update(identityRequest) }
            ?: throw IllegalArgumentException("Identity not found and not updated"))

        handleCaches(identityEntity)
    }

    suspend fun delete(identityId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
        handleCaches(identityEntity)
        identityEntity.delete()
    }

    suspend fun changePassword(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityRequest.id == null) throw IllegalArgumentException("Identity Request id is null")
        if (identityRequest.password.isNullOrEmpty()) throw IllegalArgumentException("Identity Request password is null or empty")

        val identityEntity = IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
        if (BCrypt.checkpw(identityRequest.password, identityEntity.password)) {
            throw IllegalArgumentException("Old and new passwords matched")
        } else {
            identityEntity.apply {
                this.password = BCrypt.hashpw(identityRequest.password, BCrypt.gensalt())
            }
        }
        handleCaches(identityEntity)
    }

    suspend fun changeStatus(identityRequest: IdentityRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityRequest.id == null) throw IllegalArgumentException("Identity Request id is null")
        if (identityRequest.enabled == null) throw IllegalArgumentException("Identity Request status is null")

        val identityEntity = IdentityEntity.findById(identityRequest.id) ?: throw IllegalArgumentException("Identity not found")
        if (identityRequest.enabled == identityEntity.enabled) {
            throw IllegalArgumentException("Identity statuses matched")
        } else {
            identityEntity.apply {
                this.enabled = identityRequest.enabled
            }
        }
        handleCaches(identityEntity)
    }

    suspend fun uploadImage(identityId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val imageEntity = imageService.upload(multiPartData).first()
        imageEntity.apply {
            this.preview = true
        }
        val identityEntity = IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
        identityEntity.apply {
            this.image = imageEntity
        }
        handleCaches(identityEntity)
    }

    suspend fun removeImage(identityId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
        val imageEntity = identityEntity.image
        if (imageEntity != null) {
            identityEntity.apply {
                this.image = null
            }
            imageService.delete(listOf(imageEntity.id.value))
        } else {
            throw IllegalArgumentException("Image not found")
        }
        handleCaches(identityEntity)
    }

    private fun handleCaches(identityEntity: IdentityEntity) {
        val identityKey = identityKey.format(identityEntity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)

        if (!identityEntity.ownerProperties.empty()) {

            identityEntity.ownerProperties.forEach {
                val propertyKey = propertyKey.format(it.id.value)
                if (redis.exists(propertyKey)) redis.del(propertyKey)
            }
        }

        if (!identityEntity.tenantProperties.empty()) {

            identityEntity.tenantProperties.forEach {
                val propertyKey = propertyKey.format(it.id.value)
                if (redis.exists(propertyKey)) redis.del(propertyKey)
            }
        }
    }
}