package org.burgas.database

import io.ktor.server.application.*
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object ImageTable : UUIDTable("image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
    val preview = bool("preview")
}

class ImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, ImageEntity>(ImageTable)

    var name by ImageTable.name
    var contentType by ImageTable.contentType
    var data by ImageTable.data
    var preview by ImageTable.preview
}

object DocumentTable : UUIDTable("document") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
}

class DocumentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, DocumentEntity>(DocumentTable)

    var name by DocumentTable.name
    var contentType by DocumentTable.contentType
    var data by DocumentTable.data
}

object IdentityTable : UUIDTable("identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val username = varchar("username", 250).uniqueIndex()
    val password = varchar("password", 250)
    val email = varchar("email", 250).uniqueIndex()
    val enabled = bool("enabled").default(true)
    val firstname = varchar("firstname", 250)
    val lastname = varchar("lastname", 250)
    val patronymic = varchar("patronymic", 250)
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.CASCADE
    )
        .uniqueIndex()
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var username by IdentityTable.username
    var password by IdentityTable.password
    var email by IdentityTable.email
    var enabled by IdentityTable.enabled
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic
    var image by ImageEntity optionalReferencedOn IdentityTable.imageId
    val ownerProperties by PropertyEntity referrersOn PropertyTable.ownerId
    val tenantProperties by PropertyEntity optionalReferrersOn PropertyTable.tenantId
}

object CategoryTable : UUIDTable("category") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description").uniqueIndex()
}

class CategoryEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, CategoryEntity>(CategoryTable)

    var name by CategoryTable.name
    var description by CategoryTable.description
    val properties by PropertyEntity optionalReferrersOn PropertyTable.categoryId
}

object PropertyTable : UUIDTable("property") {
    val categoryId = optReference(
        name = "category_id", refColumn = CategoryTable.id,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.CASCADE
    )
    val name = varchar("name", 250)
    val address = text("address")
    val description = text("description")
    val ownerId = reference(
        name = "owner_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val tenantId = optReference(
        name = "tenant_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.CASCADE
    )
}

class PropertyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, PropertyEntity>(PropertyTable)

    var category by CategoryEntity optionalReferencedOn PropertyTable.categoryId
    var name by PropertyTable.name
    var address by PropertyTable.address
    var description by PropertyTable.description
    val advertisement by AdvertisementEntity optionalBackReferencedOn AdvertisementTable.propertyId
    var owner by IdentityEntity referencedOn PropertyTable.ownerId
    var tenant by IdentityEntity optionalReferencedOn PropertyTable.tenantId
    var images by ImageEntity via PropertyImageTable
    var documents by DocumentEntity via PropertyDocumentTable
}

object AdvertisementTable : UUIDTable("advertisement") {
    val title = varchar("title", 250)
    val description = text("description")
    val propertyId = reference(
        name = "property_id", refColumn = PropertyTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
        .uniqueIndex()
    val price = double("price").default(0.0).check { price -> price.greaterEq(0.0) }
    val date = date("date").clientDefault { LocalDate.now().toKotlinLocalDate() }
}

class AdvertisementEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, AdvertisementEntity>(AdvertisementTable)

    var title by AdvertisementTable.title
    var description by AdvertisementTable.description
    var property by PropertyEntity referencedOn AdvertisementTable.propertyId
    var price by AdvertisementTable.price
    var date by AdvertisementTable.date
}

object PropertyImageTable : Table("property_image") {
    val propertyId = reference(
        name = "property_id", refColumn = PropertyTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val imageId = reference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(propertyId, imageId))
}

