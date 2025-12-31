package io.github.andreifilonenko.kpsat.solver

import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Result of solving a constraint programming model.
 */
data class SolveResult(
    /**
     * Status of the solve operation.
     */
    val status: SolveStatus,
    
    /**
     * The optimal objective value (if applicable).
     */
    val objectiveValue: Double?,
    
    /**
     * The variables map from the model.
     */
    val variables: Map<String, Expr>,
    
    /**
     * The solver scope for accessing solution values.
     */
    val scope: ConstraintSolverScope,
    
    /**
     * Individual objective values by name (for multi-objective).
     */
    val objectiveValues: Map<String, Long> = emptyMap(),
    
    /**
     * Optional message (e.g., error description).
     */
    val message: String? = null,
) {
    
    /**
     * Whether a solution was found.
     */
    val hasSolution: Boolean
        get() = status == SolveStatus.OPTIMAL || status == SolveStatus.FEASIBLE
    
    /**
     * Whether the solution is proven optimal.
     */
    val isOptimal: Boolean
        get() = status == SolveStatus.OPTIMAL
    
    /**
     * Get the value of a variable by name.
     */
    fun getValue(varName: String): arrow.core.Either<io.github.andreifilonenko.kpsat.dsl.ScopeError, Long> {
        val expr = variables[varName] 
            ?: return io.github.andreifilonenko.kpsat.dsl.ScopeError.VariableNotFound(
                -1, 
                io.github.andreifilonenko.kpsat.dsl.errorContext("getValue", "Variable '$varName' not found")
            ).let { arrow.core.Either.Left(it) }
        return scope.value(expr)
    }
    
    /**
     * Get the boolean value of a variable by name.
     */
    fun getBoolValue(varName: String): arrow.core.Either<io.github.andreifilonenko.kpsat.dsl.ScopeError, Boolean> {
        return getValue(varName).map { it != 0L }
    }
    
    /**
     * Get all variable values as a map.
     */
    fun getAllValues(): Map<String, Long> {
        return variables.mapNotNull { (name, expr) ->
            scope.value(expr).getOrNull()?.let { name to it }
        }.toMap()
    }
    
    /**
     * Extract values using a custom extractor function.
     */
    inline fun <T> extractSolution(extractor: (SolveResult) -> T): T? {
        return if (hasSolution) extractor(this) else null
    }
    
    /**
     * Get a summary of the result for logging.
     */
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine("Solve Result:")
        sb.appendLine("  Status: $status")
        
        if (hasSolution) {
            objectiveValue?.let { sb.appendLine("  Objective: $it") }
            
            if (objectiveValues.isNotEmpty()) {
                sb.appendLine("  Objectives by name:")
                for ((name, value) in objectiveValues) {
                    sb.appendLine("    $name: $value")
                }
            }
        }
        
        message?.let { sb.appendLine("  Message: $it") }
        
        return sb.toString()
    }
    
    override fun toString(): String = summary()
}

/**
 * Builder for extracting typed solutions from a SolveResult.
 */
class SolutionExtractor<T>(
    private val result: SolveResult,
    private val extractor: (SolveResult) -> T,
) {
    
    /**
     * Get the extracted solution, or null if no solution exists.
     */
    fun getOrNull(): T? {
        return if (result.hasSolution) extractor(result) else null
    }
    
    /**
     * Get the extracted solution, or throw if no solution exists.
     */
    fun getOrThrow(): T {
        if (!result.hasSolution) {
            throw IllegalStateException("No solution found: ${result.status}")
        }
        return extractor(result)
    }
    
    /**
     * Get the extracted solution, or a default value if no solution exists.
     */
    fun getOrDefault(default: T): T {
        return getOrNull() ?: default
    }
}

/**
 * Create a solution extractor for a specific type.
 */
fun <T> SolveResult.extract(extractor: (SolveResult) -> T): SolutionExtractor<T> {
    return SolutionExtractor(this, extractor)
}

/**
 * Exception thrown when solving fails.
 */
class SolveException(
    val status: SolveStatus,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Solve and throw if not successful.
 */
fun BuiltSolver.solveOrThrow(): SolveResult {
    val result = solve()
    if (!result.hasSolution) {
        throw SolveException(
            result.status,
            result.message ?: "Solving failed with status: ${result.status}"
        )
    }
    return result
}


