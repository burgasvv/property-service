package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.burgas.service.ImageService
import java.util.*

fun Application.configureImageRouting() {

    val imageService = ImageService()

    routing {

        route("/api/v1/construction-service/images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val imageEntity = imageService.findById(imageId)
                call.respondBytes(ContentType.parse(imageEntity.contentType), HttpStatusCode.OK
                ) { imageEntity.data.bytes }
            }
        }
    }
}