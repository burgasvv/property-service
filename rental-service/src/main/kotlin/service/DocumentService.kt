package org.burgas.service

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.DocumentEntity
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class DocumentService {

    suspend fun findById(documentId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        DocumentEntity.findById(documentId) ?: throw IllegalArgumentException("Document not found")
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) {
                DocumentEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                }
            }
        }
    }

    suspend fun delete(documentId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (DocumentEntity.findById(documentId) ?: throw IllegalArgumentException("Document not found")).delete()
    }
}

fun Application.configureDocumentRoutes() {

    val documentService = DocumentService()

    routing {

        route("/api/v1/rental-service/documents") {

            get("/by-id") {
                val documentId = UUID.fromString(call.pathParameters["documentId"])
                val documentEntity = documentService.findById(documentId)
                call.respondBytes(
                    bytes = documentEntity.data.bytes,
                    contentType = ContentType.parse(documentEntity.contentType),
                    status = HttpStatusCode.OK
                )
            }

            post("/upload") {
                documentService.upload(call.receiveMultipart())
                call.respond(HttpStatusCode.OK)
            }

            delete("/delete") {
                val documentId = UUID.fromString(call.pathParameters["documentId"])
                documentService.delete(documentId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}