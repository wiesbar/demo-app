package example.catalog

import kotlin.math.sqrt

internal class FrozenTfIdfSemanticScorer : SemanticScorer {
    override val dimension: Int = EmbeddingVocabulary.size

    override fun embedDocument(
        name: String,
        description: String,
    ): FloatArray {
        val v = FloatArray(dimension)
        accumulate(v, normalize(name), NAME_BOOST)
        accumulate(v, normalize(description), 1.0)
        return l2Normalized(v)
    }

    override fun embedQuery(query: String): FloatArray {
        val v = FloatArray(dimension)
        accumulate(v, normalize(query), 1.0)
        return l2Normalized(v)
    }
}

private fun accumulate(
    target: FloatArray,
    tokens: List<String>,
    weight: Double,
) {
    tokens.forEach { token ->
        val idx = EmbeddingVocabulary.indexOf(token) ?: return@forEach
        target[idx] = (target[idx] + weight * EmbeddingVocabulary.idfAt(idx)).toFloat()
    }
}

private fun l2Normalized(v: FloatArray): FloatArray {
    val norm = sqrt(v.sumOf { (it * it).toDouble() })
    if (norm == 0.0) return v
    return FloatArray(v.size) { i -> (v[i] / norm).toFloat() }
}
