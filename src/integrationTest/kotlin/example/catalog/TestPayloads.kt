package example.catalog

import example.web.DimensionDto
import example.web.DimensionsDto
import example.web.ProductDto
import example.web.ProductWithIdDto
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

internal fun oakDiningTablePayload(
    category: String = "TABLE",
    name: String = "Oak dining table",
    description: String = "Solid oak, seats six.",
): String =
    """
    {
      "category": "$category",
      "name": "$name",
      "description": "$description",
      "dimensions": {
        "width":  { "value": 180, "unit": "Centimeter" },
        "height": { "value":  75, "unit": "Centimeter" },
        "depth":  { "value":  90, "unit": "Centimeter" }
      }
    }
    """.trimIndent()

@Suppress("MagicNumber")
internal fun oakDiningTableDto(
    id: String,
    name: String = "Oak dining table",
    description: String = "Solid oak, seats six.",
): ProductWithIdDto =
    ProductWithIdDto(
        id = id,
        product =
            ProductDto(
                category = Category.TABLE,
                name = name,
                description = description,
                dimensions = centimetreDimensions(180, 75, 90),
            ),
    )

@Suppress("MagicNumber")
internal fun catalogProductDto(
    id: String,
    category: Category,
    name: String,
    description: String,
): ProductWithIdDto =
    ProductWithIdDto(
        id = id,
        product =
            ProductDto(
                category = category,
                name = name,
                description = description,
                dimensions = centimetreDimensions(100, 80, 50),
            ),
    )

internal fun centimetreDimensions(
    width: Int,
    height: Int,
    depth: Int,
): DimensionsDto =
    DimensionsDto(
        width = DimensionDto(width, UnitOfMeasure.CENTIMETER),
        height = DimensionDto(height, UnitOfMeasure.CENTIMETER),
        depth = DimensionDto(depth, UnitOfMeasure.CENTIMETER),
    )

internal fun putProduct(
    client: RestTestClient,
    id: String,
    body: String,
) {
    client
        .put()
        .uri("/products/$id")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isNoContent
}
