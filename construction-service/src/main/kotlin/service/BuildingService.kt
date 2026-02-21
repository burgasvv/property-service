package org.burgas.service

import org.burgas.database.BuildingEntity
import org.burgas.database.BuildingFullResponse
import org.burgas.database.BuildingShortResponse

fun BuildingEntity.toBuildingShortResponse(): BuildingShortResponse {
    return BuildingShortResponse(
        id = this.id.value,
        address = this.address,
        materials = this.materials,
        floors = this.floors,
        onObject = this.onObject,
        description = this.description,
        built = this.built
    )
}

fun BuildingEntity.toBuildingFullResponse(): BuildingFullResponse {
    return BuildingFullResponse(
        id = this.id.value,
        address = this.address,
        materials = this.materials,
        floors = this.floors,
        onObject = this.onObject,
        description = this.description,
        identity = this.identity.toIdentityShortResponse(),
        built = this.built,
        images = this.images.map { it.toImageResponse() },
        documents = this.documents.map { it.toDocumentResponse() }
    )
}