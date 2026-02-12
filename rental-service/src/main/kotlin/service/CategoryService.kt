package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.database.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

fun CategoryEntity.insert(categoryRequest: CategoryRequest) {
    this.name = categoryRequest.name ?: throw IllegalArgumentException("Category name is null")
    this.description = categoryRequest.description ?: throw IllegalArgumentException("Category description is null")
}

fun CategoryEntity.update(categoryRequest: CategoryRequest) {
    this.name = categoryRequest.name ?: this.name
    this.description = categoryRequest.description ?: this.description
}

fun CategoryEntity.toCategoryShortResponse(): CategoryShortResponse {
    return CategoryShortResponse(
        id = this.id.value,
        name = this.name,
        description = this.description
    )
}

fun CategoryEntity.toCategoryFullResponse(): CategoryFullResponse {
    return CategoryFullResponse(
        id = this.id.value,
        name = this.name,
        description = this.description,
        properties = this.properties.map { it.toPropertyShortResponse() }
    )
}

class CategoryService {

    private val redis = DatabaseFactory.redis
    private val categoryKey = "categoryFullResponse::%s"
    private val propertyKey = "propertyFullResponse::%s"

    suspend fun create(categoryRequest: CategoryRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        CategoryEntity.new { this.insert(categoryRequest) }.load(CategoryEntity::properties)
            .toCategoryFullResponse()
    }

    suspend fun findAll() = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CategoryEntity.all().map { it.toCategoryShortResponse() }
    }

    suspend fun findById(categoryId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val categoryKey = categoryKey.format(categoryId)
        val categoryString = redis.get(categoryKey)

        if (categoryString != null) {
            Json.decodeFromString<CategoryFullResponse>(categoryString)
        } else {
            val categoryFullResponse =
                (CategoryEntity.findById(categoryId) ?: throw IllegalArgumentException("Category not found"))
                    .toCategoryFullResponse()
            redis.set(categoryKey, Json.encodeToString(categoryFullResponse))
            categoryFullResponse
        }
    }

    suspend fun update(categoryRequest: CategoryRequest) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (categoryRequest.id == null) throw IllegalArgumentException("Category Request id is null")

        val categoryEntity = (CategoryEntity.findByIdAndUpdate(categoryRequest.id) { it.update(categoryRequest) }
            ?: throw IllegalArgumentException("Category not found and not updated"))

        handleCaches(categoryEntity)
    }

    suspend fun delete(categoryId: UUID) = newSuspendedTransaction(
        db = DatabaseFactory.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val categoryEntity = CategoryEntity.findById(categoryId) ?: throw IllegalArgumentException("Category not found")
        handleCaches(categoryEntity)
        categoryEntity.delete()
    }

    private fun handleCaches(categoryEntity: CategoryEntity) {
        val categoryKey = categoryKey.format(categoryEntity.id.value)
        if (redis.exists(categoryKey)) redis.del(categoryKey)

        if (!categoryEntity.properties.empty()) {

            categoryEntity.properties.forEach {
                val propertyKey = propertyKey.format(it.id.value)
                if (redis.exists(propertyKey)) redis.del(propertyKey)
            }
        }
    }
}