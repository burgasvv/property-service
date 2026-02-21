package org.burgas.service

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.io.readByteArray
import org.burgas.database.DatabaseFactory
import org.burgas.database.DocumentEntity
import org.burgas.database.DocumentResponse
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

fun DocumentEntity.toDocumentResponse(): DocumentResponse {
    return DocumentResponse(
        id = this.id.value,
        name = this.name,
        contentType = this.contentType
    )
}

class DocumentService {

    suspend fun findById(documentId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        DocumentEntity.findById(documentId) ?: throw IllegalArgumentException("Document not found")
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val documentResponses: MutableList<DocumentResponse> = mutableListOf()

        multiPartData.forEachPart { partData ->

            if (partData.contentType?.contentType != "image")
                throw IllegalArgumentException("Wrong part data content type")

            if (partData is PartData.FileItem) {

                val documentResponse = DocumentEntity.new {
                    this.name = partData.originalFileName ?: throw IllegalArgumentException("Part data name is null")
                    this.contentType = "${partData.contentType?.contentType}/${partData.contentType?.contentSubtype}"
                    this.data = ExposedBlob(partData.provider.invoke().readBuffer.readByteArray())
                }
                    .toDocumentResponse()

                documentResponses.add(documentResponse)
            }
        }

        documentResponses
    }

    suspend fun remove(documentId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        (DocumentEntity.findById(documentId) ?: throw IllegalArgumentException("Document not found")).delete()
    }
}