object PropertyDocumentTable : Table("property_document") {
    val propertyId = reference(
        name = "property_id", refColumn = PropertyTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val documentId = reference(
        name = "document_id", refColumn = DocumentTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(propertyId, documentId))
}

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalUuidApi::class)
fun Application.configureDatabase() {
    transaction(db = DatabaseFactory.postgres) {
        SchemaUtils.create(
            ImageTable, DocumentTable, IdentityTable, CategoryTable,
            PropertyTable, AdvertisementTable, PropertyImageTable, PropertyDocumentTable
        )

        val identityId = Uuid.parse("b666b330-7e92-46b5-8239-4116c3d3802f").toJavaUuid()
        val identityEntity = IdentityEntity.findById(identityId) ?: IdentityEntity.new(identityId) {
            this.authority = Authority.ADMIN
            this.username = "burgasvv"
            this.password = BCrypt.hashpw("burgasvv", BCrypt.gensalt())
            this.email = "burgasvv@gmail.com"
            this.enabled = true
            this.firstname = "Бургас"
            this.lastname = "Вячеслав"
            this.patronymic = "Васильевич"
        }

        val categoryId = Uuid.parse("4ca2efb1-947f-4f21-851b-6b56e7b00a3f").toJavaUuid()
        val categoryEntity = CategoryEntity.findById(categoryId) ?: CategoryEntity.new(categoryId) {
            this.name = "Отели"
            this.description = "Описание категории Отели"
        }

        val propertyId = Uuid.parse("2d90e432-0a11-48f2-8832-b6682ba1a66a").toJavaUuid()
        val propertyEntity = PropertyEntity.findById(propertyId) ?: PropertyEntity.new(propertyId) {
            this.category = categoryEntity
            this.name = "Делеон"
            this.address = "г.Новосибирск, ул.Русская 175/1"
            this.description = "Описание недвижимости Делеон"
            this.owner = identityEntity
        }

        val advertisementId = Uuid.parse("669ef2e9-bbe8-4c7c-9c72-b0c35ea675a1").toJavaUuid()
        AdvertisementEntity.findById(advertisementId) ?: AdvertisementEntity.new(advertisementId) {
            this.title = "Аренда отеля Делеон"
            this.description = "Вся информация об аренде отеля Делеон"
            this.property = propertyEntity
            this.price = 340500.50
            this.date = LocalDate.now().toKotlinLocalDate()
        }
    }
}

@Serializable
data class ImageResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
)

@Serializable
data class DocumentResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
)

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val enabled: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
)

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val image: ImageResponse? = null
)

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val image: ImageResponse? = null,
    val ownerProperties: List<PropertyWithCategoryResponse>? = null,
    val tenantProperties: List<PropertyWithCategoryResponse>? = null
)

@Serializable
data class CategoryRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class CategoryShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class CategoryFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val properties: List<PropertyShortResponse>? = null
)

@Serializable
data class PropertyRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val categoryId: UUID? = null,
    val name: String? = null,
    val address: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val ownerId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val tenantId: UUID? = null
)

@Serializable
data class PropertyShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val name: String? = null,
    val address: String? = null,
    val description: String? = null,
    val images: List<ImageResponse>? = null,
    val documents: List<DocumentResponse>? = null
)

@Serializable
data class PropertyWithCategoryResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val category: CategoryShortResponse? = null,
    val name: String? = null,
    val address: String? = null,
    val description: String? = null,
    val images: List<ImageResponse>? = null,
    val documents: List<DocumentResponse>? = null
)

@Serializable
data class PropertyFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val category: CategoryShortResponse? = null,
    val name: String? = null,
    val address: String? = null,
    val description: String? = null,
    val advertisement: AdvertisementShortResponse? = null,
    val owner: IdentityShortResponse? = null,
    val tenant: IdentityShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val documents: List<DocumentResponse>? = null
)

@Serializable
data class AdvertisementRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val title: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val propertyId: UUID? = null,
    val price: Double? = null,
    val date: kotlinx.datetime.LocalDate? = null
)

@Serializable
data class AdvertisementShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val date: String? = null
)

@Serializable
data class AdvertisementFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val title: String? = null,
    val description: String? = null,
    val property: PropertyWithCategoryResponse? = null,
    val price: Double? = null,
    val date: String? = null
)

@Serializable
data class ImageRequest(
    val imageIds: List<@Serializable(with = UUIDSerializer::class) UUID>
)

@Serializable
data class DocumentRequest(
    val documentIds: List<@Serializable(with = UUIDSerializer::class) UUID>
)

@Serializable
data class RentPropertyRequest(
    @Serializable(with = UUIDSerializer::class)
    val tenantId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val advertisementId: UUID
)