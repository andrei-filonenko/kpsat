package io.github.andreifilonenko.kpsat.solver

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.IntVar
import io.github.andreifilonenko.kpsat.dsl.ConstraintScope
import io.github.andreifilonenko.kpsat.dsl.Expr
import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.ScopeError
import io.github.andreifilonenko.kpsat.dsl.VarType
import io.github.andreifilonenko.kpsat.dsl.errorContext

/**
 * Implementation of ConstraintScope for OR-Tools CP-SAT solver.
 * 
 * This class manages:
 * - Creation of decision variables
 * - Collection of constraints
 * - Compilation of DSL expressions to CP-SAT model
 */
class ConstraintSolverScope(
    internal val model: CpModel,
) : ConstraintScope {
    
    private var varIdCounter = 0
    private val varIdToIntVar = mutableMapOf<Int, IntVar>()
    private val nameToVarId = mutableMapOf<String, Int>()
    
    internal val hardConstraints = mutableListOf<HardConstraintSpec>()
    internal val objectives = mutableListOf<ObjectiveSpec>()
    
    private var currentPenalties = mutableListOf<Expr>()
    
    private var solver: CpSolver? = null
    
    internal val compiler: ExprCompiler by lazy {
        ExprCompiler(model, varIdToIntVar)
    }
    
    /**
     * Set the solver instance after solving to enable value extraction.
     */
    fun setSolver(solver: CpSolver) {
        this.solver = solver
    }
    
    /**
     * Get the IntVar for a variable ID.
     */
    fun getIntVar(varId: Int): IntVar? = varIdToIntVar[varId]
    
    /**
     * Get all variable IDs and their IntVars.
     */
    fun getAllVariables(): Map<Int, IntVar> = varIdToIntVar.toMap()
    
    // ============ SCALAR VARIABLE CREATION ============
    
    override fun bool(): Expr = bool("b_${varIdCounter}")
    
    override fun bool(name: String): Expr {
        val id = nextVarId()
        val intVar = model.newBoolVar(name)
        varIdToIntVar[id] = intVar
        nameToVarId[name] = id
        return Expr(ExprNode.Var(id, VarType.BOOL))
    }
    
    override fun int(min: Long, max: Long): Expr = int("i_${varIdCounter}", min, max)
    
    override fun int(name: String, min: Long, max: Long): Expr {
        val id = nextVarId()
        val intVar = model.newIntVar(min, max, name)
        varIdToIntVar[id] = intVar
        nameToVarId[name] = id
        return Expr(ExprNode.Var(id, VarType.INT))
    }
    
    override fun float(min: Double, max: Double): Expr = float("f_${varIdCounter}", min, max)
    
    override fun float(name: String, min: Double, max: Double): Expr {
        // CP-SAT doesn't support floats directly, so we scale to integers
        // Using 1000x scaling for 3 decimal places
        val scale = 1000L
        val scaledMin = (min * scale).toLong()
        val scaledMax = (max * scale).toLong()
        
        val id = nextVarId()
        val intVar = model.newIntVar(scaledMin, scaledMax, name)
        varIdToIntVar[id] = intVar
        nameToVarId[name] = id
        return Expr(ExprNode.Var(id, VarType.FLOAT))
    }
    
    // ============ ARRAY/LIST VARIABLE CREATION ============
    
    override fun intArray(size: Int, min: Long, max: Long): Expr {
        val elements = (0 until size).map { i ->
            val id = nextVarId()
            val intVar = model.newIntVar(min, max, "arr_${varIdCounter}_$i")
            varIdToIntVar[id] = intVar
            ExprNode.Var(id, VarType.INT)
        }
        return Expr(ExprNode.ArrayOf(elements))
    }
    
    override fun boolArray(size: Int): Expr {
        val elements = (0 until size).map { i ->
            val id = nextVarId()
            val intVar = model.newBoolVar("barr_${varIdCounter}_$i")
            varIdToIntVar[id] = intVar
            ExprNode.Var(id, VarType.BOOL)
        }
        return Expr(ExprNode.ArrayOf(elements))
    }
    
    
    // ============ CONSTANT ARRAYS ============
    
    override fun constArray(values: LongArray): Expr =
        Expr(ExprNode.ArrayLiteral(values))
    
    override fun constArray(values: DoubleArray): Expr =
        Expr(ExprNode.ArrayLiteralDouble(values))
    
    override fun constArray(values: List<Expr>): Expr =
        Expr(ExprNode.ArrayOf(values.map { it.node }))
    
    override fun constArray2D(values: Array<LongArray>): Expr {
        val rows = values.map { row -> ExprNode.ArrayLiteral(row) }
        return Expr(ExprNode.Array2D(rows))
    }
    
    // ============ CONSTRAINT EMISSION ============
    
    override fun hard(name: String, constraint: Expr) {
        hardConstraints.add(HardConstraintSpec(name, constraint))
    }
    
    override fun soft(name: String, weight: Int, priority: Int, penalty: Expr) {
        // For scope-level soft constraints, we add to objectives as minimize
        objectives.add(ObjectiveSpec(
            name = name,
            expr = penalty,
            direction = ObjectiveDirection.MINIMIZE,
            priority = priority,
        ))
    }
    
    // ============ OBJECTIVE FUNCTIONS ============
    
    override fun maximize(name: String, priority: Int, expr: Expr) {
        objectives.add(ObjectiveSpec(
            name = name,
            expr = expr,
            direction = ObjectiveDirection.MAXIMIZE,
            priority = priority,
        ))
    }
    
    override fun minimize(name: String, priority: Int, expr: Expr) {
        objectives.add(ObjectiveSpec(
            name = name,
            expr = expr,
            direction = ObjectiveDirection.MINIMIZE,
            priority = priority,
        ))
    }
    
    // ============ PENALTY/REWARD ACCUMULATION ============
    
    override fun penalize(penalty: Expr) {
        currentPenalties.add(penalty)
    }
    
    override fun penalize(penalty: Expr, condition: Expr) {
        val conditionalPenalty = Expr(ExprNode.If(
            condition.node,
            penalty.node,
            ExprNode.Const(0L)
        ))
        currentPenalties.add(conditionalPenalty)
    }
    
    override fun reward(reward: Expr) {
        // Reward is negative penalty
        currentPenalties.add(Expr(ExprNode.Neg(reward.node)))
    }
    
    override fun reward(reward: Expr, condition: Expr) {
        val conditionalReward = Expr(ExprNode.If(
            condition.node,
            ExprNode.Neg(reward.node),
            ExprNode.Const(0L)
        ))
        currentPenalties.add(conditionalReward)
    }
    
    /**
     * Collect and reset accumulated penalties.
     */
    internal fun collectPenalties(): List<Expr> {
        val penalties = currentPenalties.toList()
        currentPenalties.clear()
        return penalties
    }
    
    // ============ SOLVER VALUE ACCESS ============
    
    override fun value(expr: Expr): Either<ScopeError, Long> {
        val s = solver ?: return ScopeError.SolverNotExecuted(errorContext("value")).left()
        return when (val node = expr.node) {
            is ExprNode.Var -> {
                val intVar = varIdToIntVar[node.id]
                    ?: return ScopeError.VariableNotFound(node.id, errorContext("value")).left()
                s.value(intVar).right()
            }
            is ExprNode.Const -> node.value.right()
            else -> {
                // For complex expressions, compile and evaluate
                val compiled = compiler.compile(expr)
                when (compiled) {
                    is CompiledExpr.Constant -> compiled.value.right()
                    is CompiledExpr.Variable -> s.value(compiled.intVar).right()
                }
            }
        }
    }
    
    override fun doubleValue(expr: Expr): Either<ScopeError, Double> {
        return value(expr).map { it.toDouble() }
    }
    
    private fun nextVarId(): Int = varIdCounter++
}

/**
 * Specification for a hard constraint.
 */
data class HardConstraintSpec(
    val name: String,
    val constraint: Expr,
)

/**
 * Specification for an objective function.
 */
data class ObjectiveSpec(
    val name: String,
    val expr: Expr,
    val direction: ObjectiveDirection,
    val priority: Int,
)

/**
 * Direction of optimization.
 */
enum class ObjectiveDirection {
    MAXIMIZE,
    MINIMIZE,
}

