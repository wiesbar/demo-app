package example.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

class TfIdfSemanticScorerTest :
    FunSpec({
        val scorer: SemanticScorer = FrozenTfIdfSemanticScorer()

        val oakArmchair = product("p1", Category.CHAIR, "Oak armchair", "Comfortable seating in oak.")
        val oakDining = product("p2", Category.TABLE, "Oak dining table", "Solid oak table, seats six.")
        val pineWorkbench = product("p3", Category.TABLE, "Pine workbench", "Sturdy pine surface for crafts.")
        val walnutChair = product("p4", Category.CHAIR, "Walnut chair", "Elegant walnut dining chair.")
        val velvetSofa = product("p5", Category.CHAIR, "Velvet sofa", "Plush three-seater sofa.")
        val corpus = listOf(oakArmchair, oakDining, pineWorkbench, walnutChair, velvetSofa)

        context("should rank the most semantically similar product first") {
            withData(
                nameFn = { (label, _, _) -> label },
                listOf(
                    Triple("direct match", "oak chair", "p1"),
                    Triple("synonym (couch -> sofa)", "couch", "p5"),
                    Triple("stem (chairs -> chair)", "chairs", "p4"),
                    Triple("case-insensitive", "OAK CHAIR", "p1"),
                    Triple("stopword filtered (the)", "the oak chair", "p1"),
                    Triple("two-term match", "dining table", "p2"),
                    Triple("two-term match (different product)", "pine workbench", "p3"),
                ),
            ) { (_, query, expectedTopId) ->
                topId(scorer, query, corpus) shouldBe expectedTopId
            }
        }

        test("should rank name match above description-only match") {
            val nameMatch = product("name", Category.CHAIR, "Oak chair", "Comfortable seating.")
            val descMatch = product("desc", Category.CHAIR, "Walnut chair", "Made of oak.")
            topId(scorer, "oak", listOf(nameMatch, descMatch)) shouldBe "name"
        }

        test("should produce zero cosine for queries that share no terms with the corpus") {
            val q = scorer.embedQuery("xyzzy")
            corpus.forEach { p ->
                val d = scorer.embedDocument(p.name, p.description)
                cosine(d, q) shouldBe 0.0
            }
        }

        test("should produce cosine scores in the unit interval") {
            val q = scorer.embedQuery("oak chair")
            corpus.forEach { p ->
                val score = cosine(scorer.embedDocument(p.name, p.description), q)
                score shouldBeGreaterThan -0.0001
                score shouldBeLessThanOrEqual 1.0
            }
        }

        test("should produce a vector whose length matches the scorer dimension") {
            scorer.embedDocument(oakArmchair.name, oakArmchair.description).size shouldBe scorer.dimension
            scorer.embedQuery("oak chair").size shouldBe scorer.dimension
        }
    })

private fun topId(
    scorer: SemanticScorer,
    query: String,
    corpus: List<Product>,
): String {
    val q = scorer.embedQuery(query)
    return corpus
        .map { p -> p.id to cosine(scorer.embedDocument(p.name, p.description), q) }
        .maxBy { it.second }
        .first
}

private fun cosine(
    a: FloatArray,
    b: FloatArray,
): Double {
    val dot = a.indices.sumOf { (a[it] * b[it]).toDouble() }
    val na = sqrt(a.sumOf { (it * it).toDouble() })
    val nb = sqrt(b.sumOf { (it * it).toDouble() })
    return if (na == 0.0 || nb == 0.0) 0.0 else dot / (na * nb)
}

private fun product(
    id: String,
    category: Category,
    name: String,
    description: String,
): Product =
    Product(
        id = id,
        category = category,
        name = name,
        description = description,
        dimensions =
            Dimensions(
                width = 100.centimeters,
                height = 80.centimeters,
                depth = 50.centimeters,
            ),
    )
