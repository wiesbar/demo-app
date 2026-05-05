package example.calculator

internal fun List<InfixToken>.toPostfix(): List<PostfixToken> = PostfixParser(this).parse()

private class PostfixParser(
    private val infix: List<InfixToken>,
) {
    private val output = mutableListOf<PostfixToken>()
    private val stack = ArrayDeque<InfixToken>()

    fun parse(): List<PostfixToken> {
        infix.forEachIndexed { index, token ->
            when (token) {
                is Operand -> output += token
                is BinaryOperator -> pushBinaryOperator(token)
                is UnaryOperator -> pushUnaryOperator(token)
                is Parenthesis.Left -> stack.addLast(token)
                is Parenthesis.Right -> handleRightParenthesis(token, index)
            }
        }
        drainRemainingOperators()
        return output.toList()
    }

    private fun pushBinaryOperator(current: BinaryOperator) {
        while (shouldPopBefore(current)) {
            output += stack.removeLast() as Operator
        }
        stack.addLast(current)
    }

    private fun pushUnaryOperator(current: UnaryOperator) {
        stack.addLast(current)
    }

    private fun shouldPopBefore(current: BinaryOperator): Boolean =
        when (val top = stack.lastOrNull()) {
            is UnaryOperator -> true
            is BinaryOperator -> top.precedence >= current.precedence
            else -> false
        }

    private fun handleRightParenthesis(
        rightParenthesis: Parenthesis.Right,
        index: Int,
    ) {
        val previous: InfixToken? = infix.getOrNull(index - 1)
        if (previous is Parenthesis.Left) {
            throw InvalidArithmeticExpressionException(
                "Empty parentheses at position '${previous.positionInExpression}' in expression.",
            )
        }
        drainToLeftParenthesis(rightParenthesis)
    }

    private fun drainToLeftParenthesis(rightParenthesis: Parenthesis.Right) {
        while (!isLeftParenthesis()) {
            val top =
                stack.removeLastOrNull() as? Operator
                    ?: throw InvalidArithmeticExpressionException(
                        "Unmatched ')' at position '${rightParenthesis.positionInExpression}' in expression.",
                    )
            output += top
        }
        stack.removeLast()
    }

    private fun isLeftParenthesis(): Boolean = stack.lastOrNull() is Parenthesis.Left

    private fun drainRemainingOperators() {
        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            if (top !is Operator) {
                throw InvalidArithmeticExpressionException(
                    "Unmatched '(' at position '${top.positionInExpression}' in expression.",
                )
            }
            output += top
        }
    }
}
