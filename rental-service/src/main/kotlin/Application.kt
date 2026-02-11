package org.burgas

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.burgas.database.configureDatabase
import org.burgas.routing.configureCategoryRouting
import org.burgas.routing.configureDocumentRouting
import org.burgas.routing.configureIdentityRouting
import org.burgas.routing.configureImageRouting
import org.burgas.routing.configureSecurityRouting
import org.burgas.security.configureSecurity
import org.burgas.serialization.configureSerialization

fun main() {
    embeddedServer(
        factory = Netty,
        port = 9000,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureDatabase()

    configureSecurityRouting()
    configureImageRouting()
    configureDocumentRouting()
    configureIdentityRouting()
    configureCategoryRouting()
}
