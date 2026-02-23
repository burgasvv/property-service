package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.database.*
import org.burgas.service.BuildingService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureBuildingRouting() {

    val buildingService = BuildingService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/construction-service/buildings/create", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val buildingRequest = call.receive(BuildingRequest::class)
                val identityId = buildingRequest.identityId
                    ?: throw IllegalArgumentException("Building Request identityId not found for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for authentication")

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<BuildingRequest>("buildingRequest")] = buildingRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/construction-service/buildings/update", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")

                val buildingRequest = call.receive(BuildingRequest::class)
                val buildingId = buildingRequest.id
                    ?: throw IllegalArgumentException("Building Request id not found for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val buildingEntity = (BuildingEntity.findById(buildingId)
                        ?: throw IllegalArgumentException("Building not found for authentication"))
                        .load(BuildingEntity::identity)

                    if (buildingEntity.identity.email == principal.name) {
                        call.attributes[AttributeKey<BuildingRequest>("buildingRequest")] = buildingRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (
                call.request.path().equals("/api/v1/construction-service/buildings/delete", false) ||
                call.request.path().equals("/api/v1/construction-service/buildings/upload-images", false) ||
                call.request.path().equals("/api/v1/construction-service/buildings/remove-images", false) ||
                call.request.path().equals("/api/v1/construction-service/buildings/upload-documents", false) ||
                call.request.path().equals("/api/v1/construction-service/buildings/remove-documents", false) ||
                call.request.path().equals("/api/v1/construction-service/buildings/set-image-preview", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val buildingId = UUID.fromString(call.parameters["buildingId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val buildingEntity = (BuildingEntity.findById(buildingId)
                        ?: throw IllegalArgumentException("Building not found for authentication"))
                        .load(BuildingEntity::identity)

                    if (buildingEntity.identity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }
            }

            proceed()
        }

        route("/api/v1/construction-service/buildings") {

            get {
                call.respond(HttpStatusCode.OK, buildingService.findAll())
            }

            get("/by-id") {
                val buildingId = UUID.fromString(call.parameters["buildingId"])
                call.respond(HttpStatusCode.OK, buildingService.findById(buildingId))
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val buildingRequest = call.attributes[AttributeKey<BuildingRequest>("buildingRequest")]
                    buildingService.create(buildingRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val buildingRequest = call.attributes[AttributeKey<BuildingRequest>("buildingRequest")]
                    buildingService.update(buildingRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    buildingService.delete(buildingId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-images") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    buildingService.uploadImages(buildingId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-images") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    val imageRequest = call.receive(ImageRequest::class)
                    buildingService.removeImages(buildingId, imageRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-documents") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    buildingService.uploadDocuments(buildingId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-documents") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    val documentRequest = call.receive(DocumentRequest::class)
                    buildingService.removeDocuments(buildingId, documentRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/set-image-preview") {
                    val buildingId = UUID.fromString(call.parameters["buildingId"])
                    val imageId = UUID.fromString(call.parameters["imageId"])
                    buildingService.setImagePreview(buildingId, imageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}