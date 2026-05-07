package example.catalog

import kotlin.math.ln

internal const val MIN_STEMMABLE_LENGTH = 4
internal const val NAME_BOOST = 3.0

internal val STOP_WORDS =
    setOf(
        "the",
        "a",
        "an",
        "and",
        "or",
        "of",
        "for",
        "in",
        "on",
        "with",
        "to",
        "is",
        "are",
        "this",
        "that",
        "very",
    )

internal val SYNONYMS =
    mapOf(
        "couch" to "sofa",
        "settee" to "sofa",
        "armchair" to "chair",
        "stool" to "chair",
    )

private val SEED_CORPUS =
    listOf(
        "Oak armchair" to "Comfortable seating in oak.",
        "Oak dining table" to "Solid oak table, seats six.",
        "Pine workbench" to "Sturdy pine surface for crafts.",
        "Walnut chair" to "Elegant walnut dining chair.",
        "Velvet sofa" to "Plush three-seater sofa.",
        "Velvet armchair" to "Plush comfortable seating.",
        "Pine chair" to "Lightweight pine chair for crafts.",
    )

internal fun normalize(text: String): List<String> =
    text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it !in STOP_WORDS }
        .map { stem(it) }
        .map { SYNONYMS[it] ?: it }

internal fun stem(token: String): String {
    val canStrip = token.endsWith("s") && token.length >= MIN_STEMMABLE_LENGTH
    return if (canStrip) token.dropLast(1) else token
}

internal object EmbeddingVocabulary {
    private val terms: List<String>
    private val indexByTerm: Map<String, Int>
    private val idfByIndex: DoubleArray

    init {
        val docTermSets = SEED_CORPUS.map { (name, desc) -> seedDocStems(name, desc) }
        terms = docTermSets.flatten().toSortedSet().toList()
        indexByTerm = terms.withIndex().associate { (i, t) -> t to i }
        idfByIndex = computeIdf(terms, docTermSets)
    }

    val size: Int get() = terms.size

    fun indexOf(term: String): Int? = indexByTerm[term]

    fun idfAt(index: Int): Double = idfByIndex[index]
}

private fun seedDocStems(
    name: String,
    description: String,
): Set<String> = (normalize(name) + normalize(description)).toSet()

private fun computeIdf(
    terms: List<String>,
    docTermSets: List<Set<String>>,
): DoubleArray {
    val n = docTermSets.size
    val df = HashMap<String, Int>()
    docTermSets.forEach { ts -> ts.forEach { t -> df.merge(t, 1, Int::plus) } }
    return DoubleArray(terms.size) { i -> idfFor(df[terms[i]] ?: 0, n) }
}

private fun idfFor(
    df: Int,
    n: Int,
): Double = ln((n + 1.0) / (df + 1.0)) + 1.0
