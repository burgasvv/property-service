package org.burgas.gatewayserver.router

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

@Configuration
class RouterConfig {

    private final val rentalServiceUri = "http://localhost:9000"
    private final val constructionServiceUri = "http://localhost:9010"

    @Bean
    fun routerLocator(routeLocatorBuilder: RouteLocatorBuilder): RouteLocator {
        return routeLocatorBuilder.routes()
            .route {
                it
                    .path("/api/v1/rental-service/**")
                    .filters { filterSpec -> filterSpec.addRequestHeader(HttpHeaders.ORIGIN, rentalServiceUri) }
                    .uri(rentalServiceUri)
            }
            .route {
                it
                    .path("/api/v1/construction-service/**")
                    .filters { filterSpec -> filterSpec.addRequestHeader(HttpHeaders.ORIGIN, constructionServiceUri) }
                    .uri(constructionServiceUri)
            }
            .build()
    }
}