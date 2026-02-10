package org.burgas

import io.ktor.server.application.*
import org.burgas.database.configureDatabase
import org.burgas.routing.configureRouting
import org.burgas.serialization.configureSerialization
import org.burgas.service.configureImageRoutes

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
    configureDatabase()

    configureImageRoutes()
}
