package org.burgas.service

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.ImageEntity
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ImageService {

    suspend fun findById(imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ImageEntity.findById(imageId) ?: throw IllegalArgumentException("Image not found")
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) {
                ImageEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.preview = false
                    this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                }
            }
        }
    }

    suspend fun delete(imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (ImageEntity.findById(imageId) ?: throw IllegalArgumentException("Image not found")).delete()
    }
}

fun Application.configureImageRoutes() {

    val imageService = ImageService()

    routing {

        route("/api/v1/rental-service/images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val imageEntity = imageService.findById(imageId)
                call.respondBytes(
                    contentType = ContentType.parse(imageEntity.contentType),
                    bytes = imageEntity.data.bytes,
                    status = HttpStatusCode.OK
                )
            }

            post("/upload") {
                imageService.upload(call.receiveMultipart())
                call.respond(HttpStatusCode.OK)
            }

            delete("/delete") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                imageService.delete(imageId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}