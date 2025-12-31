@file:Suppress("TooManyFunctions")

package io.github.andreifilonenko.kpsat.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import io.github.andreifilonenko.kpsat.dsl.ConstraintScope
import io.github.andreifilonenko.kpsat.dsl.Expr
import io.github.andreifilonenko.kpsat.dsl.sum

/**
 * Builder for constructing and solving constraint programming models.
 * 
 * Usage:
 * ```kotlin
 * val result = ConstraintSolverBuilder()
 *     .timeLimit(60)
 *     .variables { scope ->
 *         mapOf("x" to scope.int(0, 100), "y" to scope.int(0, 100))
 *     }
 *     .hard("capacity") { _, vars -> vars["x"]!! + vars["y"]!! leq 150L }
 *     .soft("preference", weight = 10, priority = 1) { _, vars -> 
 *         vars["x"]!! - 50L 
 *     }
 *     .maximize("profit", priority = 2) { _, vars -> vars["y"]!! }
 *     .onSolutionReady { scope, vars -> extractData(scope, vars) }
 *     .build()
 *     .solve()
 * ```
 */
class ConstraintSolverBuilder {
    
    private var timeLimitSeconds: Long = 60
    private var numSearchWorkers: Int = 8
    private var logSearchProgress: Boolean = false
    
    private val variableDefinitions = mutableListOf<(ConstraintScope) -> Map<String, Expr>>()
    private val hardConstraintDefs = mutableListOf<HardConstraintDef>()
    private val softConstraintDefs = mutableListOf<SoftConstraintDef>()
    private val objectiveDefs = mutableListOf<ObjectiveDef>()
    
    private var onSolutionCallback: ((ConstraintSolverScope, Map<String, Expr>) -> Unit)? = null
    private var onProgressCallback: ((SolveProgress) -> Unit)? = null
    
    init {
        // Load OR-Tools native library
        Loader.loadNativeLibraries()
    }
    
    // ============ CONFIGURATION ============
    
    /**
     * Set the time limit for solving in seconds.
     */
    fun timeLimit(seconds: Long): ConstraintSolverBuilder = apply {
        this.timeLimitSeconds = seconds
    }
    
    /**
     * Set the number of search workers (parallel threads).
     */
    fun numWorkers(workers: Int): ConstraintSolverBuilder = apply {
        this.numSearchWorkers = workers
    }
    
    /**
     * Enable logging of search progress.
     */
    fun logProgress(enable: Boolean = true): ConstraintSolverBuilder = apply {
        this.logSearchProgress = enable
    }
    
    // ============ VARIABLE DEFINITION ============
    
    /**
     * Define variables using a lambda that receives the scope.
     * @param block Lambda that takes ConstraintScope and returns a map of variable names to expressions
     */
    fun variables(block: (ConstraintScope) -> Map<String, Expr>): ConstraintSolverBuilder = apply {
        variableDefinitions.add(block)
    }
    
    // ============ HARD CONSTRAINTS ============
    
    /**
     * Add a hard constraint that must be satisfied.
     * @param name Name for debugging/reporting
     * @param constraint Lambda that takes (scope, variables) and produces a boolean expression (must be true)
     */
    fun hard(name: String, constraint: (ConstraintScope, Map<String, Expr>) -> Expr): ConstraintSolverBuilder = apply {
        hardConstraintDefs.add(HardConstraintDef(name, constraint))
    }
    
    // ============ SOFT CONSTRAINTS ============
    
    /**
     * Add a soft constraint with a penalty expression.
     * @param name Name for debugging/reporting
     * @param weight Weight multiplier for the penalty
     * @param priority Priority level (higher = more important)
     * @param minBound Optional minimum bound for normalization
     * @param maxBound Optional maximum bound for normalization
     * @param penalty Lambda that takes (scope, variables) and produces a penalty expression (>= 0 typically)
     */
    fun soft(
        name: String,
        weight: Int = 1,
        priority: Int = 0,
        minBound: Long? = null,
        maxBound: Long? = null,
        penalty: (ConstraintScope, Map<String, Expr>) -> Expr,
    ): ConstraintSolverBuilder = apply {
        softConstraintDefs.add(SoftConstraintDef(
            name = name,
            weight = weight,
            priority = priority,
            minBound = minBound,
            maxBound = maxBound,
            penalty = penalty,
        ))
    }
    
    // ============ OBJECTIVES ============
    
    /**
     * Add a maximize objective.
     * @param name Name for debugging/reporting
     * @param priority Priority level for lexicographic ordering
     * @param expr Lambda that takes (scope, variables) and produces the expression to maximize
     */
    fun maximize(
        name: String,
        priority: Int = 0,
        expr: (ConstraintScope, Map<String, Expr>) -> Expr,
    ): ConstraintSolverBuilder = apply {
        objectiveDefs.add(ObjectiveDef(name, ObjectiveDirection.MAXIMIZE, priority, expr))
    }
    
