package io.github.andreifilonenko.kpsat.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Compiled soft constraint ready for optimization.
 * 
 * Soft constraints at the same priority level are normalized to ensure fair comparison.
 * The normalization follows OptaPlanner's approach:
 * 1. Estimate min/max bounds for each constraint
 * 2. Scale penalties to a common range [0, NORMALIZATION_SCALE]
 * 3. Apply weights after normalization
 * 4. Sum normalized weighted penalties per priority level
 */
data class CompiledSoftConstraint(
    val name: String,
    val penaltyVar: IntVar,
    val weight: Int,
    val priority: Int,
    val minBound: Long,
    val maxBound: Long,
) {
    
    /**
     * Create a normalized penalty variable.
     * Normalized to range [0, NORMALIZATION_SCALE] then multiplied by weight.
     */
    fun createNormalizedPenalty(
        model: CpModel,
        scale: Long = NORMALIZATION_SCALE,
    ): IntVar {
        val range = maxBound - minBound
        
        if (range <= 0) {
            return model.newConstant(0)
        }
        
        val maxNormalized = scale * weight
        val normalizedVar = model.newIntVar(0, maxNormalized, "${name}_normalized")
        
        val shiftedPenalty = model.newIntVar(0, maxBound - minBound, "${name}_shifted")
        model.addEquality(
            shiftedPenalty,
            LinearExpr.newBuilder().add(penaltyVar).add(-minBound)
        )
        
        val scaledPenalty = model.newIntVar(0, (maxBound - minBound) * scale * weight, "${name}_scaled")
        model.addMultiplicationEquality(
            scaledPenalty,
            shiftedPenalty,
            model.newConstant(scale * weight)
        )
        
        model.addDivisionEquality(normalizedVar, scaledPenalty, model.newConstant(range))
        
        return normalizedVar
    }
    
    companion object {
        /**
         * Scale for normalization. Higher values provide more precision.
         * Using 1000 gives 0.1% precision which is typically sufficient.
         */
        const val NORMALIZATION_SCALE = 1000L
    }
}

/**
 * A group of soft constraints at the same priority level.
 */
data class SoftConstraintGroup(
    val priority: Int,
    val constraints: List<CompiledSoftConstraint>,
) {
    
    /**
     * Create the aggregated objective expression for this priority level.
     * Returns the sum of all normalized weighted penalties.
     */
    fun createGroupObjective(model: CpModel): IntVar {
        if (constraints.isEmpty()) {
            return model.newConstant(0)
        }
        
        if (constraints.size == 1) {
            return constraints[0].createNormalizedPenalty(model)
        }
        
        val normalizedVars = constraints.map { it.createNormalizedPenalty(model) }
        
        val maxSum = normalizedVars.sumOf { it.domain.max() }
        val sumVar = model.newIntVar(0, maxSum, "priority_${priority}_sum")
        
        model.addEquality(sumVar, LinearExpr.sum(normalizedVars.toTypedArray()))
        
        return sumVar
    }
}

/**
 * Strategy for estimating bounds of soft constraints.
 */
enum class BoundsEstimationStrategy {
    /**
     * Use explicit bounds provided by the user.
     * Falls back to domain-based if not provided.
     */
    EXPLICIT,
    
    /**
     * Infer bounds from variable domains.
     * Quick but may overestimate ranges.
     */
    DOMAIN_BASED,
    
    /**
     * Use sampling solves to find realistic bounds.
     * More accurate but slower.
     */
    SAMPLING,
    
    /**
     * Combine explicit bounds with domain inference.
     */
    HYBRID,
}

/**
 * Result of bounds estimation for a soft constraint.
 */
data class EstimatedBounds(
    val minBound: Long,
    val maxBound: Long,
    val strategy: BoundsEstimationStrategy,
    val confidence: Double = 1.0, 
)

/**
 * Estimates bounds for soft constraint penalties.
 */
object BoundsEstimator {
    
    /**
     * Estimate bounds for a compiled penalty expression.
     * 
     * @param penaltyVar The compiled penalty variable
     * @param explicitMin User-provided minimum bound
     * @param explicitMax User-provided maximum bound
     * @return Estimated bounds
     */
    fun estimate(
        penaltyVar: IntVar,
        explicitMin: Long?,
        explicitMax: Long?,
    ): EstimatedBounds {
        if (explicitMin != null && explicitMax != null) {
            return EstimatedBounds(
                minBound = explicitMin,
                maxBound = explicitMax,
                strategy = BoundsEstimationStrategy.EXPLICIT,
                confidence = 1.0,
            )
        }
        
        val domainMin = penaltyVar.domain.min()
        val domainMax = penaltyVar.domain.max()
        
        val min = explicitMin ?: domainMin
        val max = explicitMax ?: domainMax
        
        val strategy = when {
            explicitMin != null || explicitMax != null -> BoundsEstimationStrategy.HYBRID
            else -> BoundsEstimationStrategy.DOMAIN_BASED
        }
        
        val confidence = when (strategy) {
            BoundsEstimationStrategy.HYBRID -> 0.8
            BoundsEstimationStrategy.DOMAIN_BASED -> 0.5
            else -> 1.0
        }
        
        return EstimatedBounds(
            minBound = min,
            maxBound = max,
            strategy = strategy,
            confidence = confidence,
        )
    }
    
    /**
     * Estimate bounds using a quick sampling solve.
     * This is more accurate but takes time.
     */
    fun estimateWithSampling(
        model: CpModel,
        penaltyVar: IntVar,
        timeoutSeconds: Double = 1.0,
    ): EstimatedBounds {
        return EstimatedBounds(
            minBound = penaltyVar.domain.min(),
            maxBound = penaltyVar.domain.max(),
            strategy = BoundsEstimationStrategy.DOMAIN_BASED,
            confidence = 0.5,
        )
    }
}


