@file:Suppress("TooManyFunctions")

package io.github.andreifilonenko.kpsat.dsl

/**
 * Core expression wrapper - wraps an ExprNode AST.
 * Operations on Expr build up an AST that can be:
 * 1. Compiled to solver expressions (CP-SAT, etc.)
 * 2. Evaluated directly for unit testing
 */
@JvmInline
value class Expr(val node: ExprNode)

/**
 * Intermediate type for `condition then ifTrue otherwise ifFalse` syntax.
 */
class ThenClause(
    internal val condition: Expr,
    internal val ifTrue: Expr,
) {
    infix fun otherwise(ifFalse: Expr): Expr = iif(condition, ifTrue, ifFalse)
    infix fun otherwise(ifFalse: Long): Expr = iif(condition, ifTrue, Expr(ExprNode.Const(ifFalse)))
}

/**
 * Intermediate type for `condition then ifTrueLong otherwise ifFalse` syntax.
 */
class ThenClauseLong(
    internal val condition: Expr,
    internal val ifTrue: Long,
) {
    infix fun otherwise(ifFalse: Expr): Expr = iif(condition, Expr(ExprNode.Const(ifTrue)), ifFalse)
    infix fun otherwise(ifFalse: Long): Expr = iif(condition, Expr(ExprNode.Const(ifTrue)), Expr(ExprNode.Const(ifFalse)))
}

// ============ ARITHMETIC OPERATORS ============

/** Addition: a + b */
operator fun Expr.plus(other: Expr): Expr =
    Expr(ExprNode.Sum(listOf(node, other.node)))

/** Addition: a + constant */
operator fun Expr.plus(other: Long): Expr =
    Expr(ExprNode.Sum(listOf(node, ExprNode.Const(other))))

/** Addition: a + constant */
operator fun Expr.plus(other: Double): Expr =
    Expr(ExprNode.Sum(listOf(node, ExprNode.ConstDouble(other))))

/** Addition: constant + a */
operator fun Long.plus(other: Expr): Expr =
    Expr(ExprNode.Sum(listOf(ExprNode.Const(this), other.node)))

/** Addition: constant + a */
operator fun Double.plus(other: Expr): Expr =
    Expr(ExprNode.Sum(listOf(ExprNode.ConstDouble(this), other.node)))

/** Multiplication: a * b */
operator fun Expr.times(other: Expr): Expr =
    Expr(ExprNode.Prod(listOf(node, other.node)))

/** Multiplication: a * constant */
operator fun Expr.times(other: Long): Expr =
    Expr(ExprNode.Prod(listOf(node, ExprNode.Const(other))))

/** Multiplication: a * constant */
operator fun Expr.times(other: Double): Expr =
    Expr(ExprNode.Prod(listOf(node, ExprNode.ConstDouble(other))))

/** Multiplication: constant * a */
operator fun Long.times(other: Expr): Expr =
    Expr(ExprNode.Prod(listOf(ExprNode.Const(this), other.node)))

/** Multiplication: constant * a */
operator fun Double.times(other: Expr): Expr =
    Expr(ExprNode.Prod(listOf(ExprNode.ConstDouble(this), other.node)))

/** Subtraction: a - b */
operator fun Expr.minus(other: Expr): Expr =
    Expr(ExprNode.Sub(node, other.node))

/** Subtraction: a - constant */
operator fun Expr.minus(other: Long): Expr =
    Expr(ExprNode.Sub(node, ExprNode.Const(other)))

/** Subtraction: constant - a */
operator fun Long.minus(other: Expr): Expr =
    Expr(ExprNode.Sub(ExprNode.Const(this), other.node))

/** Integer division: a / b */
operator fun Expr.div(other: Expr): Expr =
    Expr(ExprNode.Div(node, other.node))

/** Integer division: a / constant */
operator fun Expr.div(other: Long): Expr =
    Expr(ExprNode.Div(node, ExprNode.Const(other)))

