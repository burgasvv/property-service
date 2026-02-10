package org.burgas

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {

    val httpClient = HttpClient()

    routing {

        route("/api/v1/rental-service/{...}") {
            handle {
                val targetUrl = "http://localhost:9000${call.request.path()}"
                val httpResponse = httpClient.request(targetUrl) {
                    parameters { appendAll(call.parameters) }
                    headers { appendAll(call.request.headers) }
                    setBody(call.receiveChannel())
                }
                call.respondBytes(ContentType.Application.Json) { httpResponse.readRawBytes() }
            }
        }
    }
}
