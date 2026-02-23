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
    suspend fun uploadForIdentity(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val partData = multiPartData.readPart()
        if (partData != null) {

            if (partData.contentType?.contentType != "image")
                throw IllegalArgumentException("Wrong uploaded contentType")

            if (partData is PartData.FileItem) {

                ImageEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.data = ExposedBlob(partData.provider.invoke().readBuffer.readByteArray())
                    this.preview = true
                }

            } else {
                throw IllegalArgumentException("Part data is not FileItem")
            }

        } else {
            throw IllegalArgumentException("Part data is null")
        }
    }

    @OptIn(InternalAPI::class)
    suspend fun uploadForBuilding(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val imageEntities: MutableList<ImageEntity> = mutableListOf()
        multiPartData.forEachPart { partData ->

            if (partData.contentType?.contentType != "image")
                throw IllegalArgumentException("Wrong uploaded contentType")

            if (partData is PartData.FileItem) {

                val imageEntity = ImageEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.data = ExposedBlob(partData.provider.invoke().readBuffer.readByteArray())
                    this.preview = false
                }

                imageEntities.add(imageEntity)

            } else {
                throw IllegalArgumentException("Part Data is not FileItem")
            }
        }
        imageEntities
    }

    suspend fun remove(imageId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (ImageEntity.findById(imageId) ?: throw IllegalArgumentException("Image not found")).delete()
    }
}