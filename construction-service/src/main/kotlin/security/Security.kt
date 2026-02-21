package org.burgas.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

fun Application.configureSecurity() {

    authentication {

        basic(name = "basic-auth-all") {
            validate { credentials ->

                newSuspendedTransaction {
                    val identityEntity = IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                    if (
                        identityEntity != null && identityEntity.enabled &&
                        BCrypt.checkpw(credentials.password, identityEntity.password)
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)

                    } else {
                        null
                    }
                }
            }
        }

        basic(name = "basic-auth-admin") {
            validate { credentials ->

                newSuspendedTransaction {
                    val identityEntity = IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                    if (
                        identityEntity != null && identityEntity.enabled &&
                        BCrypt.checkpw(credentials.password, identityEntity.password) &&
                        identityEntity.authority == Authority.ADMIN
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)

                    } else {
                        null
                    }
                }
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val exceptionResponse = ExceptionResponse(
                status = HttpStatusCode.BadRequest.description,
                code = HttpStatusCode.BadRequest.value,
                message = cause.localizedMessage
            )
            call.respond(exceptionResponse)
        }
    }

    install(CSRF) {
        allowOrigin("http://localhost:9010")
        allowOrigin("http://localhost:8765")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Host)
        allowHeader("X-CSRF-Token")

        anyHost()
    }

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
    }
}

@Serializable
data class CsrfToken(@Serializable(with = UUIDSerializer::class) val token: UUID)