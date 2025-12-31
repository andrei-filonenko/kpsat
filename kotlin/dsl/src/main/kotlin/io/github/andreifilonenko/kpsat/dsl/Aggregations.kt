@file:Suppress("TooManyFunctions")

package io.github.andreifilonenko.kpsat.dsl

// ============ BASIC AGGREGATIONS ============

/**
 * Sum of a list of expressions.
 */
fun sum(exprs: List<Expr>): Expr =
    Expr(ExprNode.Sum(exprs.map { it.node }))

/**
 * Product of a list of expressions.
 */
fun prod(exprs: List<Expr>): Expr =
    Expr(ExprNode.Prod(exprs.map { it.node }))

/**
 * Minimum of a list of expressions.
 */
fun min(exprs: List<Expr>): Expr =
    Expr(ExprNode.Min(exprs.map { it.node }))

/**
 * Maximum of a list of expressions.
 */
fun max(exprs: List<Expr>): Expr =
    Expr(ExprNode.Max(exprs.map { it.node }))

// ============ AGGREGATIONS OVER RANGES ============

/**
 * Sum over a range with a lambda.
 * ```kotlin
 * sum(0 until n) { i -> costs[i] * vars[i] }
 * ```
 */
inline fun sum(range: IntRange, transform: (Int) -> Expr): Expr =
    sum(range.map(transform))

/**
 * Sum over a range with a lambda.
 */
inline fun sum(range: LongRange, crossinline transform: (Long) -> Expr): Expr =
    sum(range.map { transform(it) })

/**
 * Sum over an iterable with a lambda.
 */
inline fun <T> sum(items: Iterable<T>, transform: (T) -> Expr): Expr =
    sum(items.map(transform))

/**
 * Product over a range with a lambda.
 */
inline fun prod(range: IntRange, transform: (Int) -> Expr): Expr =
    prod(range.map(transform))

/**
 * Product over a range with a lambda.
 */
inline fun prod(range: LongRange, crossinline transform: (Long) -> Expr): Expr =
    prod(range.map { transform(it) })

/**
 * Product over an iterable with a lambda.
 */
inline fun <T> prod(items: Iterable<T>, transform: (T) -> Expr): Expr =
    prod(items.map(transform))

/**
 * Minimum over a range with a lambda.
 */
inline fun min(range: IntRange, transform: (Int) -> Expr): Expr =
    min(range.map(transform))

/**
 * Minimum over a range with a lambda.
 */
inline fun min(range: LongRange, crossinline transform: (Long) -> Expr): Expr =
    min(range.map { transform(it) })

/**
 * Minimum over an iterable with a lambda.
 */
inline fun <T> min(items: Iterable<T>, transform: (T) -> Expr): Expr =
    min(items.map(transform))

/**
 * Maximum over a range with a lambda.
 */
inline fun max(range: IntRange, transform: (Int) -> Expr): Expr =
    max(range.map(transform))

/**
 * Maximum over a range with a lambda.
 */
inline fun max(range: LongRange, crossinline transform: (Long) -> Expr): Expr =
    max(range.map { transform(it) })

/**
 * Maximum over an iterable with a lambda.
 */
inline fun <T> max(items: Iterable<T>, transform: (T) -> Expr): Expr =
    max(items.map(transform))

// ============ LOGICAL AGGREGATIONS ============

/**
 * AND of a list of expressions (conjunction).
 */
fun allOf(exprs: List<Expr>): Expr =
    Expr(ExprNode.And(exprs.map { it.node }))

/**
 * OR of a list of expressions (disjunction).
 */
fun anyOf(exprs: List<Expr>): Expr =
    Expr(ExprNode.Or(exprs.map { it.node }))

/**
 * XOR of a list of expressions.
 */
fun xorOf(exprs: List<Expr>): Expr =
    Expr(ExprNode.Xor(exprs.map { it.node }))

// ============ QUANTIFIERS ============

/**
 * Universal quantifier: all elements in range satisfy predicate.
 * ```kotlin
 * forAll(0 until n) { i -> vars[i] geq 0L }
 * ```
 */
inline fun forAll(range: IntRange, predicate: (Int) -> Expr): Expr =
    allOf(range.map(predicate))

/**
 * Universal quantifier over LongRange.
 */
