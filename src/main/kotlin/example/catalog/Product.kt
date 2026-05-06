package example.catalog

enum class Category { TABLE, CHAIR }

internal data class Product(
    val id: String,
    val category: Category,
    val name: String,
    val description: String,
    val dimensions: Dimensions,
) {
    init {
        requireNonBlank(id, "product id must not be blank")
        requireNonBlank(name, "product name must not be blank")
        requireNonBlank(description, "product description must not be blank")
    }
}

private fun requireNonBlank(
    value: String,
    message: String,
) {
    if (value.isBlank()) throw InvalidProductException(message)
}

internal data class SearchHit(
    val product: Product,
    val score: Double,
)

internal interface FurnitureSearchEngine {
    suspend fun search(
        query: String,
        category: Category? = null,
        size: Int = 20,
    ): List<SearchHit>
}

internal interface FurnitureIndexer {
    suspend fun index(product: Product)

    suspend fun deleteIfExists(id: String): Boolean
}
