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
import org.burgas.service.AdvertisementService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureAdvertisementRouting() {

    val advertisementService = AdvertisementService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/rental-service/advertisements/create", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val advertisementRequest = call.receive(AdvertisementRequest::class)

                val propertyId = advertisementRequest.propertyId
                    ?: throw IllegalArgumentException("Advertisement propertyId is null for authenticate")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val propertyEntity = (PropertyEntity.findById(propertyId)
                        ?: throw IllegalArgumentException("Property not found for authentication"))
                        .load(PropertyEntity::owner)

                    val owner = propertyEntity.owner

                    if (owner.email == principal.name) {
                        call.attributes[AttributeKey<AdvertisementRequest>("advertisementRequest")] =
                            advertisementRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/rental-service/advertisements/update", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val advertisementRequest = call.receive(AdvertisementRequest::class)

                val advertisementId = advertisementRequest.id
                    ?: throw IllegalArgumentException("Advertisement Request id is null for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val advertisementEntity = (AdvertisementEntity.findById(advertisementId)
                        ?: throw IllegalArgumentException("Advertisement not found for authentication"))
                        .load(AdvertisementEntity::property)

                    val identityEntity = advertisementEntity.property.owner

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<AdvertisementRequest>("advertisementRequest")] =
                            advertisementRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/rental-service/advertisements/delete", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val advertisementId = UUID.fromString(call.parameters["advertisementId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val advertisementEntity = (AdvertisementEntity.findById(advertisementId)
                        ?: throw IllegalArgumentException("Advertisement not found for authentication"))
                        .load(AdvertisementEntity::property)

                    val identityEntity = advertisementEntity.property.owner

                    if (identityEntity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/rental-service/advertisements/rent-property", false)) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val rentPropertyRequest = call.receive(RentPropertyRequest::class)

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(rentPropertyRequest.tenantId)
                        ?: throw IllegalArgumentException("Identity not found for authentication")

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<RentPropertyRequest>("rentPropertyRequest")] = rentPropertyRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }
            }

            proceed()
        }

        route("/api/v1/rental-service/advertisements") {

            get {
                call.respond(HttpStatusCode.OK, advertisementService.findAll())
            }

            get("/by-id") {
                val advertisementId = UUID.fromString(call.parameters["advertisementId"])
                call.respond(HttpStatusCode.OK, advertisementService.findById(advertisementId))
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val advertisementRequest =
                        call.attributes[AttributeKey<AdvertisementRequest>("advertisementRequest")]
                    advertisementService.create(advertisementRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val advertisementRequest =
                        call.attributes[AttributeKey<AdvertisementRequest>("advertisementRequest")]
                    advertisementService.update(advertisementRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val advertisementId = UUID.fromString(call.parameters["advertisementId"])
                    advertisementService.delete(advertisementId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/rent-property") {
                    val rentPropertyRequest = call.attributes[AttributeKey<RentPropertyRequest>("rentPropertyRequest")]
                    advertisementService.rentPropertyByAdvertisement(rentPropertyRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}