package io.github.andreifilonenko.kpsat.solver

import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Expression optimizer that transforms DSL expression trees into more efficient forms
 * before compilation to CP-SAT.
 *
 * Key optimizations:
 * 1. Counting pattern: sum(iif(cond, 1, 0), ...) → CountTrue(cond, ...)
 * 2. Indicator sum: sum(iif(cond, value, 0), ...) → IndicatorSum([(cond, value), ...])
 * 3. And/Or flattening: And(And(a, b), c) → And(a, b, c)
 * 4. Constant folding for nested operations
 */
class ExprOptimizer {

    /**
     * Optimize an expression tree.
     */
    fun optimize(expr: Expr): Expr = Expr(optimize(expr.node))

    /**
     * Optimize an expression node recursively.
     */
    fun optimize(node: ExprNode): ExprNode = when (node) {
        // First optimize children, then apply patterns
        is ExprNode.Sum -> optimizeSum(node.operands.map { optimize(it) })
        is ExprNode.And -> optimizeAnd(node.operands.map { optimize(it) })
        is ExprNode.Or -> optimizeOr(node.operands.map { optimize(it) })
        is ExprNode.If -> optimizeIf(optimize(node.condition), optimize(node.ifTrue), optimize(node.ifFalse))
        
        // Recursively optimize compound nodes
        is ExprNode.Sub -> ExprNode.Sub(optimize(node.left), optimize(node.right))
        is ExprNode.Prod -> ExprNode.Prod(node.operands.map { optimize(it) })
        is ExprNode.Div -> ExprNode.Div(optimize(node.left), optimize(node.right))
        is ExprNode.Mod -> ExprNode.Mod(optimize(node.left), optimize(node.right))
        is ExprNode.Neg -> ExprNode.Neg(optimize(node.operand))
        
        is ExprNode.Eq -> ExprNode.Eq(optimize(node.left), optimize(node.right))
        is ExprNode.Neq -> ExprNode.Neq(optimize(node.left), optimize(node.right))
        is ExprNode.Lt -> ExprNode.Lt(optimize(node.left), optimize(node.right))
        is ExprNode.Leq -> ExprNode.Leq(optimize(node.left), optimize(node.right))
        is ExprNode.Gt -> ExprNode.Gt(optimize(node.left), optimize(node.right))
        is ExprNode.Geq -> ExprNode.Geq(optimize(node.left), optimize(node.right))
        
        is ExprNode.Not -> ExprNode.Not(optimize(node.operand))
        is ExprNode.Xor -> ExprNode.Xor(node.operands.map { optimize(it) })
        
        is ExprNode.Min -> ExprNode.Min(node.operands.map { optimize(it) })
        is ExprNode.Max -> ExprNode.Max(node.operands.map { optimize(it) })
        
        is ExprNode.Abs -> ExprNode.Abs(optimize(node.operand))
        
        is ExprNode.At -> ExprNode.At(optimize(node.array), optimize(node.index))
        is ExprNode.At2D -> ExprNode.At2D(optimize(node.array), optimize(node.i), optimize(node.j))
        
        is ExprNode.ArrayOf -> ExprNode.ArrayOf(node.elements.map { optimize(it) })
        is ExprNode.Array2D -> ExprNode.Array2D(node.rows.map { optimize(it) })
        
        is ExprNode.InDomain -> ExprNode.InDomain(optimize(node.variable), node.domain)
        
        // Already optimized node types pass through
        is ExprNode.CountTrue -> ExprNode.CountTrue(node.conditions.map { optimize(it) })
        is ExprNode.IndicatorSum -> ExprNode.IndicatorSum(
            node.terms.map { (cond, value) -> optimize(cond) to optimize(value) }
        )
        
        // Leaf nodes and unsupported nodes pass through unchanged
        is ExprNode.Const,
        is ExprNode.ConstDouble,
        is ExprNode.Var,
        is ExprNode.ArrayLiteral,
        is ExprNode.ArrayLiteralDouble,
        is ExprNode.Count,
        is ExprNode.Contains,
        is ExprNode.Find,
        is ExprNode.IndexOf,
        is ExprNode.Intersection,
        is ExprNode.Union,
        is ExprNode.Sort,
        is ExprNode.SumOver,
        is ExprNode.ProdOver,
        is ExprNode.MinOver,
        is ExprNode.MaxOver,
        is ExprNode.AndOver,
        is ExprNode.OrOver,
        is ExprNode.Sqrt,
        is ExprNode.Pow,
        is ExprNode.Exp,
        is ExprNode.Log,
        is ExprNode.Ceil,
        is ExprNode.Floor,
        is ExprNode.Round -> node
    }

