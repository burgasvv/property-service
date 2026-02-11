package org.burgas.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.ImageService
import java.util.UUID

fun Application.configureImageRouting() {

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