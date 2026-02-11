package org.burgas.service

import org.burgas.database.PropertyEntity
import org.burgas.database.PropertyShortResponse
import org.burgas.database.PropertyWithCategoryResponse

fun PropertyEntity.toPropertyShortResponse(): PropertyShortResponse {
    return PropertyShortResponse(
        id = this.id.value,
        name = this.name,
        address = this.address,
        description = this.description,
        images = this.images.map { it.toImageResponse() },
        documents = this.documents.map { it.toDocumentResponse() }
    )
}

fun PropertyEntity.toPropertyWithCategoryResponse(): PropertyWithCategoryResponse {
    return PropertyWithCategoryResponse(
        id = this.id.value,
        category = this.category?.toCategoryShortResponse(),
        name = this.name,
        address = this.address,
        description = this.description
    )
}