package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.burgas.service.ImageService
import java.util.*

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
                val imageIds = call.receive<List<UUID>>()
                imageService.delete(imageIds)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}