package org.burgas.service

import org.burgas.database.PropertyEntity
import org.burgas.database.PropertyWithCategoryResponse

fun PropertyEntity.toPropertyWithCategoryResponse(): PropertyWithCategoryResponse {
    return PropertyWithCategoryResponse(
        id = this.id.value,
        category = this.category?.toCategoryShortResponse(),
        name = this.name,
        address = this.address,
        description = this.description
    )
}