    /**
     * Optimize a Sum expression.
     * Detects:
     * - Counting pattern: sum of iif(cond, 1, 0) → CountTrue
     * - Indicator sum pattern: sum of iif(cond, value, 0) → IndicatorSum
     * - Constant folding
     * - Flattening nested sums
     */
    private fun optimizeSum(operands: List<ExprNode>): ExprNode {
        // Flatten nested sums first
        val flattened = operands.flatMap { op ->
            when (op) {
                is ExprNode.Sum -> op.operands
                else -> listOf(op)
            }
        }

        // Separate constants from non-constants
        val (constants, nonConstants) = flattened.partition { it is ExprNode.Const }
        val constantSum = constants.sumOf { (it as ExprNode.Const).value }

        // Try to detect counting or indicator patterns
        val indicatorTerms = mutableListOf<Pair<ExprNode, ExprNode>>()
        val countingConditions = mutableListOf<ExprNode>()
        val regularTerms = mutableListOf<ExprNode>()

        for (term in nonConstants) {
            when {
                // Pattern: iif(cond, 1, 0) → counting
                isCountingPattern(term) -> {
                    countingConditions.add((term as ExprNode.If).condition)
                }
                // Pattern: iif(cond, value, 0) → indicator sum
                isIndicatorPattern(term) -> {
                    val ifNode = term as ExprNode.If
                    indicatorTerms.add(ifNode.condition to ifNode.ifTrue)
                }
                // Already a CountTrue - merge its conditions
                term is ExprNode.CountTrue -> {
                    countingConditions.addAll(term.conditions)
                }
                // Already an IndicatorSum - merge its terms
                term is ExprNode.IndicatorSum -> {
                    indicatorTerms.addAll(term.terms)
                }
                else -> regularTerms.add(term)
            }
        }

        // Build optimized result
        val resultTerms = mutableListOf<ExprNode>()

        // Add constant if non-zero
        if (constantSum != 0L) {
            resultTerms.add(ExprNode.Const(constantSum))
        }

        // Add CountTrue if we have counting conditions
        if (countingConditions.isNotEmpty()) {
            resultTerms.add(ExprNode.CountTrue(countingConditions))
        }

        // Add IndicatorSum if we have indicator terms
        if (indicatorTerms.isNotEmpty()) {
            resultTerms.add(ExprNode.IndicatorSum(indicatorTerms))
        }

        // Add remaining regular terms
        resultTerms.addAll(regularTerms)

        return when {
            resultTerms.isEmpty() -> ExprNode.Const(0)
            resultTerms.size == 1 -> resultTerms[0]
            else -> ExprNode.Sum(resultTerms)
        }
    }

    /**
     * Checks if node is iif(cond, 1, 0) pattern.
     */
    private fun isCountingPattern(node: ExprNode): Boolean =
        node is ExprNode.If &&
            node.ifTrue is ExprNode.Const && (node.ifTrue as ExprNode.Const).value == 1L &&
            node.ifFalse is ExprNode.Const && (node.ifFalse as ExprNode.Const).value == 0L

    /**
     * Checks if node is iif(cond, value, 0) pattern where value is not constant 1.
     */
    private fun isIndicatorPattern(node: ExprNode): Boolean =
        node is ExprNode.If &&
            node.ifFalse is ExprNode.Const && (node.ifFalse as ExprNode.Const).value == 0L &&
            !(node.ifTrue is ExprNode.Const && (node.ifTrue as ExprNode.Const).value == 1L)

    /**
     * Optimize an And expression.
     * - Flattens nested And nodes
     * - Removes constant true values
     * - Short-circuits on constant false
     */
    private fun optimizeAnd(operands: List<ExprNode>): ExprNode {
        // Flatten nested And
        val flattened = operands.flatMap { op ->
            when (op) {
                is ExprNode.And -> op.operands
                else -> listOf(op)
            }
        }

        // Filter constants and check for false
        val nonConstants = mutableListOf<ExprNode>()
        for (op in flattened) {
            when {
                op is ExprNode.Const && op.value == 0L -> {
                    // Short-circuit: false AND anything = false
                    return ExprNode.Const(0)
                }
                op is ExprNode.Const && op.value != 0L -> {
                    // Skip true constants
                }
                else -> nonConstants.add(op)
            }
        }

        return when {
            nonConstants.isEmpty() -> ExprNode.Const(1) // All were true
            nonConstants.size == 1 -> nonConstants[0]
            else -> ExprNode.And(nonConstants)
        }
    }

    /**
     * Optimize an Or expression.
     * - Flattens nested Or nodes
     * - Removes constant false values
     * - Short-circuits on constant true
     */
    private fun optimizeOr(operands: List<ExprNode>): ExprNode {
        // Flatten nested Or
        val flattened = operands.flatMap { op ->
            when (op) {
                is ExprNode.Or -> op.operands
                else -> listOf(op)
            }
        }

        // Filter constants and check for true
        val nonConstants = mutableListOf<ExprNode>()
        for (op in flattened) {
            when {
                op is ExprNode.Const && op.value != 0L -> {
                    // Short-circuit: true OR anything = true
                    return ExprNode.Const(1)
                }
                op is ExprNode.Const && op.value == 0L -> {
                    // Skip false constants
                }
                else -> nonConstants.add(op)
            }
        }

        return when {
            nonConstants.isEmpty() -> ExprNode.Const(0) // All were false
            nonConstants.size == 1 -> nonConstants[0]
            else -> ExprNode.Or(nonConstants)
        }
    }

    /**
     * Optimize an If expression.
     * - Constant condition folding
     * - Simplify when ifTrue and ifFalse are equal
     */
    private fun optimizeIf(condition: ExprNode, ifTrue: ExprNode, ifFalse: ExprNode): ExprNode = when {
        // Constant condition
        condition is ExprNode.Const -> if (condition.value != 0L) ifTrue else ifFalse
        // Same branches
        ifTrue == ifFalse -> ifTrue
        // Keep as-is
        else -> ExprNode.If(condition, ifTrue, ifFalse)
    }
}