/** Modulo: a % b */
operator fun Expr.rem(other: Expr): Expr =
    Expr(ExprNode.Mod(node, other.node))

/** Modulo: a % constant */
operator fun Expr.rem(other: Long): Expr =
    Expr(ExprNode.Mod(node, ExprNode.Const(other)))

/** Negation: -a */
operator fun Expr.unaryMinus(): Expr =
    Expr(ExprNode.Neg(node))

// ============ COMPARISON OPERATORS ============

/** Equality: a == b */
infix fun Expr.eq(other: Expr): Expr =
    Expr(ExprNode.Eq(node, other.node))

/** Equality: a == constant */
infix fun Expr.eq(other: Long): Expr =
    Expr(ExprNode.Eq(node, ExprNode.Const(other)))

/** Not equal: a != b */
infix fun Expr.neq(other: Expr): Expr =
    Expr(ExprNode.Neq(node, other.node))

/** Not equal: a != constant */
infix fun Expr.neq(other: Long): Expr =
    Expr(ExprNode.Neq(node, ExprNode.Const(other)))

/** Less than or equal: a <= b */
infix fun Expr.leq(other: Expr): Expr =
    Expr(ExprNode.Leq(node, other.node))

/** Less than or equal: a <= constant */
infix fun Expr.leq(other: Long): Expr =
    Expr(ExprNode.Leq(node, ExprNode.Const(other)))

/** Less than or equal: a <= constant */
infix fun Expr.leq(other: Double): Expr =
    Expr(ExprNode.Leq(node, ExprNode.ConstDouble(other)))

/** Greater than or equal: a >= b */
infix fun Expr.geq(other: Expr): Expr =
    Expr(ExprNode.Geq(node, other.node))

/** Greater than or equal: a >= constant */
infix fun Expr.geq(other: Long): Expr =
    Expr(ExprNode.Geq(node, ExprNode.Const(other)))

/** Greater than or equal: a >= constant */
infix fun Expr.geq(other: Double): Expr =
    Expr(ExprNode.Geq(node, ExprNode.ConstDouble(other)))

/** Less than: a < b */
infix fun Expr.lt(other: Expr): Expr =
    Expr(ExprNode.Lt(node, other.node))

/** Less than: a < constant */
infix fun Expr.lt(other: Long): Expr =
    Expr(ExprNode.Lt(node, ExprNode.Const(other)))

/** Greater than: a > b */
infix fun Expr.gt(other: Expr): Expr =
    Expr(ExprNode.Gt(node, other.node))

/** Greater than: a > constant */
infix fun Expr.gt(other: Long): Expr =
    Expr(ExprNode.Gt(node, ExprNode.Const(other)))

// ============ LOGICAL OPERATORS ============

/** Logical AND: a && b */
infix fun Expr.and(other: Expr): Expr =
    Expr(ExprNode.And(listOf(node, other.node)))

/** Logical OR: a || b */
infix fun Expr.or(other: Expr): Expr =
    Expr(ExprNode.Or(listOf(node, other.node)))

/** Logical XOR: a ^ b */
infix fun Expr.xor(other: Expr): Expr =
    Expr(ExprNode.Xor(listOf(node, other.node)))

/** Logical NOT: !a */
operator fun Expr.not(): Expr =
    Expr(ExprNode.Not(node))

/** Implication: a => b (equivalent to !a || b) */
infix fun Expr.implies(other: Expr): Expr = !this or other

/** Biconditional: a <=> b (equivalent to a == b for booleans) */
infix fun Expr.iff(other: Expr): Expr = this eq other

// ============ COLLECTION OPERATORS ============

/** Check if collection contains element */
fun Expr.containsElem(elem: Long): Expr =
    Expr(ExprNode.Contains(node, ExprNode.Const(elem)))

/** Check if collection contains element */
fun Expr.containsElem(elem: Expr): Expr =
    Expr(ExprNode.Contains(node, elem.node))

