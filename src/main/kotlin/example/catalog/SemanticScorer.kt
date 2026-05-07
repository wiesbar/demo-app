package example.catalog

internal interface SemanticScorer {
    val dimension: Int

    fun embedDocument(
        name: String,
        description: String,
    ): FloatArray

    fun embedQuery(query: String): FloatArray
}
