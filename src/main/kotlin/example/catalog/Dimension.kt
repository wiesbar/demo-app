package example.catalog

internal data class Dimension(
    val value: Int,
    val unit: UnitOfMeasure,
) {
    init {
        if (value <= 0) throw InvalidProductException("dimension value must be positive, got $value")
    }
}

internal data class Dimensions(
    val width: Dimension,
    val height: Dimension,
    val depth: Dimension,
)

internal val Int.centimeters: Dimension get() = Dimension(this, UnitOfMeasure.CENTIMETER)
internal val Int.millimeters: Dimension get() = Dimension(this, UnitOfMeasure.MILLIMETER)
