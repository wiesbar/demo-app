package example.calculator

internal sealed interface Result {
    val value: Double
    val positionInExpression: Int
}

internal class Value(
    operand: Operand,
) : Result {
    override val value: Double = operand.value
    override val positionInExpression: Int = operand.positionInExpression
}

internal class Sum(
    left: Result,
    right: Result,
) : Result {
    override val value: Double = left.value + right.value
    override val positionInExpression: Int = right.positionInExpression
}

internal class Difference(
    left: Result,
    right: Result,
) : Result {
    override val value: Double = left.value - right.value
    override val positionInExpression: Int = right.positionInExpression
}

internal class Product(
    left: Result,
    right: Result,
) : Result {
    override val value: Double = left.value * right.value
    override val positionInExpression: Int = right.positionInExpression
}

internal class Negation(
    operator: UnaryOperator.Negate,
    operand: Result,
) : Result {
    override val value: Double = -operand.value
    override val positionInExpression: Int = operator.positionInExpression
}
