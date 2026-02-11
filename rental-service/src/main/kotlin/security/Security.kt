package org.burgas.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.form
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.database.DatabaseFactory
import org.burgas.database.IdentityEntity
import org.burgas.database.IdentityTable
import org.burgas.serialization.UUIDSerializer
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class CsrfToken(
    @Serializable(with = UUIDSerializer::class)
    val token: UUID
)

fun Application.configureSecurity() {

    authentication {

        basic(name = "basic-auth-all") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
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

        basic("basic-auth-admin") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction(
                    db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
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
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)

        allowHeader("X-CSRF-Token")
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Host)

        anyHost()
    }

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
    }

    routing {

        route("/api/v1/rental-service/security") {

            get("/csrf-token") {
                val csrfToken = call.sessions.get(CsrfToken::class)
                if (csrfToken != null) {
                    call.respond(HttpStatusCode.OK, csrfToken)
                } else {
                    val newToken = CsrfToken(UUID.randomUUID())
                    call.sessions.set(newToken, CsrfToken::class)
                    call.respond(HttpStatusCode.OK, newToken)
                }
            }
        }
    }
}