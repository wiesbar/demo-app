# Arithmetic expression calculator

The `example.calculator` package implements a self-contained, non-Spring expression evaluator. It is exposed to the rest of the application as the `ArithmeticExpressionCalculator` interface, with `DefaultArithmeticExpressionCalculator` as the production implementation.

## Pipeline

The evaluator processes an input string in three stages:

1. **Tokenize** — `toInfix(expression)` (in `InfixParser.kt`) walks the input character-by-character and produces a list of `InfixToken`s. Whitespace is skipped. Each token records its position in the original expression so error messages can point at the offending character.
2. **Shunting-yard** — `.toPostfix()` (in `PostfixParser.kt`) converts the infix token list to postfix (Reverse Polish Notation) using Dijkstra's shunting-yard algorithm.
3. **Evaluate** — `Postfix.calculate()` (in `Postfix.kt`) walks the postfix list with a value stack and reduces it to a single `Double`.

`Tokens.kt` defines the sealed `Token` hierarchy: `Operand`, `BinaryOperator.{Plus, Minus, Times}`, `UnaryOperator.Negate`, `Parenthesis.{Left, Right}`.

## Supported syntax

- Integer and decimal literals, including a leading `.` (e.g. `.5` is `0.5`).
- Whitespace anywhere between tokens.
- Binary operators: `+`, `-`, `*`. `*` has higher precedence than `+` and `-`. All binary operators are left-associative.
- Unary minus: `-` immediately before a number, an opening parenthesis, or another operator (e.g. `-3`, `-(1 + 2)`, `1 - -2`, `--1`). Unary minus binds tighter than any binary operator.
- Parentheses for grouping.

## Not supported

- Division (`/`) — there is no division operator yet.
- Unary plus (`+3`) — rejected as a missing-operand error.
- Identifiers, function calls, scientific notation.

## Examples

| Expression       | Result |
|------------------|--------|
| `1 + 2`          | `3.0`  |
| `2 + 3 * 4`      | `14.0` |
| `(2 + 3) * 4`    | `20.0` |
| `1 - 2 + 3`      | `2.0`  |
| `-(1 + 2)`       | `-3.0` |
| `1 + -(2 - 3)`   | `2.0`  |
| `--1`            | `1.0`  |
| `.5 + 1`         | `1.5`  |

## Errors

Invalid input throws `InvalidArithmeticExpressionException` (a `RuntimeException` declared in the calculator package). The HTTP layer's `GlobalExceptionHandler` maps this to `400 Bad Request`. Each error message names the offending character or operator and its position in the expression — for example:

- `""` → *Invalid empty expression.*
- `"1 +"` → *Missing operand for '+' operator at position '2' in expression.*
- `"a + b"` → *Invalid character 'a' at position '0' in expression.*
- `"(1 + 2"` → *Unmatched '(' at position '0' in expression.*
- `"()"` → *Empty parentheses at position '0' in expression.*
- `"1..2"` → *Invalid number literal '1..2' at position '0' in expression.*
