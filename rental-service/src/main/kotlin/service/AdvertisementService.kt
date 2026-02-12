package org.burgas.service

import kotlinx.datetime.toJavaLocalDate
import org.burgas.database.AdvertisementEntity
import org.burgas.database.AdvertisementShortResponse
import java.time.format.DateTimeFormatter

fun AdvertisementEntity.toAdvertisementShortResponse(): AdvertisementShortResponse {
    return AdvertisementShortResponse(
        id = this.id.value,
        title = this.title,
        description = this.description,
        price = this.price,
        date = this.date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
    )
}