    /**
     * Add a minimize objective.
     * @param name Name for debugging/reporting
     * @param priority Priority level for lexicographic ordering
     * @param expr Lambda that takes (scope, variables) and produces the expression to minimize
     */
    fun minimize(
        name: String,
        priority: Int = 0,
        expr: (ConstraintScope, Map<String, Expr>) -> Expr,
    ): ConstraintSolverBuilder = apply {
        objectiveDefs.add(ObjectiveDef(name, ObjectiveDirection.MINIMIZE, priority, expr))
    }
    
    // ============ CALLBACKS ============
    
    /**
     * Set a callback to extract data when a solution is found.
     */
    fun onSolutionReady(callback: (ConstraintSolverScope, Map<String, Expr>) -> Unit): ConstraintSolverBuilder = apply {
        this.onSolutionCallback = callback
    }
    
    /**
     * Set a callback for progress updates during solving.
     */
    fun onProgress(callback: (SolveProgress) -> Unit): ConstraintSolverBuilder = apply {
        this.onProgressCallback = callback
    }
    
    // ============ RUNTIME CONFIGURATION ============
    // These methods can be called any time before solve() to configure
    // constraint weights and priorities. Useful for API endpoints or LLM function calling.
    
    /**
     * Set the weight of a soft constraint by name.
     * Can be called any time before solve().
     * @param name The name of the soft constraint
     * @param weight The new weight value
     * @throws IllegalArgumentException if no soft constraint with that name exists
     */
    fun setWeight(name: String, weight: Int): ConstraintSolverBuilder = apply {
        val constraint = softConstraintDefs.find { it.name == name }
            ?: throw IllegalArgumentException("No soft constraint named '$name'")
        constraint.weight = weight
    }
    
    /**
     * Set the priority of a soft constraint or objective by name.
     * Can be called any time before solve().
     * @param name The name of the soft constraint or objective
     * @param priority The new priority value
     * @throws IllegalArgumentException if no constraint/objective with that name exists
     */
    fun setPriority(name: String, priority: Int): ConstraintSolverBuilder = apply {
        val soft = softConstraintDefs.find { it.name == name }
        val objective = objectiveDefs.find { it.name == name }
        when {
            soft != null -> soft.priority = priority
            objective != null -> objective.priority = priority
            else -> throw IllegalArgumentException("No soft constraint or objective named '$name'")
        }
    }
    
    /**
     * Enable a constraint or objective by name.
     * Can be called any time before solve().
     * @param name The name of the hard/soft constraint or objective
     * @throws IllegalArgumentException if no constraint/objective with that name exists
     */
    fun enable(name: String): ConstraintSolverBuilder = apply {
        val hard = hardConstraintDefs.find { it.name == name }
        val soft = softConstraintDefs.find { it.name == name }
        val objective = objectiveDefs.find { it.name == name }
        when {
            hard != null -> hard.enabled = true
            soft != null -> soft.enabled = true
            objective != null -> objective.enabled = true
            else -> throw IllegalArgumentException("No constraint or objective named '$name'")
        }
    }
    
    /**
     * Disable a constraint or objective by name.
     * Can be called any time before solve().
     * @param name The name of the hard/soft constraint or objective
     * @throws IllegalArgumentException if no constraint/objective with that name exists
     */
    fun disable(name: String): ConstraintSolverBuilder = apply {
        val hard = hardConstraintDefs.find { it.name == name }
        val soft = softConstraintDefs.find { it.name == name }
        val objective = objectiveDefs.find { it.name == name }
        when {
            hard != null -> hard.enabled = false
            soft != null -> soft.enabled = false
            objective != null -> objective.enabled = false
            else -> throw IllegalArgumentException("No constraint or objective named '$name'")
        }
    }
    
    /**
     * Get the current configuration of all hard constraints.
     * Useful for inspecting the model state before solving.
     */
    fun getHardConstraints(): List<HardConstraintDef> = hardConstraintDefs.toList()
    
    /**
     * Get the current configuration of all soft constraints.
     * Useful for inspecting the model state before solving.
     */
    fun getSoftConstraints(): List<SoftConstraintDef> = softConstraintDefs.toList()
    
    /**
     * Get the current configuration of all objectives.
     * Useful for inspecting the model state before solving.
     */
    fun getObjectives(): List<ObjectiveDef> = objectiveDefs.toList()
    
    // ============ BUILD AND SOLVE ============
    
    /**
     * Build the solver configuration.
     */
    fun build(): BuiltSolver {
        return BuiltSolver(
            timeLimitSeconds = timeLimitSeconds,
            numSearchWorkers = numSearchWorkers,
            logSearchProgress = logSearchProgress,
            variableDefinitions = variableDefinitions.toList(),
            hardConstraintDefs = hardConstraintDefs.toList(),
            softConstraintDefs = softConstraintDefs.toList(),
            objectiveDefs = objectiveDefs.toList(),
            onSolutionCallback = onSolutionCallback,
            onProgressCallback = onProgressCallback,
        )
    }
}