/** Check if value is in set: value isIn set */
infix fun Long.isIn(set: Expr): Expr =
    Expr(ExprNode.Contains(set.node, ExprNode.Const(this)))

/** Check if value is in set: value isIn set */
infix fun Expr.isIn(set: Expr): Expr =
    Expr(ExprNode.Contains(set.node, node))

/** Array access: array[index] */
operator fun Expr.get(index: Expr): Expr =
    Expr(ExprNode.At(node, index.node))

/** Array access: array[index] */
operator fun Expr.get(index: Long): Expr =
    Expr(ExprNode.At(node, ExprNode.Const(index)))

/** Array access: array[index] */
operator fun Expr.get(index: Int): Expr =
    Expr(ExprNode.At(node, ExprNode.Const(index.toLong())))

/** 2D array access: array[i, j] */
operator fun Expr.get(i: Expr, j: Expr): Expr =
    Expr(ExprNode.At2D(node, i.node, j.node))

/** 2D array access: array[i, j] */
operator fun Expr.get(i: Long, j: Long): Expr =
    Expr(ExprNode.At(ExprNode.At(node, ExprNode.Const(i)), ExprNode.Const(j)))

// ============ CONDITIONAL ============

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Expr, ifFalse: Expr): Expr =
    Expr(ExprNode.If(condition.node, ifTrue.node, ifFalse.node))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Long, ifFalse: Long): Expr =
    Expr(ExprNode.If(condition.node, ExprNode.Const(ifTrue), ExprNode.Const(ifFalse)))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Expr, ifFalse: Long): Expr =
    Expr(ExprNode.If(condition.node, ifTrue.node, ExprNode.Const(ifFalse)))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Long, ifFalse: Expr): Expr =
    Expr(ExprNode.If(condition.node, ExprNode.Const(ifTrue), ifFalse.node))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Double, ifFalse: Double): Expr =
    Expr(ExprNode.If(condition.node, ExprNode.ConstDouble(ifTrue), ExprNode.ConstDouble(ifFalse)))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Expr, ifFalse: Double): Expr =
    Expr(ExprNode.If(condition.node, ifTrue.node, ExprNode.ConstDouble(ifFalse)))

/** If-then-else: iif(cond, a, b) returns a if cond else b */
fun iif(condition: Expr, ifTrue: Double, ifFalse: Expr): Expr =
    Expr(ExprNode.If(condition.node, ExprNode.ConstDouble(ifTrue), ifFalse.node))

/** Fluent conditional: cond then a otherwise b */
infix fun Expr.then(ifTrue: Expr): ThenClause = ThenClause(this, ifTrue)

/** Fluent conditional: cond then a otherwise b */
infix fun Expr.then(ifTrue: Long): ThenClauseLong = ThenClauseLong(this, ifTrue)

/** Apply value only if condition is true, otherwise 0 */
infix fun Expr.onlyIf(condition: Expr): Expr = iif(condition, this, 0L)

/** Apply value only if condition is true, otherwise 0 */
infix fun Long.onlyIf(condition: Expr): Expr = iif(condition, this, 0L)

// ============ MATH FUNCTIONS ============

/** Absolute value: |x| */
fun abs(expr: Expr): Expr = Expr(ExprNode.Abs(expr.node))

/** Square root: sqrt(x) */
fun sqrt(expr: Expr): Expr = Expr(ExprNode.Sqrt(expr.node))

/** Power: base^exponent */
fun pow(base: Expr, exponent: Expr): Expr =
    Expr(ExprNode.Pow(base.node, exponent.node))

/** Power: base^exponent */
fun pow(base: Expr, exponent: Long): Expr =
    Expr(ExprNode.Pow(base.node, ExprNode.Const(exponent)))

/** Power: base^exponent */
fun pow(base: Expr, exponent: Double): Expr =
    Expr(ExprNode.Pow(base.node, ExprNode.ConstDouble(exponent)))

