@file:Suppress("TooManyFunctions")

package io.github.andreifilonenko.kpsat.dsl

/**
 * Variable type for decision variables in the model.
 */
enum class VarType {
    BOOL,
    INT,
    FLOAT,
    SET,
    LIST,
}

/**
 * AST node representing an expression in the constraint DSL.
 * This is a symbolic representation that can be:
 * 1. Compiled to solver-specific expressions (CP-SAT, etc.)
 * 2. Evaluated directly for unit testing
 */
sealed interface ExprNode {

    // ============ LITERALS ============

    /** Constant integer value */
    data class Const(val value: Long) : ExprNode

    /** Constant double value */
    data class ConstDouble(val value: Double) : ExprNode

    /** Reference to a decision variable by ID */
    data class Var(val id: Int, val type: VarType) : ExprNode

    /** Integer array literal */
    data class ArrayLiteral(val values: LongArray) : ExprNode {
        override fun equals(other: Any?) = other is ArrayLiteral && values.contentEquals(other.values)
        override fun hashCode() = values.contentHashCode()
    }

    /** Double array literal */
    data class ArrayLiteralDouble(val values: DoubleArray) : ExprNode {
        override fun equals(other: Any?) = other is ArrayLiteralDouble && values.contentEquals(other.values)
        override fun hashCode() = values.contentHashCode()
    }

    /** Array of expressions */
    data class ArrayOf(val elements: List<ExprNode>) : ExprNode

    /** 2D array (array of arrays) */
    data class Array2D(val rows: List<ExprNode>) : ExprNode

    // ============ ARITHMETIC ============

    /** Sum of expressions: a + b + c + ... */
    data class Sum(val operands: List<ExprNode>) : ExprNode

