package example.calculator

internal sealed interface Token {
    val positionInExpression: Int
}

internal sealed interface InfixToken : Token

internal sealed interface PostfixToken : Token

internal sealed interface Operator :
    InfixToken,
    PostfixToken {
    val symbol: Char
    val locationInExpression: String
        get() = "'$symbol' operator at position '$positionInExpression' in expression."
}

internal class Operand(
    val value: Double,
    override val positionInExpression: Int,
) : InfixToken,
    PostfixToken

internal sealed class BinaryOperator(
    val precedence: Int,
    override val symbol: Char,
) : Operator {
    class Plus(
        override val positionInExpression: Int,
    ) : BinaryOperator(1, '+')

    class Minus(
        override val positionInExpression: Int,
    ) : BinaryOperator(1, '-')

    class Times(
        override val positionInExpression: Int,
    ) : BinaryOperator(2, '*')
}

internal sealed class UnaryOperator(
    override val symbol: Char,
) : Operator {
    class Negate(
        override val positionInExpression: Int,
    ) : UnaryOperator('-')
}

internal sealed class Parenthesis : InfixToken {
    class Left(
        override val positionInExpression: Int,
    ) : Parenthesis()

    class Right(
        override val positionInExpression: Int,
    ) : Parenthesis()
}