/** Exponential: e^x */
fun exp(expr: Expr): Expr = Expr(ExprNode.Exp(expr.node))

/** Natural logarithm: ln(x) */
fun log(expr: Expr): Expr = Expr(ExprNode.Log(expr.node))

/** Ceiling: smallest integer >= x */
fun ceil(expr: Expr): Expr = Expr(ExprNode.Ceil(expr.node))

/** Floor: largest integer <= x */
fun floor(expr: Expr): Expr = Expr(ExprNode.Floor(expr.node))

/** Round to nearest integer */
fun round(expr: Expr): Expr = Expr(ExprNode.Round(expr.node))

// ============ SET OPERATIONS ============

/** Intersection of multiple sets */
fun intersection(sets: List<Expr>): Expr =
    Expr(ExprNode.Intersection(sets.map { it.node }))

/** Set intersection: a ∩ b */
infix fun Expr.intersect(other: Expr): Expr =
    Expr(ExprNode.Intersection(listOf(node, other.node)))

/** Union of multiple sets */
fun union(sets: List<Expr>): Expr =
    Expr(ExprNode.Union(sets.map { it.node }))

/** Set union: a ∪ b */
infix fun Expr.union(other: Expr): Expr =
    Expr(ExprNode.Union(listOf(node, other.node)))

/** Cardinality: number of elements in set */
fun Expr.card(): Expr = Expr(ExprNode.Count(node))

/** Check if sets are disjoint (no common elements) */
infix fun Expr.disjoint(other: Expr): Expr =
    (this intersect other).card() eq 0L

// ============ LIST OPERATIONS ============

/** Index of element in list (-1 if not found) */
fun Expr.indexOf(element: Expr): Expr =
    Expr(ExprNode.IndexOf(node, element.node))

/** Index of element in list (-1 if not found) */
fun Expr.indexOf(element: Long): Expr =
    Expr(ExprNode.IndexOf(node, ExprNode.Const(element)))

/** Sort list, optionally by key function */
fun sort(list: Expr, key: ((Expr) -> Expr)? = null): Expr =
    Expr(ExprNode.Sort(list.node, key?.let { k -> { n: ExprNode -> k(Expr(n)).node } }))

/** Return sorted copy of list */
fun Expr.sorted(): Expr = sort(this)

/** First element of list */
fun Expr.first(): Expr = this[0]

/** Last element of list */
fun Expr.last(): Expr = this[this.count() - Expr(ExprNode.Const(1))]

/** Number of elements in list */
fun Expr.count(): Expr = Expr(ExprNode.Count(node))

/** Find index of value in array */
fun Expr.find(value: Expr): Expr = Expr(ExprNode.Find(node, value.node))

/** Find index of value in array */
fun Expr.find(value: Long): Expr = Expr(ExprNode.Find(node, ExprNode.Const(value)))

// ============ DOMAIN CONSTRAINT ============

/** Constrain variable to be within a domain (set of allowed values) */
infix fun Expr.inDomain(domain: LongArray): Expr =
    Expr(ExprNode.InDomain(node, domain))

// ============ ARRAY CREATION ============

/** Create array from list of expressions */
fun arrayOf(exprs: List<Expr>): Expr =
    Expr(ExprNode.ArrayOf(exprs.map { it.node }))

// ============ RANGE HELPERS ============

/** Range from 0 until n */
fun range(n: Int): IntRange = 0 until n

/** Range from 0 until n */
fun range(n: Long): LongRange = 0L until n

/** Range from start until end */
fun range(start: Int, end: Int): IntRange = start until end

/** Range from start until end */
fun range(start: Long, end: Long): LongRange = start until end

// ============ FACTORY FUNCTIONS ============

/** Create an Expr from a Long constant */
fun expr(value: Long): Expr = Expr(ExprNode.Const(value))

/** Create an Expr from a Double constant */
fun expr(value: Double): Expr = Expr(ExprNode.ConstDouble(value))



