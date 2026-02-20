package org.burgas.database

import io.ktor.server.application.*
import io.ktor.util.StringValues
import kotlinx.serialization.Serializable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

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

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable("identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val username = varchar("username", 255).uniqueIndex()
    val password = varchar("password", 255)
    val email = varchar("email", 255).uniqueIndex()
    val enabled = bool("enabled").default(true)
    val firstname = varchar("firstname", 255)
    val lastname = varchar("lastname", 255)
    val patronymic = varchar("patronymic", 255)
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
    val buildings by BuildingEntity referrersOn BuildingTable.identityId
}

object BuildingTable : UUIDTable("building") {
    val address = text("address").uniqueIndex()
    val materials = text("materials")
    val floors = integer("floors").default(0).check { floors -> floors.greaterEq(0) }
    val onObject = integer("on_object").default(0).check { onObject -> onObject.greaterEq(0) }
    val description = text("description")
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val built = bool("built").default(false)
}

class BuildingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, BuildingEntity>(BuildingTable)

    var address by BuildingTable.address
    var materials by BuildingTable.materials
    var floors by BuildingTable.floors
    var onObject by BuildingTable.onObject
    var description by BuildingTable.description
    var identity by IdentityEntity referencedOn BuildingTable.identityId
    var built by BuildingTable.built
    var images by ImageEntity via BuildingImageTable
    var documents by DocumentEntity via BuildingDocumentTable
}

object BuildingImageTable : Table("building_image") {
    val buildingId = reference(
        name = "building_id", refColumn = BuildingTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val imageId = reference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(buildingId, imageId))
}

object BuildingDocumentTable : Table("building_document") {
    val buildingId = reference(
        name = "building_id", refColumn = BuildingTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val documentId = reference(
        name = "document_id", refColumn = DocumentTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(buildingId, documentId))
}

@OptIn(ExperimentalUuidApi::class)
@Suppress("UnusedReceiverParameter")
fun Application.configureDatabase() {

    transaction(db = DatabaseFactory.postgres) {
        SchemaUtils.create(
            ImageTable, DocumentTable, IdentityTable, BuildingTable, BuildingImageTable, BuildingDocumentTable
        )

        val identityId = Uuid.parse("5c7334fd-8215-4abb-ba91-76609eac15b9").toJavaUuid()
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

        val buildingIdFirst = Uuid.parse("521fbf5e-76b2-4c53-abbb-f32a48dc8957").toJavaUuid()
        BuildingEntity.findById(buildingIdFirst) ?: BuildingEntity.new(buildingIdFirst) {
            this.address = "г.Новосибирск, ул.Русская, д.26"
            this.materials = "Материалы для строительства здания"
            this.floors = 15
            this.onObject = 25
            this.description = "Описание строительства данного здания"
            this.identity = identityEntity
            this.built = false
        }

        val buildingIdSecond = Uuid.parse("7450b799-f118-4303-a911-97a6c101dcdf").toJavaUuid()
        BuildingEntity.findById(buildingIdSecond) ?: BuildingEntity.new(buildingIdSecond) {
            this.address = "г.Москва, ул.Рябчинова, д.108/1"
            this.materials = "Материалы для строительства здания"
            this.floors = 25
            this.onObject = 45
            this.description = "Описание строительства данного здания"
            this.identity = identityEntity
            this.built = false
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
    val buildings: List<BuildingShortResponse>? = null
)

@Serializable
data class BuildingRequest(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val materials: String? = null,
    val floors: Int? = null,
    val onObject: Int? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val identityId: UUID? = null,
    val built: Boolean? = null
)

@Serializable
data class BuildingShortResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val materials: String? = null,
    val floors: Int? = null,
    val onObject: Int? = null,
    val description: String? = null,
    val built: Boolean? = null
)

data class BuildingFullResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val address: String? = null,
    val materials: String? = null,
    val floors: Int? = null,
    val onObject: Int? = null,
    val description: String? = null,
    val identity: IdentityShortResponse? = null,
    val built: Boolean? = null,
    val images: List<ImageResponse>? = null,
    val documents: List<DocumentResponse>? = null
)