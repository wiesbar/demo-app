package example.web

import com.fasterxml.jackson.annotation.JsonUnwrapped
import example.catalog.Category
import example.catalog.Dimension
import example.catalog.Dimensions
import example.catalog.Product
import example.catalog.SearchHit
import example.catalog.UnitOfMeasure

data class SearchResponseDto(
    val hits: List<SearchHitDto>,
    val total: Int,
)

data class SearchHitDto(
    val product: ProductWithIdDto,
    val score: Double,
)

data class ProductDto(
    val category: Category,
    val name: String,
    val description: String,
    val dimensions: DimensionsDto,
)

data class ProductWithIdDto(
    val id: String,
    @JsonUnwrapped val product: ProductDto,
)

data class DimensionsDto(
    val width: DimensionDto,
    val height: DimensionDto,
    val depth: DimensionDto,
)

data class DimensionDto(
    val value: Int,
    val unit: UnitOfMeasure,
)

internal fun SearchHit.toDto(): SearchHitDto = SearchHitDto(product.toDto(), score)

internal fun Product.toDto(): ProductWithIdDto = ProductWithIdDto(id, toProductDto())

internal fun Product.toProductDto(): ProductDto = ProductDto(category, name, description, dimensions.toDto())

internal fun ProductDto.toDomain(id: String): Product = Product(id, category, name, description, dimensions.toDomain())

internal fun Dimensions.toDto(): DimensionsDto = DimensionsDto(width.toDto(), height.toDto(), depth.toDto())

internal fun DimensionsDto.toDomain(): Dimensions = Dimensions(width.toDomain(), height.toDomain(), depth.toDomain())

internal fun Dimension.toDto(): DimensionDto = DimensionDto(value, unit)

internal fun DimensionDto.toDomain(): Dimension = Dimension(value, unit)
