package org.burgas.service

import io.ktor.http.content.MultiPartData
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

fun BuildingEntity.insert(buildingRequest: BuildingRequest) {
    this.address = buildingRequest.address ?: throw IllegalArgumentException("Building address is null")
    this.materials = buildingRequest.materials ?: throw IllegalArgumentException("Building materials is null")
    this.floors = buildingRequest.floors ?: throw IllegalArgumentException("Building floors is null")
    this.onObject = buildingRequest.onObject ?: throw IllegalArgumentException("Building onObject is null")
    this.description = buildingRequest.description ?: throw IllegalArgumentException("Building description is null")
    this.identity = IdentityEntity.findById(
        buildingRequest.identityId ?: throw IllegalArgumentException("Building Request identityId is null")
    ) ?: throw IllegalArgumentException("Building identity not found")
    this.built = buildingRequest.built ?: false
}

fun BuildingEntity.update(buildingRequest: BuildingRequest) {
    this.address = buildingRequest.address ?: this.address
    this.materials = buildingRequest.materials ?: this.materials
    this.floors = buildingRequest.floors ?: this.floors
    this.onObject = buildingRequest.onObject ?: this.onObject
    this.description = buildingRequest.description ?: this.description
    this.identity = IdentityEntity.findById(buildingRequest.identityId ?: UUID(0, 0)) ?: this.identity
    this.built = buildingRequest.built ?: this.built
}

fun BuildingEntity.toBuildingShortResponse(): BuildingShortResponse {
    return BuildingShortResponse(
        id = this.id.value,
        address = this.address,
        materials = this.materials,
        floors = this.floors,
        onObject = this.onObject,
        description = this.description,
        built = this.built,
        images = this.images.map { it.toImageResponse() }
    )
}

fun BuildingEntity.toBuildingFullResponse(): BuildingFullResponse {
    return BuildingFullResponse(
        id = this.id.value,
        address = this.address,
        materials = this.materials,
        floors = this.floors,
        onObject = this.onObject,
        description = this.description,
        identity = this.identity.toIdentityShortResponse(),
        built = this.built,
        images = this.images.map { it.toImageResponse() },
        documents = this.documents.map { it.toDocumentResponse() }
    )
}

class BuildingService {

    private val imageService = ImageService()
    private val documentService = DocumentService()

    private val redis = DatabaseFactory.redis
    private val buildingKey = "buildingFullResponse::%s"
    private val identityKey = "identityFullResponse::%s"

    suspend fun create(buildingRequest: BuildingRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        BuildingEntity.new { this.insert(buildingRequest) }.load(
            BuildingEntity::identity, BuildingEntity::images, BuildingEntity::documents
        ).toBuildingFullResponse()
    }

    suspend fun findAll() = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        BuildingEntity.all().with(BuildingEntity::images).map { it.toBuildingShortResponse() }
    }

    suspend fun findById(buildingId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val buildingKey = buildingKey.format(buildingId)
        val buildingString = redis.get(buildingKey)

        if (buildingString != null) {
            Json.decodeFromString<BuildingFullResponse>(buildingString)
        } else {
            val buildingFullResponse =
                (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))
                    .load(BuildingEntity::identity, BuildingEntity::images, BuildingEntity::documents)
                    .toBuildingFullResponse()
            redis.set(buildingKey, Json.encodeToString(buildingFullResponse))
            buildingFullResponse
        }
    }

    suspend fun update(buildingRequest: BuildingRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (buildingRequest.id == null) throw IllegalArgumentException("Building Request id is null")

        val buildingEntity = (BuildingEntity.findByIdAndUpdate(buildingRequest.id) { it.update(buildingRequest) })
            ?: throw IllegalArgumentException("Building not found and not updated")

        handleCaches(buildingEntity)
    }

    suspend fun delete(buildingId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))
        handleCaches(buildingEntity)
        val imageIds = buildingEntity.images.map { it.id.value }
        val documentIds = buildingEntity.documents.map { it.id.value }
        buildingEntity.delete()
        imageIds.forEach { imageId -> imageService.remove(imageId) }
        documentIds.forEach { documentId -> documentService.remove(documentId) }
    }

    suspend fun uploadImages(buildingId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))
        val imageEntities = imageService.uploadForBuilding(multiPartData)

        buildingEntity.images = SizedCollection(buildingEntity.images + imageEntities)
        handleCaches(buildingEntity)
    }

    suspend fun removeImages(buildingId: UUID, imageRequest: ImageRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))

        val imageIds =
            buildingEntity.images.map { it.id.value }.filter { imageId -> imageRequest.imageIds.contains(imageId) }
        imageIds.forEach { imageId -> imageService.remove(imageId) }

        handleCaches(buildingEntity)
    }

    suspend fun setImagePreview(buildingId: UUID, imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))

        val imageEntity = buildingEntity.images.find { it.id.value == imageId }
            ?: throw IllegalArgumentException("Building image not found")

        buildingEntity.images.filter { it.preview }.forEach { it.preview = false }
        imageEntity.apply {
            this.preview = true
        }
    }

    suspend fun uploadDocuments(buildingId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))
        val documentEntities = documentService.upload(multiPartData)

        buildingEntity.documents = SizedCollection(buildingEntity.documents + documentEntities)
        handleCaches(buildingEntity)
    }

    suspend fun removeDocuments(buildingId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val buildingEntity =
            (BuildingEntity.findById(buildingId) ?: throw IllegalArgumentException("Building not found"))
        val documentIds = buildingEntity.documents.map { it.id.value }
            .filter { documentId -> documentRequest.documentIds.contains(documentId) }

        documentIds.forEach { documentService.remove(it) }
        handleCaches(buildingEntity)
    }

    private fun handleCaches(buildingEntity: BuildingEntity) {
        val buildingKey = buildingKey.format(buildingEntity.id.value)
        if (redis.exists(buildingKey)) redis.del(buildingKey)

        val identity = buildingEntity.identity
        val identityKey = identityKey.format(identity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)
    }
}