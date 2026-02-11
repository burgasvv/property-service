package org.burgas.service

import org.burgas.database.CategoryEntity
import org.burgas.database.CategoryShortResponse

fun CategoryEntity.toCategoryShortResponse(): CategoryShortResponse {
    return CategoryShortResponse(
        id = this.id.value,
        name = this.name,
        description = this.description
    )
}