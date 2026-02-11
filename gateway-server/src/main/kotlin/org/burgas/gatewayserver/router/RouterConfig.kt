package org.burgas.gatewayserver.router

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

@Configuration
class RouterConfig {

    private final val rentalServiceUri = "http://localhost:9000"

    @Bean
    fun routerLocator(routeLocatorBuilder: RouteLocatorBuilder): RouteLocator {
        return routeLocatorBuilder.routes()
            .route {
                it
                    .path("/api/v1/rental-service/**")
                    .filters { filterSpec -> filterSpec.addRequestHeader(HttpHeaders.ORIGIN, rentalServiceUri) }
                    .uri(rentalServiceUri)
            }.build()
    }
}