/**
 * Definition for a hard constraint.
 * Mutable enabled property allows runtime configuration before solving.
 */
data class HardConstraintDef(
    val name: String,
    val constraint: (ConstraintScope, Map<String, Expr>) -> Expr,
    var enabled: Boolean = true,
)

/**
 * Definition for a soft constraint.
 * Mutable properties allow runtime configuration before solving.
 */
data class SoftConstraintDef(
    val name: String,
    var weight: Int,
    var priority: Int,
    val minBound: Long?,
    val maxBound: Long?,
    val penalty: (ConstraintScope, Map<String, Expr>) -> Expr,
    var enabled: Boolean = true,
)

/**
 * Definition for an objective.
 * Mutable properties allow runtime configuration before solving.
 */
data class ObjectiveDef(
    val name: String,
    val direction: ObjectiveDirection,
    var priority: Int,
    val expr: (ConstraintScope, Map<String, Expr>) -> Expr,
    var enabled: Boolean = true,
)

/**
 * Progress information during solving.
 */
data class SolveProgress(
    val objectiveValue: Double,
    val bestBound: Double,
    val elapsedSeconds: Double,
    val solutionCount: Int,
)

/**
 * A built solver ready to execute.
 */
class BuiltSolver internal constructor(
    private val timeLimitSeconds: Long,
    private val numSearchWorkers: Int,
    private val logSearchProgress: Boolean,
    private val variableDefinitions: List<(ConstraintScope) -> Map<String, Expr>>,
    private val hardConstraintDefs: List<HardConstraintDef>,
    private val softConstraintDefs: List<SoftConstraintDef>,
    private val objectiveDefs: List<ObjectiveDef>,
    private val onSolutionCallback: ((ConstraintSolverScope, Map<String, Expr>) -> Unit)?,
    private val onProgressCallback: ((SolveProgress) -> Unit)?,
) {
    
    /**
     * Solve the model and return the result.
     */
    fun solve(): SolveResult {
        val model = CpModel()
        val scope = ConstraintSolverScope(model)
        
        // Create variables
        val vars = mutableMapOf<String, Expr>()
        for (varDef in variableDefinitions) {
            vars.putAll(varDef(scope))
        }
        
        // Add hard constraints (only enabled ones)
        for (hardDef in hardConstraintDefs.filter { it.enabled }) {
            val constraint = hardDef.constraint(scope, vars)
            scope.hard(hardDef.name, constraint)
        }
        
        // Compile and add hard constraints to model
        for (hardSpec in scope.hardConstraints) {
            val compiled = scope.compiler.compile(hardSpec.constraint)
            when (compiled) {
                is CompiledExpr.Variable -> model.addEquality(compiled.intVar, 1)
                is CompiledExpr.Constant -> {
                    if (compiled.value == 0L) {
                        return SolveResult(
                            status = SolveStatus.INFEASIBLE,
                            objectiveValue = null,
                            variables = vars,
                            scope = scope,
                            message = "Hard constraint '${hardSpec.name}' evaluates to false",
                        )
                    }
                }
            }
        }
        
        // Process soft constraints and objectives using lexicographic solver
        // Filter out disabled constraints and objectives
        val enabledSoftConstraints = softConstraintDefs.filter { it.enabled }
        val enabledObjectives = objectiveDefs.filter { it.enabled }
        
        val lexicographicSolver = LexicographicSolver(
            model = model,
            scope = scope,
            vars = vars,
            softConstraintDefs = enabledSoftConstraints,
            objectiveDefs = enabledObjectives,
            timeLimitSeconds = timeLimitSeconds,
            numSearchWorkers = numSearchWorkers,
            logSearchProgress = logSearchProgress,
            onProgressCallback = onProgressCallback,
        )
        
        return lexicographicSolver.solve(onSolutionCallback)
    }
}

/**
 * Status of the solve operation.
 */
enum class SolveStatus {
    OPTIMAL,
    FEASIBLE,
    INFEASIBLE,
    UNKNOWN,
    MODEL_INVALID,
    ;
    
    companion object {
        fun from(cpStatus: CpSolverStatus): SolveStatus = when (cpStatus) {
            CpSolverStatus.OPTIMAL -> OPTIMAL
            CpSolverStatus.FEASIBLE -> FEASIBLE
            CpSolverStatus.INFEASIBLE -> INFEASIBLE
            CpSolverStatus.UNKNOWN -> UNKNOWN
            CpSolverStatus.MODEL_INVALID -> MODEL_INVALID
            else -> UNKNOWN
        }
    }
}


