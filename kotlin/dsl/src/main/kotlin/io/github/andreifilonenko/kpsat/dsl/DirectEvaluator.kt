@file:Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod")

package io.github.andreifilonenko.kpsat.dsl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Direct evaluator for expressions without a solver.
 * Useful for unit testing constraint logic.
 *
 * Usage:
 * ```kotlin
 * val evaluator = DirectEvaluator()
 *     .withVar(varId, 5L)
 *     .withVar(varId2, 10L)
 * val result = evaluator.evaluate(expr)
 * ```
 */
class DirectEvaluator private constructor(
    private val variables: PersistentMap<Int, Any>,
) {

    constructor() : this(persistentMapOf())

    /**
     * Create a new evaluator with an additional variable binding.
     */
    fun withVar(id: Int, value: Long): DirectEvaluator =
        DirectEvaluator(variables.put(id, value))

    /**
     * Create a new evaluator with an additional variable binding (boolean).
     */
    fun withVar(id: Int, value: Boolean): DirectEvaluator =
        DirectEvaluator(variables.put(id, if (value) 1L else 0L))

    /**
     * Create a new evaluator with an additional variable binding (double).
     */
    fun withVar(id: Int, value: Double): DirectEvaluator =
        DirectEvaluator(variables.put(id, value))

    /**
     * Create a new evaluator with an additional list binding.
     */
    fun withList(id: Int, values: List<Long>): DirectEvaluator =
        DirectEvaluator(variables.put(id, values))

    /**
     * Create a new evaluator with an additional set binding.
     */
    fun withSet(id: Int, values: Set<Long>): DirectEvaluator =
        DirectEvaluator(variables.put(id, values))

    // ============ LEGACY MUTABLE API (for backward compatibility) ============

    private val mutableVariables = mutableMapOf<Int, Any>()

    /**
     * Set the value of a variable by ID.
     * @deprecated Use withVar for immutable API
     */
    fun setVar(id: Int, value: Long) {
        mutableVariables[id] = value
    }

    /**
     * Set the value of a variable by ID (boolean).
     * @deprecated Use withVar for immutable API
     */
    fun setVar(id: Int, value: Boolean) {
        mutableVariables[id] = if (value) 1L else 0L
    }

    /**
     * Set the value of a variable by ID (double).
     * @deprecated Use withVar for immutable API
     */
    fun setVar(id: Int, value: Double) {
        mutableVariables[id] = value
    }

    /**
     * Set a list value for a variable.
     * @deprecated Use withList for immutable API
     */
    fun setList(id: Int, values: List<Long>) {
        mutableVariables[id] = values
    }

    /**
     * Set a set value for a variable.
     * @deprecated Use withSet for immutable API
     */
    fun setSet(id: Int, values: Set<Long>) {
        mutableVariables[id] = values
    }

    /**
     * Clear all variable values.
     * @deprecated Use immutable API instead
     */
    fun clear() {
        mutableVariables.clear()
    }

    private fun getVariable(id: Int): Any? =
        variables[id] ?: mutableVariables[id]

    // ============ EVALUATION API ============

    /**
     * Evaluate an expression, returning Either<EvalError, Any>.
     */
    fun evaluate(expr: Expr): Either<EvalError, Any> =
        evaluate(expr.node)

    /**
     * Evaluate an expression as Long.
     */
    fun evaluateLong(expr: Expr): Either<EvalError, Long> =
        evaluate(expr.node).flatMap { toLong(it, "evaluateLong") }

    /**
     * Evaluate an expression as Double.
     */
    fun evaluateDouble(expr: Expr): Either<EvalError, Double> =
        evaluate(expr.node).flatMap { toDouble(it, "evaluateDouble") }

    /**
     * Evaluate an expression as Boolean.
     */
    fun evaluateBool(expr: Expr): Either<EvalError, Boolean> =
        evaluateLong(expr).map { it != 0L }

    // ============ INTERNAL EVALUATION ============

    @Suppress("LargeClass")
    private fun evaluate(node: ExprNode): Either<EvalError, Any> = when (node) {
        is ExprNode.Const -> node.value.right()
        is ExprNode.ConstDouble -> node.value.right()
        is ExprNode.Var -> getVariable(node.id)?.right()
            ?: EvalError.VariableNotSet(node.id, node.type, errorContext("evaluate", "Var(${node.id})")).left()
        is ExprNode.ArrayLiteral -> node.values.toList().right()
        is ExprNode.ArrayLiteralDouble -> node.values.toList().right()
        is ExprNode.ArrayOf -> node.elements.map { evaluate(it) }.sequence().map { it }
        is ExprNode.Array2D -> node.rows.map { evaluate(it) }.sequence()
        is ExprNode.Sum -> evaluateSum(node.operands)
        is ExprNode.Sub -> evaluateSub(node.left, node.right)
        is ExprNode.Prod -> evaluateProd(node.operands)
        is ExprNode.Div -> evaluateDiv(node.left, node.right)
        is ExprNode.Mod -> evaluateMod(node.left, node.right)
        is ExprNode.Neg -> evaluateNeg(node.operand)
        is ExprNode.Eq -> evaluate(node.left).flatMap { l ->
            evaluate(node.right).map { r -> if (l == r) 1L else 0L }
        }
        is ExprNode.Neq -> evaluate(node.left).flatMap { l ->
            evaluate(node.right).map { r -> if (l != r) 1L else 0L }
        }
        is ExprNode.Lt -> compareValues(node.left, node.right).map { if (it < 0) 1L else 0L }
        is ExprNode.Leq -> compareValues(node.left, node.right).map { if (it <= 0) 1L else 0L }
        is ExprNode.Gt -> compareValues(node.left, node.right).map { if (it > 0) 1L else 0L }
        is ExprNode.Geq -> compareValues(node.left, node.right).map { if (it >= 0) 1L else 0L }
        is ExprNode.And -> evaluateAnd(node.operands)
        is ExprNode.Or -> evaluateOr(node.operands)
        is ExprNode.Not -> evaluateLong(node.operand).map { if (it == 0L) 1L else 0L }
        is ExprNode.Xor -> evaluateXor(node.operands)
        is ExprNode.If -> evaluateLong(node.condition).flatMap { cond ->
            if (cond != 0L) evaluate(node.ifTrue) else evaluate(node.ifFalse)
        }
        is ExprNode.At -> evaluateAt(node)
        is ExprNode.At2D -> evaluateAt2D(node)
        is ExprNode.Count -> evaluateCount(node.collection)
        is ExprNode.Contains -> evaluateContains(node)
        is ExprNode.Find -> evaluateFind(node)
        is ExprNode.IndexOf -> evaluateIndexOf(node)
        is ExprNode.Intersection -> evaluateIntersection(node.sets)
        is ExprNode.Union -> evaluateUnion(node.sets)
        is ExprNode.Sort -> evaluateSort(node)
        is ExprNode.Min -> evaluateMin(node.operands)
        is ExprNode.Max -> evaluateMax(node.operands)
        is ExprNode.SumOver -> evaluateSumOver(node)
        is ExprNode.ProdOver -> evaluateProdOver(node)
        is ExprNode.MinOver -> evaluateMinOver(node)
        is ExprNode.MaxOver -> evaluateMaxOver(node)
        is ExprNode.AndOver -> evaluateAndOver(node)
        is ExprNode.OrOver -> evaluateOrOver(node)
        is ExprNode.Abs -> evaluateAbs(node.operand)
        is ExprNode.Sqrt -> evaluateDouble(node.operand).map { sqrt(it) }
        is ExprNode.Pow -> evaluatePow(node.base, node.exponent)
        is ExprNode.Exp -> evaluateDouble(node.operand).map { exp(it) }
        is ExprNode.Log -> evaluateDouble(node.operand).map { ln(it) }
        is ExprNode.Ceil -> evaluateDouble(node.operand).map { ceil(it).toLong() }
        is ExprNode.Floor -> evaluateDouble(node.operand).map { floor(it).toLong() }
        is ExprNode.Round -> evaluateDouble(node.operand).map { round(it).toLong() }
        is ExprNode.InDomain -> evaluateInDomain(node)

        // Optimized aggregation nodes (created by ExprOptimizer)
        is ExprNode.CountTrue -> evaluateCountTrue(node.conditions)
        is ExprNode.IndicatorSum -> evaluateIndicatorSum(node.terms)
    }

    private fun evaluateLong(node: ExprNode): Either<EvalError, Long> =
        evaluate(node).flatMap { toLong(it, "evaluateLong") }

    private fun evaluateDouble(node: ExprNode): Either<EvalError, Double> =
        evaluate(node).flatMap { toDouble(it, "evaluateDouble") }

    private fun toLong(result: Any, operation: String): Either<EvalError, Long> = when (result) {
        is Long -> result.right()
        is Double -> result.toLong().right()
        is Boolean -> (if (result) 1L else 0L).right()
        else -> EvalError.TypeMismatch(
            expected = "Long",
            actual = result::class.simpleName ?: "unknown",
            value = result,
            context = errorContext(operation)
        ).left()
    }

    private fun toDouble(result: Any, operation: String): Either<EvalError, Double> = when (result) {
        is Long -> result.toDouble().right()
        is Double -> result.right()
        else -> EvalError.TypeMismatch(
            expected = "Double",
            actual = result::class.simpleName ?: "unknown",
            value = result,
            context = errorContext(operation)
        ).left()
    }

    private fun toDoubleValue(value: Any): Double = when (value) {
        is Double -> value
        is Long -> value.toDouble()
        else -> 0.0
    }

    private fun evaluateSum(operands: List<ExprNode>): Either<EvalError, Any> =
        operands.map { evaluate(it) }.sequence().map { values ->
            when {
                values.any { it is Double } -> values.sumOf { toDoubleValue(it) }
                else -> values.sumOf { it as Long }
            }
        }

    private fun evaluateSub(left: ExprNode, right: ExprNode): Either<EvalError, Any> =
        evaluate(left).flatMap { l ->
            evaluate(right).map { r ->
                when {
                    l is Double || r is Double -> toDoubleValue(l) - toDoubleValue(r)
                    else -> (l as Long) - (r as Long)
                }
            }
        }

    private fun evaluateProd(operands: List<ExprNode>): Either<EvalError, Any> =
        operands.map { evaluate(it) }.sequence().map { values ->
            when {
                values.any { it is Double } -> values.fold(1.0) { acc, v -> acc * toDoubleValue(v) }
                else -> values.fold(1L) { acc, v -> acc * (v as Long) }
            }
        }

    private fun evaluateDiv(left: ExprNode, right: ExprNode): Either<EvalError, Any> =
        evaluate(left).flatMap { l ->
            evaluate(right).flatMap { r ->
                val rValue = toDoubleValue(r)
                either {
                    ensure(rValue != 0.0) {
                        EvalError.DivisionByZero(errorContext("division"))
                    }
                    when {
                        l is Double || r is Double -> toDoubleValue(l) / rValue
                        else -> (l as Long) / (r as Long)
                    }
                }
            }
        }

    private fun evaluateMod(left: ExprNode, right: ExprNode): Either<EvalError, Any> =
        evaluateLong(left).flatMap { l ->
            evaluateLong(right).flatMap { r ->
                either {
                    ensure(r != 0L) {
                        EvalError.DivisionByZero(errorContext("modulo"))
                    }
                    l % r
                }
            }
        }

    private fun evaluateNeg(operand: ExprNode): Either<EvalError, Any> =
        evaluate(operand).flatMap { v ->
            when (v) {
                is Long -> (-v).right()
                is Double -> (-v).right()
                else -> EvalError.InvalidOperand(
                    operation = "negate",
                    operandType = v::class.simpleName ?: "unknown",
                    context = errorContext("negate")
                ).left()
            }
        }

    private fun compareValues(left: ExprNode, right: ExprNode): Either<EvalError, Int> =
        evaluate(left).flatMap { l ->
            evaluate(right).map { r ->
                toDoubleValue(l).compareTo(toDoubleValue(r))
            }
        }

    private fun evaluateAnd(operands: List<ExprNode>): Either<EvalError, Long> =
        operands.map { evaluateLong(it) }.sequence().map { values ->
            if (values.all { it != 0L }) 1L else 0L
        }

    private fun evaluateOr(operands: List<ExprNode>): Either<EvalError, Long> =
        operands.map { evaluateLong(it) }.sequence().map { values ->
            if (values.any { it != 0L }) 1L else 0L
        }

    private fun evaluateXor(operands: List<ExprNode>): Either<EvalError, Long> =
        operands.map { evaluateLong(it) }.sequence().map { values ->
            val trueCount = values.count { it != 0L }
            if (trueCount % 2 == 1) 1L else 0L
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateAt(node: ExprNode.At): Either<EvalError, Any> =
        evaluate(node.array).flatMap { arr ->
            evaluateLong(node.index).flatMap { idx ->
                val index = idx.toInt()
                when (arr) {
                    is List<*> -> {
                        val size = arr.size
                        either {
                            ensure(index in 0 until size) {
                                EvalError.IndexOutOfBounds(idx, size, errorContext("At", "index=$index"))
                            }
                            arr[index] ?: raise(
                                EvalError.NullValue("[$index]", errorContext("At"))
                            )
                        }
                    }
                    is LongArray -> {
                        either {
                            ensure(index in arr.indices) {
                                EvalError.IndexOutOfBounds(idx, arr.size, errorContext("At"))
                            }
                            arr[index]
                        }
                    }
                    is DoubleArray -> {
                        either {
                            ensure(index in arr.indices) {
                                EvalError.IndexOutOfBounds(idx, arr.size, errorContext("At"))
                            }
                            arr[index]
                        }
                    }
                    else -> EvalError.InvalidOperand(
                        operation = "index",
                        operandType = arr::class.simpleName ?: "unknown",
                        context = errorContext("At")
                    ).left()
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateAt2D(node: ExprNode.At2D): Either<EvalError, Any> =
        evaluate(node.array).flatMap { arr ->
            evaluateLong(node.i).flatMap { iLong ->
                evaluateLong(node.j).flatMap { jLong ->
                    val i = iLong.toInt()
                    val j = jLong.toInt()
                    when (arr) {
                        is List<*> -> either {
                            ensure(i in 0 until arr.size) {
                                EvalError.IndexOutOfBounds(iLong, arr.size, errorContext("At2D", "row"))
                            }
                            val row = arr[i] as? List<*> ?: raise(
                                EvalError.TypeMismatch("List", arr[i]?.let { it::class.simpleName } ?: "null", arr[i] ?: "null", errorContext("At2D"))
                            )
                            ensure(j in 0 until row.size) {
                                EvalError.IndexOutOfBounds(jLong, row.size, errorContext("At2D", "col"))
                            }
                            row[j] ?: raise(
                                EvalError.NullValue("[$i][$j]", errorContext("At2D"))
                            )
                        }
                        else -> EvalError.InvalidOperand(
                            operation = "2D index",
                            operandType = arr::class.simpleName ?: "unknown",
                            context = errorContext("At2D")
                        ).left()
                    }
                }
            }
        }

    private fun evaluateCount(collection: ExprNode): Either<EvalError, Long> =
        evaluate(collection).flatMap { coll ->
            when (coll) {
                is List<*> -> coll.size.toLong().right()
                is Set<*> -> coll.size.toLong().right()
                is LongArray -> coll.size.toLong().right()
                is DoubleArray -> coll.size.toLong().right()
                else -> EvalError.InvalidOperand(
                    operation = "count",
                    operandType = coll::class.simpleName ?: "unknown",
                    context = errorContext("Count")
                ).left()
            }
        }

    private fun evaluateContains(node: ExprNode.Contains): Either<EvalError, Long> =
        evaluate(node.collection).flatMap { coll ->
            evaluate(node.element).flatMap { elem ->
                when (coll) {
                    is List<*> -> (if (elem in coll) 1L else 0L).right()
                    is Set<*> -> (if (elem in coll) 1L else 0L).right()
                    is LongArray -> (if (elem in coll.toList()) 1L else 0L).right()
                    else -> EvalError.InvalidOperand(
                        operation = "contains",
                        operandType = coll::class.simpleName ?: "unknown",
                        context = errorContext("Contains")
                    ).left()
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateFind(node: ExprNode.Find): Either<EvalError, Long> =
        evaluate(node.array).flatMap { arr ->
            evaluate(node.value).flatMap { value ->
                when (arr) {
                    is List<*> -> arr.indexOf(value).toLong().right()
                    is LongArray -> arr.indexOf(value as Long).toLong().right()
                    else -> EvalError.InvalidOperand(
                        operation = "find",
                        operandType = arr::class.simpleName ?: "unknown",
                        context = errorContext("Find")
                    ).left()
                }
            }
        }

    private fun evaluateIndexOf(node: ExprNode.IndexOf): Either<EvalError, Long> =
        evaluate(node.list).flatMap { list ->
            evaluate(node.element).flatMap { elem ->
                when (list) {
                    is List<*> -> list.indexOf(elem).toLong().right()
                    else -> EvalError.InvalidOperand(
                        operation = "indexOf",
                        operandType = list::class.simpleName ?: "unknown",
                        context = errorContext("IndexOf")
                    ).left()
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateIntersection(sets: List<ExprNode>): Either<EvalError, Set<Long>> =
        when {
            sets.isEmpty() -> emptySet<Long>().right()
            else -> sets.map { evaluate(it) }.sequence().map { evaluatedSets ->
                evaluatedSets.map { it as Set<Long> }.reduce { acc, s -> acc.intersect(s) }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateUnion(sets: List<ExprNode>): Either<EvalError, Set<Long>> =
        when {
            sets.isEmpty() -> emptySet<Long>().right()
            else -> sets.map { evaluate(it) }.sequence().map { evaluatedSets ->
                evaluatedSets.map { it as Set<Long> }.reduce { acc, s -> acc.union(s) }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateSort(node: ExprNode.Sort): Either<EvalError, List<Any>> =
        evaluate(node.list).flatMap { list ->
            when (list) {
                is List<*> -> {
                    val typedList = list as List<Any>
                    when (node.key) {
                        null -> typedList.sortedWith { a, b -> toDoubleValue(a).compareTo(toDoubleValue(b)) }.right()
                        else -> either {
                            typedList.sortedBy { elem ->
                                val elemNode = toNode(elem).bind()
                                evaluateDouble(node.key.invoke(elemNode)).bind()
                            }
                        }
                    }
                }
                else -> EvalError.InvalidOperand(
                    operation = "sort",
                    operandType = list::class.simpleName ?: "unknown",
                    context = errorContext("Sort")
                ).left()
            }
        }

    private fun evaluateMin(operands: List<ExprNode>): Either<EvalError, Any> =
        operands.map { evaluate(it) }.sequence().map { values ->
            when {
                values.any { it is Double } -> values.minOf { toDoubleValue(it) }
                else -> values.minOf { it as Long }
            }
        }

    private fun evaluateMax(operands: List<ExprNode>): Either<EvalError, Any> =
        operands.map { evaluate(it) }.sequence().map { values ->
            when {
                values.any { it is Double } -> values.maxOf { toDoubleValue(it) }
                else -> values.maxOf { it as Long }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateSumOver(node: ExprNode.SumOver): Either<EvalError, Any> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluate(node.lambda(elemNode)) }
            }.sequence().map { values ->
                when {
                    values.any { it is Double } -> values.sumOf { toDoubleValue(it) }
                    else -> values.sumOf { it as Long }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateProdOver(node: ExprNode.ProdOver): Either<EvalError, Any> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluate(node.lambda(elemNode)) }
            }.sequence().map { values ->
                when {
                    values.any { it is Double } -> values.fold(1.0) { acc, v -> acc * toDoubleValue(v) }
                    else -> values.fold(1L) { acc, v -> acc * (v as Long) }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateMinOver(node: ExprNode.MinOver): Either<EvalError, Any> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluate(node.lambda(elemNode)) }
            }.sequence().map { values ->
                when {
                    values.any { it is Double } -> values.minOf { toDoubleValue(it) }
                    else -> values.minOf { it as Long }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateMaxOver(node: ExprNode.MaxOver): Either<EvalError, Any> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluate(node.lambda(elemNode)) }
            }.sequence().map { values ->
                when {
                    values.any { it is Double } -> values.maxOf { toDoubleValue(it) }
                    else -> values.maxOf { it as Long }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateAndOver(node: ExprNode.AndOver): Either<EvalError, Long> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluateLong(node.lambda(elemNode)) }
            }.sequence().map { values ->
                if (values.all { it != 0L }) 1L else 0L
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateOrOver(node: ExprNode.OrOver): Either<EvalError, Long> =
        evaluate(node.collection).flatMap { collection ->
            val list = collection as List<Any>
            list.map { elem ->
                toNode(elem).flatMap { elemNode -> evaluateLong(node.lambda(elemNode)) }
            }.sequence().map { values ->
                if (values.any { it != 0L }) 1L else 0L
            }
        }

    private fun toNode(elem: Any): Either<EvalError, ExprNode> = when (elem) {
        is Long -> ExprNode.Const(elem).right()
        is Double -> ExprNode.ConstDouble(elem).right()
        else -> EvalError.InvalidOperand(
            operation = "toNode",
            operandType = elem::class.simpleName ?: "unknown",
            context = errorContext("toNode")
        ).left()
    }

    private fun evaluateAbs(operand: ExprNode): Either<EvalError, Any> =
        evaluate(operand).flatMap { v ->
            when (v) {
                is Long -> abs(v).right()
                is Double -> abs(v).right()
                else -> EvalError.InvalidOperand(
                    operation = "abs",
                    operandType = v::class.simpleName ?: "unknown",
                    context = errorContext("Abs")
                ).left()
            }
        }

    private fun evaluatePow(base: ExprNode, exponent: ExprNode): Either<EvalError, Double> =
        evaluateDouble(base).flatMap { b ->
            evaluateDouble(exponent).map { e ->
                b.pow(e)
            }
        }

    private fun evaluateInDomain(node: ExprNode.InDomain): Either<EvalError, Long> =
        evaluateLong(node.variable).map { value ->
            if (value in node.domain.toList()) 1L else 0L
        }

    /**
     * Evaluate CountTrue: count how many conditions are true (non-zero).
     * This is semantically equivalent to: sum(iif(cond1, 1, 0), iif(cond2, 1, 0), ...)
     */
    private fun evaluateCountTrue(conditions: List<ExprNode>): Either<EvalError, Long> =
        conditions.map { evaluateLong(it) }.sequence().map { values ->
            values.count { it != 0L }.toLong()
        }

    /**
     * Evaluate IndicatorSum: sum of values where corresponding conditions are true.
     * This is semantically equivalent to: sum(iif(cond1, val1, 0), iif(cond2, val2, 0), ...)
     */
    private fun evaluateIndicatorSum(terms: List<Pair<ExprNode, ExprNode>>): Either<EvalError, Any> =
        terms.map { (cond, value) ->
            evaluateLong(cond).flatMap { condVal ->
                evaluate(value).map { valResult ->
                    if (condVal != 0L) valResult else 0L
                }
            }
        }.sequence().map { values ->
            when {
                values.any { it is Double } -> values.sumOf { toDoubleValue(it) }
                else -> values.sumOf { it as Long }
            }
        }
}

// ============ EXTENSION FUNCTIONS ============

/**
 * Sequence a list of Either into Either of list.
 */
private fun <E, A> List<Either<E, A>>.sequence(): Either<E, List<A>> =
    fold<Either<E, A>, Either<E, List<A>>>(emptyList<A>().right()) { acc, either ->
        acc.flatMap { list -> either.map { list + it } }
    }

/**
 * Convenience function to evaluate an expression with given variable bindings.
 */
fun Expr.evaluate(varBindings: Map<Int, Long>): Either<EvalError, Long> {
    val evaluator = varBindings.entries.fold(DirectEvaluator()) { eval, (id, value) ->
        eval.withVar(id, value)
    }
    return evaluator.evaluateLong(this)
}

/**
 * Convenience function to evaluate a boolean expression.
 */
fun Expr.evaluateBool(varBindings: Map<Int, Long>): Either<EvalError, Boolean> =
    evaluate(varBindings).map { it != 0L }
