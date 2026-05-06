package example.catalog

internal data class ProductDocument(
    val category: String = "",
    val name: String = "",
    val description: String = "",
    val dimensions: DimensionsDoc = DimensionsDoc(),
) {
    fun toDomain(id: String): Product =
        Product(
            id = id,
            category = Category.valueOf(category),
            name = name,
            description = description,
            dimensions = dimensions.toDomain(),
        )
}

internal data class DimensionsDoc(
    val width: DimensionDoc = DimensionDoc(),
    val height: DimensionDoc = DimensionDoc(),
    val depth: DimensionDoc = DimensionDoc(),
)

internal data class DimensionDoc(
    val value: Int = 0,
    val unit: String = "",
)

private fun DimensionsDoc.toDomain(): Dimensions = Dimensions(width.toDomain(), height.toDomain(), depth.toDomain())

private fun DimensionDoc.toDomain(): Dimension = Dimension(value, UnitOfMeasure.valueOf(unit))
