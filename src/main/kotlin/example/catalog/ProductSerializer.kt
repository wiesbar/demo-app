package example.catalog

import tools.jackson.databind.ObjectMapper

internal class ProductSerializer(
    private val mapper: ObjectMapper,
) {
    fun serialize(product: Product): String {
        val doc =
            ProductDocument(
                category = product.category.name,
                name = product.name,
                description = product.description,
                dimensions = product.dimensions.toDoc(),
            )
        return mapper.writeValueAsString(doc)
    }
}

private fun Dimensions.toDoc(): DimensionsDoc = DimensionsDoc(width.toDoc(), height.toDoc(), depth.toDoc())

private fun Dimension.toDoc(): DimensionDoc = DimensionDoc(value, unit.name)
