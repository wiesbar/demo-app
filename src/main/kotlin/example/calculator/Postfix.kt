package example.calculator

internal fun List<PostfixToken>.calculate(): Double = Postfix(this).calculate()

private class Postfix(
    private val expression: List<PostfixToken>,
) {
    private val values = ArrayDeque<Result>()

    fun calculate(): Double {
        if (expression.isEmpty()) {
            throw InvalidArithmeticExpressionException("Invalid empty expression.")
        }
        for (token in expression) {
            when (token) {
                is Operand -> values.addLast(Value(token))
                is UnaryOperator.Negate -> token.calculate()
                is BinaryOperator -> token.calculate()
            }
        }
        if (values.size != 1) {
            throw InvalidArithmeticExpressionException(
                "Unexpected operand at position '${values.last().positionInExpression}' in expression.",
            )
        }
        return values.last().value
    }

    private fun UnaryOperator.Negate.calculate() {
        val value =
            values.removeLastOrNull()
                ?: throw InvalidArithmeticExpressionException("Missing operand for $locationInExpression")
        values.addLast(Negation(this, value))
    }

    private fun BinaryOperator.calculate() {
        if (values.size < 2) {
            throw InvalidArithmeticExpressionException("Missing operand for $locationInExpression")
        }
        val right = values.removeLast()
        val left = values.removeLast()
        val result =
            when (this) {
                is BinaryOperator.Plus -> Sum(left, right)
                is BinaryOperator.Minus -> Difference(left, right)
                is BinaryOperator.Times -> Product(left, right)
            }
        values.addLast(result)
    }
}