inline fun forAll(range: LongRange, crossinline predicate: (Long) -> Expr): Expr =
    allOf(range.map { predicate(it) })

/**
 * Universal quantifier over an iterable.
 */
inline fun <T> forAll(items: Iterable<T>, predicate: (T) -> Expr): Expr =
    allOf(items.map(predicate))

/**
 * Existential quantifier: at least one element satisfies predicate.
 * ```kotlin
 * exists(0 until n) { i -> vars[i] eq targetValue }
 * ```
 */
inline fun exists(range: IntRange, predicate: (Int) -> Expr): Expr =
    anyOf(range.map(predicate))

/**
 * Existential quantifier over LongRange.
 */
inline fun exists(range: LongRange, crossinline predicate: (Long) -> Expr): Expr =
    anyOf(range.map { predicate(it) })

/**
 * Existential quantifier over an iterable.
 */
inline fun <T> exists(items: Iterable<T>, predicate: (T) -> Expr): Expr =
    anyOf(items.map(predicate))

// ============ COLLECTION-BASED AGGREGATIONS (for AST-based lazy evaluation) ============

/**
 * Sum over an array expression with a lambda.
 * Creates a SumOver AST node for lazy evaluation.
 */
fun sumOver(array: Expr, transform: (Expr) -> Expr): Expr =
    Expr(ExprNode.SumOver(array.node) { n -> transform(Expr(n)).node })

/**
 * Product over an array expression with a lambda.
 */
fun prodOver(array: Expr, transform: (Expr) -> Expr): Expr =
    Expr(ExprNode.ProdOver(array.node) { n -> transform(Expr(n)).node })

/**
 * Minimum over an array expression with a lambda.
 */
fun minOver(array: Expr, transform: (Expr) -> Expr): Expr =
    Expr(ExprNode.MinOver(array.node) { n -> transform(Expr(n)).node })

/**
 * Maximum over an array expression with a lambda.
 */
fun maxOver(array: Expr, transform: (Expr) -> Expr): Expr =
    Expr(ExprNode.MaxOver(array.node) { n -> transform(Expr(n)).node })

/**
 * ForAll over an array expression with a predicate.
 */
fun forAllOver(array: Expr, predicate: (Expr) -> Expr): Expr =
    Expr(ExprNode.AndOver(array.node) { n -> predicate(Expr(n)).node })

/**
 * Exists over an array expression with a predicate.
 */
fun existsOver(array: Expr, predicate: (Expr) -> Expr): Expr =
    Expr(ExprNode.OrOver(array.node) { n -> predicate(Expr(n)).node })

// ============ COUNTING ============

/**
 * Count how many expressions in a list are true.
 */
fun count(exprs: List<Expr>): Expr =
    sum(exprs)

/**
 * Count how many items in a range satisfy a predicate.
 */
inline fun count(range: IntRange, predicate: (Int) -> Expr): Expr =
    sum(range.map(predicate))

/**
 * Count how many items in a range satisfy a predicate.
 */
inline fun count(range: LongRange, crossinline predicate: (Long) -> Expr): Expr =
    sum(range.map { predicate(it) })

/**
 * Count how many items in an iterable satisfy a predicate.
 */
inline fun <T> count(items: Iterable<T>, predicate: (T) -> Expr): Expr =
    sum(items.map(predicate))

// ============ ALL DIFFERENT ============

/**
 * All expressions must have different values.
 * This creates O(n²) pairwise constraints.
 * The solver module provides a more efficient AllDifferent constraint.
 */
fun allDifferent(exprs: List<Expr>): Expr =
    when {
        exprs.size <= 1 -> Expr(ExprNode.Const(1))
        else -> allOf(
            exprs.flatMapIndexed { i, a ->
                exprs.drop(i + 1).map { b -> a neq b }
            }
        )
    }

// ============ EXACTLY/AT LEAST/AT MOST ============

/**
 * Exactly n of the expressions are true.
 */
fun exactly(n: Int, exprs: List<Expr>): Expr =
    count(exprs) eq n.toLong()

/**
 * At least n of the expressions are true.
 */
fun atLeast(n: Int, exprs: List<Expr>): Expr =
    count(exprs) geq n.toLong()

/**
 * At most n of the expressions are true.
 */
fun atMost(n: Int, exprs: List<Expr>): Expr =
    count(exprs) leq n.toLong()

