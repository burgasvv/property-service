package org.burgas

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.burgas.database.configureDatabase
import org.burgas.kafka.configureKafka
import org.burgas.routing.*
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
    configureKafka()

    configureSecurityRouting()
    configureImageRouting()
    configureDocumentRouting()
    configureIdentityRouting()
    configureCategoryRouting()
    configurePropertyRouting()
    configureAdvertisementRouting()
}
