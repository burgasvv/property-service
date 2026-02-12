package org.burgas.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.DocumentService
import java.util.UUID

fun Application.configureDocumentRouting() {

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
                val documentIds = call.receive<List<UUID>>()
                documentService.delete(documentIds)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}