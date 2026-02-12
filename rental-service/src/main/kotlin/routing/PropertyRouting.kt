package org.burgas.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.database.DatabaseFactory
import org.burgas.database.DocumentRequest
import org.burgas.database.IdentityEntity
import org.burgas.database.ImageRequest
import org.burgas.database.PropertyEntity
import org.burgas.database.PropertyRequest
import org.burgas.service.PropertyService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configurePropertyRouting() {

    val propertyService = PropertyService()
    val propertyRequestWithParamPathList = listOf(
        "/api/v1/rental-service/properties/delete",
        "/api/v1/rental-service/properties/upload-images",
        "/api/v1/rental-service/properties/remove-images",
        "/api/v1/rental-service/properties/upload-documents",
        "/api/v1/rental-service/properties/remove-documents",
    )

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/rental-service/properties/create", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val propertyRequest = call.receive(PropertyRequest::class)
                val ownerId =
                    propertyRequest.ownerId ?: throw IllegalArgumentException("OwnerId is null for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(ownerId)
                        ?: throw IllegalArgumentException("Identity not found for authentication")

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<PropertyRequest>("propertyRequest")] = propertyRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/rental-service/properties/update", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val propertyRequest = call.receive(PropertyRequest::class)

                val propertyId = propertyRequest.id
                    ?: throw IllegalArgumentException("Property Request id not found for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val propertyEntity = PropertyEntity.findById(propertyId)
                        ?: throw IllegalArgumentException("Property not found for authentication")
                    val identityEntity = propertyEntity.owner

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<PropertyRequest>("propertyRequest")] = propertyRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (propertyRequestWithParamPathList.contains(call.request.path())) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val propertyId = UUID.fromString(call.parameters["propertyId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val propertyEntity = PropertyEntity.findById(propertyId)
                        ?: throw IllegalArgumentException("Property not found for authentication")

                    val identityEntity = propertyEntity.owner

                    if (identityEntity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }
            }

            proceed()
        }

        route("/api/v1/rental-service/properties") {

            get {
                call.respond(HttpStatusCode.OK, propertyService.findAll())
            }

            get("/by-id") {
                val propertyId = UUID.fromString(call.parameters["propertyId"])
                call.respond(HttpStatusCode.OK, propertyService.findById(propertyId))
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val propertyRequest = call.attributes[AttributeKey<PropertyRequest>("propertyRequest")]
                    propertyService.create(propertyRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val propertyRequest = call.attributes[AttributeKey<PropertyRequest>("propertyRequest")]
                    propertyService.update(propertyRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val propertyId = UUID.fromString(call.parameters["propertyId"])
                    propertyService.delete(propertyId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-images") {
                    val propertyId = UUID.fromString(call.parameters["propertyId"])
                    propertyService.uploadImages(propertyId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-images") {
                    val propertyId = UUID.fromString(call.parameters["propertyId"])
                    val imageRequest = call.receive(ImageRequest::class)
                    propertyService.removeImages(propertyId, imageRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-documents") {
                    val propertyId = UUID.fromString(call.parameters["propertyId"])
                    propertyService.uploadDocuments(propertyId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-documents") {
                    val propertyId = UUID.fromString(call.parameters["propertyId"])
                    val documentRequest = call.receive(DocumentRequest::class)
                    propertyService.removeDocuments(propertyId,documentRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}