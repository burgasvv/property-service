package org.burgas.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.intercept
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityRequest
import org.burgas.service.IdentityService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

fun Application.configureIdentityRouting() {

    val identityService = IdentityService()
    val requestPathWithParamList = listOf(
        "/api/v1/rental-service/identities/by-id",
        "/api/v1/rental-service/identities/delete",
        "/api/v1/rental-service/identities/upload-image",
        "/api/v1/rental-service/identities/remove-image"
    )
    val requestPathWithBodyList = listOf(
        "/api/v1/rental-service/identities/update",
        "/api/v1/rental-service/identities/change-password"
    )

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (requestPathWithParamList.contains(call.request.path())) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val identityId = UUID.fromString(call.parameters["identityId"])

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for authentication")

                    if (identityEntity.email == principal.name) {
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (requestPathWithBodyList.contains(call.request.path())) {

                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Principal not found for authentication")
                val identityRequest = call.receive(IdentityRequest::class)
                val identityId = identityRequest.id
                    ?: throw IllegalArgumentException("Identity Request id not found for authentication")

                newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(identityId)
                        ?: throw IllegalArgumentException("Identity not found for authentication")

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                        proceed()

                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }
            }

            proceed()
        }

        route("/api/v1/rental-service/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.OK)
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                put("/change-status") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }

                put("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.update(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/change-password") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    identityService.changePassword(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.uploadImage(identityId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.removeImage(identityId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}