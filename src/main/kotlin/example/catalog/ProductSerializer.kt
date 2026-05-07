package example.catalog

import tools.jackson.databind.ObjectMapper

internal class ProductSerializer(
    private val mapper: ObjectMapper,
    private val scorer: SemanticScorer,
) {
    fun serialize(product: Product): String {
        // todo - refactor
        // `ProductSerializer` and `SemanticScorer` coupling
        // the serializer mixes JSON marshalling with embedding generation.
        // A `ProductDocumentFactory` that builds `ProductDocument` (embedding included)
        // and defers serialization to a plain mapper would separate concerns.
        // Current code returns `String`, bypassing `IndexQueryBuilder.withObject(...)`.

        val embedding = scorer.embedDocument(product.name, product.description)
        val embeddingList = if (embedding.any { it != 0.0f }) embedding.toList() else emptyList()
        val doc =
            ProductDocument(
                category = product.category.name,
                name = product.name,
                description = product.description,
                dimensions = product.dimensions.toDoc(),
                embedding = embeddingList,
            )
        return mapper.writeValueAsString(doc)
    }
}

private fun Dimensions.toDoc(): DimensionsDoc = DimensionsDoc(width.toDoc(), height.toDoc(), depth.toDoc())

private fun Dimension.toDoc(): DimensionDoc = DimensionDoc(value, unit.name)
