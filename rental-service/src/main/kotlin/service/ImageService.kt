package org.burgas.service

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.ImageEntity
import org.burgas.database.ImageResponse
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

fun ImageEntity.toImageResponse(): ImageResponse {
    return ImageResponse(
        id = this.id.value,
        name = this.name,
        contentType = this.contentType,
        preview = this.preview
    )
}

class ImageService {

    suspend fun findById(imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ImageEntity.findById(imageId) ?: throw IllegalArgumentException("Image not found")
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val imageResponses = mutableListOf<ImageEntity>()
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) {
                val imageResponse = ImageEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.preview = false
                    this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                }
                imageResponses.add(imageResponse)
            }
        }
        imageResponses
    }

    suspend fun delete(imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (ImageEntity.findById(imageId) ?: throw IllegalArgumentException("Image not found")).delete()
    }
}