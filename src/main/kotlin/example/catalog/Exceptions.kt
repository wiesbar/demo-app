package example.catalog

internal class InvalidProductException(
    message: String,
) : RuntimeException(message)

internal class ProductNotFoundException(
    id: String,
) : RuntimeException("product with id '$id' not found")
