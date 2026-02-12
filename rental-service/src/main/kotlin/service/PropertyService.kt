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

fun PropertyEntity.insert(propertyRequest: PropertyRequest) {
    this.category = CategoryEntity.findById(
        propertyRequest.categoryId ?: throw IllegalArgumentException("Property categoryId is null")
    )
        ?: throw IllegalArgumentException("Category not found")
    this.name = propertyRequest.name ?: throw IllegalArgumentException("Property name is null")
    this.address = propertyRequest.address ?: throw IllegalArgumentException("Property address is null")
    this.description = propertyRequest.description ?: throw IllegalArgumentException("Property description is null")
    this.owner = IdentityEntity.findById(
        propertyRequest.ownerId ?: throw IllegalArgumentException("Property ownerId is null")
    )
        ?: throw IllegalArgumentException("Owner not found")
    this.tenant = IdentityEntity.findById(propertyRequest.tenantId ?: UUID(0, 0)) ?: this.tenant
}

fun PropertyEntity.update(propertyRequest: PropertyRequest) {
    this.category = CategoryEntity.findById(propertyRequest.categoryId ?: UUID(0, 0)) ?: this.category
    this.name = propertyRequest.name ?: this.name
    this.address = propertyRequest.address ?: this.address
    this.description = propertyRequest.description ?: this.description
    this.owner = IdentityEntity.findById(propertyRequest.ownerId ?: UUID(0, 0)) ?: this.owner
    this.tenant = IdentityEntity.findById(propertyRequest.tenantId ?: UUID(0, 0)) ?: this.tenant
}

fun PropertyEntity.toPropertyShortResponse(): PropertyShortResponse {
    return PropertyShortResponse(
        id = this.id.value,
        name = this.name,
        address = this.address,
        description = this.description,
        images = this.images.map { it.toImageResponse() },
        documents = this.documents.map { it.toDocumentResponse() }
    )
}

fun PropertyEntity.toPropertyWithCategoryResponse(): PropertyWithCategoryResponse {
    return PropertyWithCategoryResponse(
        id = this.id.value,
        category = this.category?.toCategoryShortResponse(),
        name = this.name,
        address = this.address,
        description = this.description
    )
}

fun PropertyEntity.toPropertyFullResponse(): PropertyFullResponse {
    return PropertyFullResponse(
        id = this.id.value,
        category = this.category?.toCategoryShortResponse(),
        name = this.name,
        address = this.address,
        description = this.description,
        advertisement = this.advertisement?.toAdvertisementShortResponse(),
        owner = this.owner.toIdentityShortResponse(),
        tenant = this.tenant?.toIdentityShortResponse(),
        images = this.images.map { it.toImageResponse() },
        documents = this.documents.map { it.toDocumentResponse() }
    )
}

class PropertyService {

    private val imageService = ImageService()
    private val documentService = DocumentService()

    private val redis = DatabaseFactory.redis
    private val propertyKey = "propertyFullResponse::%s"
    private val categoryKey = "categoryFullResponse::%s"
    private val advertisementKey = "advertisementFullResponse::%s"
    private val identityKey = "identityFullResponse::%s"

    suspend fun create(propertyRequest: PropertyRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        PropertyEntity.new { this.insert(propertyRequest) }
            .load(
                PropertyEntity::category, PropertyEntity::advertisement,
                PropertyEntity::owner, PropertyEntity::tenant,
                PropertyEntity::images, PropertyEntity::documents
            )
            .toPropertyFullResponse()
    }

    suspend fun findAll() = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        PropertyEntity.all().with(PropertyEntity::category, PropertyEntity::images, PropertyEntity::documents)
            .map { it.toPropertyWithCategoryResponse() }
    }

    suspend fun findById(propertyId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val propertyKey = propertyKey.format(propertyId)
        val propertyString = redis.get(propertyKey)

        if (propertyString != null) {
            Json.decodeFromString<PropertyFullResponse>(propertyKey)
        } else {
            val propertyFullResponse =
                (PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("Property not found"))
                    .load(
                        PropertyEntity::category, PropertyEntity::advertisement,
                        PropertyEntity::owner, PropertyEntity::tenant,
                        PropertyEntity::images, PropertyEntity::documents
                    )
                    .toPropertyFullResponse()
            redis.set(propertyKey, Json.encodeToString(propertyFullResponse))
            propertyFullResponse
        }
    }

    suspend fun update(propertyRequest: PropertyRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (propertyRequest.id == null) throw IllegalArgumentException("Property Request id is null")
        val propertyEntity = (PropertyEntity.findByIdAndUpdate(propertyRequest.id) { it.update(propertyRequest) }
            ?: throw IllegalArgumentException("Property not found and not updated"))

        handleCaches(propertyEntity)
    }

    suspend fun delete(propertyId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val propertyEntity = PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("property not found")
        handleCaches(propertyEntity)
        propertyEntity.delete()
    }

    suspend fun uploadImages(propertyId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val propertyEntity = PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("Property not found")
        val imageEntities = imageService.upload(multiPartData)
        propertyEntity.images = SizedCollection(propertyEntity.images + imageEntities)
        handleCaches(propertyEntity)
    }

    suspend fun removeImages(propertyId: UUID, imageRequest: ImageRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val propertyEntity = PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("Property not found")
        val filteredImageIds = propertyEntity.images.map { it.id.value }.filter { imageRequest.imageIds.contains(it) }
        imageService.delete(filteredImageIds)
        handleCaches(propertyEntity)
    }

    suspend fun uploadDocuments(propertyId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val propertyEntity = PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("Property not found")
        val documentEntities = documentService.upload(multiPartData)
        propertyEntity.documents = SizedCollection(propertyEntity.documents + documentEntities)
        handleCaches(propertyEntity)
    }

    suspend fun removeDocuments(propertyId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val propertyEntity = PropertyEntity.findById(propertyId) ?: throw IllegalArgumentException("Property not found")
        val filteredDocumentIds =
            propertyEntity.documents.map { it.id.value }.filter { documentRequest.documentIds.contains(it) }
        documentService.delete(filteredDocumentIds)
        handleCaches(propertyEntity)
    }

    private fun handleCaches(propertyEntity: PropertyEntity) {
        val propertyKey = propertyKey.format(propertyEntity.id.value)
        if (redis.exists(propertyKey)) redis.del(propertyKey)

        val category = propertyEntity.category
        if (category != null) {
            val categoryKey = categoryKey.format(category.id.value)

            if (redis.exists(categoryKey)) redis.del(categoryKey)
        }

        val advertisement = propertyEntity.advertisement
        if (advertisement != null) {
            val advertisementKey = advertisementKey.format(advertisement.id.value)

            if (redis.exists(advertisementKey)) redis.del(advertisementKey)
        }

        val ownerEntity = propertyEntity.owner
        val ownerKey = identityKey.format(ownerEntity.id.value)
        if (redis.exists(ownerKey)) redis.del(ownerKey)

        val tenantEntity = propertyEntity.tenant
        if (tenantEntity != null) {
            val tenantKey = identityKey.format(tenantEntity.id.value)

            if (redis.exists(tenantKey)) redis.del(tenantKey)
        }
    }
}