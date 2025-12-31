package io.github.andreifilonenko.kpsat.dsl

import arrow.core.Either

/**
 * Base type for model elements.
 * KPSAT has three types: hard constraints (must be satisfied),
 * soft constraints (penalties/rewards), and objectives (minimize/maximize).
 */
sealed interface Constraint {
    val name: String
    val expr: Expr
}

/**
 * A hard constraint that must be satisfied for a solution to be feasible.
 */
data class HardConstraint(
    override val name: String,
    override val expr: Expr,
) : Constraint

/**
 * A soft constraint representing a penalty or reward.
 * Penalties are minimized, rewards are maximized during optimization.
 */
data class SoftConstraint(
    override val name: String,
    override val expr: Expr,
    val weight: Int = 1,
    val priority: Int = 0,
) : Constraint

/**
 * Scope for building constraint programming models.
 * Provides DSL methods for creating variables, arrays, and emitting constraints.
 *
 * Used as a context receiver:
 * ```kotlin
 * context(ConstraintScope)
 * fun buildModel(vars: MutableMap<String, Expr>) {
 *     vars["x"] = int(0, 100)
 *     hard("positive") { vars["x"]!! geq 0L }
 * }
 * ```
 */
interface ConstraintScope {

    // ============ SCALAR VARIABLE CREATION ============

    /** Create a boolean decision variable */
    fun bool(): Expr

    /** Create a boolean decision variable with a name (for debugging) */
    fun bool(name: String): Expr

    /** Create an integer decision variable with bounds [min, max] */
    fun int(min: Long, max: Long): Expr

    /** Create an integer decision variable with bounds and a name */
    fun int(name: String, min: Long, max: Long): Expr

    /** Create an integer decision variable with bounds [min, max] */
    fun int(min: Int, max: Int): Expr = int(min.toLong(), max.toLong())

    /** Create a float decision variable with bounds [min, max] */
    fun float(min: Double, max: Double): Expr

    /** Create a float decision variable with bounds and a name */
    fun float(name: String, min: Double, max: Double): Expr

    // ============ ARRAY/LIST VARIABLE CREATION ============

    /** Create an array of integer decision variables */
    fun intArray(size: Int, min: Long, max: Long): Expr

    /** Create an array of boolean decision variables */
    fun boolArray(size: Int): Expr

    // ============ CONSTANT ARRAYS ============

    /** Create a constant array from Long values */
    fun constArray(values: LongArray): Expr

    /** Create a constant array from Int values */
    fun constArray(values: IntArray): Expr = constArray(values.map { it.toLong() }.toLongArray())

    /** Create a constant array from Double values */
    fun constArray(values: DoubleArray): Expr

    /** Create a constant array from expressions */
    fun constArray(values: List<Expr>): Expr

    /** Create a 2D constant array */
    fun constArray2D(values: Array<LongArray>): Expr

    /** Create a 2D constant array from Ints */
    fun constArray2D(values: Array<IntArray>): Expr =
        constArray2D(values.map { row -> row.map { it.toLong() }.toLongArray() }.toTypedArray())

    // ============ CONSTRAINT EMISSION ============

    /**
     * Emit a hard constraint that must be satisfied.
     * @param name Name for debugging/reporting
     * @param constraint Boolean expression that must be true
     */
    fun hard(name: String, constraint: Expr)

    /**
     * Emit a soft constraint with a penalty to minimize.
     * @param name Name for debugging/reporting
     * @param weight Weight multiplier for the penalty
     * @param priority Priority level (higher = more important, solved first in lexicographic order)
     * @param penalty Expression representing the penalty (typically >= 0)
     */
    fun soft(name: String, weight: Int = 1, priority: Int = 0, penalty: Expr)

    // ============ OBJECTIVE FUNCTIONS ============

    /**
     * Set an objective to maximize.
     * @param name Name for debugging/reporting
     * @param priority Priority level for lexicographic ordering
     * @param expr Expression to maximize
     */
    fun maximize(name: String, priority: Int = 0, expr: Expr)

    /**
     * Set an objective to minimize.
     * @param name Name for debugging/reporting
     * @param priority Priority level for lexicographic ordering
     * @param expr Expression to minimize
     */
    fun minimize(name: String, priority: Int = 0, expr: Expr)

    // ============ PENALTY/REWARD ACCUMULATION ============

    /**
     * Add a penalty to the current soft constraint context.
     * Used inside soft constraint blocks.
     */
    fun penalize(penalty: Expr)

    /**
     * Add a penalty with a condition.
     */
    fun penalize(penalty: Expr, condition: Expr)

    /**
     * Add a reward to the current soft constraint context.
     * Used inside soft constraint blocks.
     */
    fun reward(reward: Expr)

    /**
     * Add a reward with a condition.
     */
    fun reward(reward: Expr, condition: Expr)

    // ============ SOLVER VALUE ACCESS ============

    /**
     * Get the solved value of a variable (only valid after solving).
     * @return Either an error or the value as a Long
     */
    fun value(expr: Expr): Either<ScopeError, Long>

    /**
     * Get the solved value as a Boolean.
     */
    fun boolValue(expr: Expr): Either<ScopeError, Boolean> = value(expr).map { it != 0L }

    /**
     * Get the solved value as a Double.
     */
    fun doubleValue(expr: Expr): Either<ScopeError, Double>
}

/**
 * DSL builder function for constraints.
 * Allows inline block syntax:
 * ```kotlin
 * hard("limit") { x + y leq 100L }
 * ```
 */
context(ConstraintScope)
inline fun hard(name: String, block: () -> Expr) {
    hard(name, block())
}

/**
 * DSL builder function for soft constraints with penalty.
 */
context(ConstraintScope)
inline fun soft(name: String, weight: Int = 1, priority: Int = 0, block: () -> Expr) {
    soft(name, weight, priority, block())
}

/**
 * DSL builder function for maximize objectives.
 */
context(ConstraintScope)
inline fun maximize(name: String, priority: Int = 0, block: () -> Expr) {
    maximize(name, priority, block())
}

/**
 * DSL builder function for minimize objectives.
 */
context(ConstraintScope)
inline fun minimize(name: String, priority: Int = 0, block: () -> Expr) {
    minimize(name, priority, block())
}


