package org.burgas

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.burgas.database.configureDatabase
import org.burgas.routing.*
import org.burgas.security.configureSecurity
import org.burgas.serialization.configureSerialization

fun main() {
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0",
        port = 9010,
        module = Application::module
    )
        .start(true)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureDatabase()

    configureSecurityRouting()
    configureImageRouting()
    configureDocumentRouting()
    configureIdentityRouting()
    configureBuildingRouting()
}