    /** Subtraction: a - b */
    data class Sub(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Product of expressions: a * b * c * ... */
    data class Prod(val operands: List<ExprNode>) : ExprNode

    /** Division: a / b */
    data class Div(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Modulo: a % b */
    data class Mod(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Negation: -a */
    data class Neg(val operand: ExprNode) : ExprNode

    // ============ COMPARISON ============

    /** Equality: a == b */
    data class Eq(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Not equal: a != b */
    data class Neq(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Less than: a < b */
    data class Lt(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Less than or equal: a <= b */
    data class Leq(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Greater than: a > b */
    data class Gt(val left: ExprNode, val right: ExprNode) : ExprNode

    /** Greater than or equal: a >= b */
    data class Geq(val left: ExprNode, val right: ExprNode) : ExprNode

    // ============ LOGICAL ============

    /** Logical AND: a && b && c && ... */
    data class And(val operands: List<ExprNode>) : ExprNode

    /** Logical OR: a || b || c || ... */
    data class Or(val operands: List<ExprNode>) : ExprNode

    /** Logical NOT: !a */
    data class Not(val operand: ExprNode) : ExprNode

    /** Logical XOR: a ^ b ^ c ^ ... */
    data class Xor(val operands: List<ExprNode>) : ExprNode

    // ============ CONDITIONAL ============

    /** If-then-else: condition ? ifTrue : ifFalse */
    data class If(val condition: ExprNode, val ifTrue: ExprNode, val ifFalse: ExprNode) : ExprNode

    // ============ COLLECTION OPERATIONS ============

    /** Array/list access: array[index] */
    data class At(val array: ExprNode, val index: ExprNode) : ExprNode

    /** 2D array access: array[i, j] */
    data class At2D(val array: ExprNode, val i: ExprNode, val j: ExprNode) : ExprNode

    /** Count elements in set/list */
    data class Count(val collection: ExprNode) : ExprNode

    /** Check if collection contains element */
    data class Contains(val collection: ExprNode, val element: ExprNode) : ExprNode

    /** Find element index in array */
    data class Find(val array: ExprNode, val value: ExprNode) : ExprNode

    /** Index of element in list */
    data class IndexOf(val list: ExprNode, val element: ExprNode) : ExprNode

    // ============ SET OPERATIONS ============

    /** Set intersection */
    data class Intersection(val sets: List<ExprNode>) : ExprNode

    /** Set union */
    data class Union(val sets: List<ExprNode>) : ExprNode

    // ============ LIST OPERATIONS ============

    /** Sort a list */
    data class Sort(val list: ExprNode, val key: ((ExprNode) -> ExprNode)?) : ExprNode

    // ============ AGGREGATIONS (List of expressions) ============

    /** Minimum of expressions */
    data class Min(val operands: List<ExprNode>) : ExprNode

    /** Maximum of expressions */
    data class Max(val operands: List<ExprNode>) : ExprNode

    // ============ LAMBDA AGGREGATIONS (Over collection with function) ============

    /** Sum over collection: sum(collection, i -> f(i)) */
    data class SumOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    /** Product over collection */
    data class ProdOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    /** Minimum over collection */
    data class MinOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    /** Maximum over collection */
    data class MaxOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    /** ForAll: all elements satisfy predicate */
    data class AndOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    /** Exists: at least one element satisfies predicate */
    data class OrOver(val collection: ExprNode, val lambda: (ExprNode) -> ExprNode) : ExprNode

    // ============ MATH FUNCTIONS ============

    /** Absolute value */
    data class Abs(val operand: ExprNode) : ExprNode

    /** Square root */
    data class Sqrt(val operand: ExprNode) : ExprNode

    /** Power: base^exponent */
    data class Pow(val base: ExprNode, val exponent: ExprNode) : ExprNode

    /** Exponential: e^x */
    data class Exp(val operand: ExprNode) : ExprNode

    /** Natural logarithm */
    data class Log(val operand: ExprNode) : ExprNode

    /** Ceiling */
    data class Ceil(val operand: ExprNode) : ExprNode

    /** Floor */
    data class Floor(val operand: ExprNode) : ExprNode

    /** Round */
    data class Round(val operand: ExprNode) : ExprNode

    // ============ DOMAIN CONSTRAINTS ============

    /** Variable must be within a domain (set of allowed values) */
    data class InDomain(val variable: ExprNode, val domain: LongArray) : ExprNode {
        override fun equals(other: Any?) = other is InDomain && 
            variable == other.variable && domain.contentEquals(other.domain)
        override fun hashCode() = 31 * variable.hashCode() + domain.contentHashCode()
    }

    // ============ OPTIMIZED AGGREGATIONS ============
    // These are created by ExprOptimizer for efficient compilation

    /**
     * Count how many conditions are true.
     * Optimized form of: sum(iif(cond1, 1, 0), iif(cond2, 1, 0), ...)
     * Compiles to a single sum of boolean variables.
     */
    data class CountTrue(val conditions: List<ExprNode>) : ExprNode

    /**
     * Sum of indicator-weighted values.
     * Optimized form of: sum(iif(cond1, val1, 0), iif(cond2, val2, 0), ...)
     * Each term is a (condition, value) pair where value is added when condition is true.
     * Compiles using native CP-SAT indicator constraints.
     */
    data class IndicatorSum(val terms: List<Pair<ExprNode, ExprNode>>) : ExprNode
}

// ============ HELPER CONSTRUCTORS ============

/** Create a constant expression */
fun const(value: Long): ExprNode = ExprNode.Const(value)
fun const(value: Double): ExprNode = ExprNode.ConstDouble(value)

/** Create a sum of two nodes */
fun sumNode(left: ExprNode, right: ExprNode): ExprNode =
    ExprNode.Sum(listOf(left, right))

/** Create a product of two nodes */
fun prodNode(left: ExprNode, right: ExprNode): ExprNode =
    ExprNode.Prod(listOf(left, right))

/** Create an AND of two nodes */
fun andNode(left: ExprNode, right: ExprNode): ExprNode =
    ExprNode.And(listOf(left, right))

/** Create an OR of two nodes */
fun orNode(left: ExprNode, right: ExprNode): ExprNode =
    ExprNode.Or(listOf(left, right))


