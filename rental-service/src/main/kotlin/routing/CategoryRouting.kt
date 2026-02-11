package org.burgas.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.database.CategoryRequest
import org.burgas.service.CategoryService
import java.util.UUID

fun Application.configureCategoryRouting() {

    val categoryService = CategoryService()

    routing {

        route("/api/v1/rental-service/categories") {

            get {
                call.respond(HttpStatusCode.OK, categoryService.findAll())
            }

            get("/by-id") {
                val categoryId = UUID.fromString(call.parameters["categoryId"])
                call.respond(HttpStatusCode.OK, categoryService.findById(categoryId))
            }

            authenticate("basic-auth-admin") {

                post("/create") {
                    val categoryRequest = call.receive(CategoryRequest::class)
                    categoryService.create(categoryRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/update") {
                    val categoryRequest = call.receive(CategoryRequest::class)
                    categoryService.update(categoryRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val categoryId = UUID.fromString(call.parameters["categoryId"])
                    categoryService.delete(categoryId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}