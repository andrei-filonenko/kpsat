@file:Suppress("TooManyFunctions", "LongMethod", "ComplexMethod")

package io.github.andreifilonenko.kpsat.solver

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import arrow.core.NonEmptyList
import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.VarType

// ============================================================================
// SECTION 1: Core Types (value classes for type safety)
// ============================================================================

/**
 * Unique identifier for an equivalence class in the e-graph.
 */
@JvmInline
value class EClassId(val id: Int) {
    override fun toString(): String = "e$id"
}

// ============================================================================
// SECTION 2: ENodeOp - Sealed interface mirroring ExprNode operations
// ============================================================================

/**
 * E-graph node operation. This is a simplified representation of ExprNode
 * where children are replaced by EClassId references.
 * 
 * IMPORTANT: This sealed interface must have a variant for every ExprNode variant.
 * The compiler enforces exhaustive `when` expressions.
 */
sealed interface ENodeOp {
    // ============ LITERALS ============
    data class Const(val value: Long) : ENodeOp
    data class ConstDouble(val value: Double) : ENodeOp
    data class Var(val id: Int, val type: VarType) : ENodeOp
    data class ArrayLiteral(val values: LongArray) : ENodeOp {
        override fun equals(other: Any?) = other is ArrayLiteral && values.contentEquals(other.values)
        override fun hashCode() = values.contentHashCode()
    }
    data class ArrayLiteralDouble(val values: DoubleArray) : ENodeOp {
        override fun equals(other: Any?) = other is ArrayLiteralDouble && values.contentEquals(other.values)
        override fun hashCode() = values.contentHashCode()
    }
    data object ArrayOf : ENodeOp
    data object Array2D : ENodeOp

    // ============ ARITHMETIC ============
    data object Sum : ENodeOp
    data object Sub : ENodeOp
    data object Prod : ENodeOp
    data object Div : ENodeOp
    data object Mod : ENodeOp
    data object Neg : ENodeOp

    // ============ COMPARISON ============
    data object Eq : ENodeOp
    data object Neq : ENodeOp
    data object Lt : ENodeOp
    data object Leq : ENodeOp
    data object Gt : ENodeOp
    data object Geq : ENodeOp

    // ============ LOGICAL ============
    data object And : ENodeOp
    data object Or : ENodeOp
    data object Not : ENodeOp
    data object Xor : ENodeOp

    // ============ CONDITIONAL ============
    data object If : ENodeOp

    // ============ COLLECTION OPERATIONS ============
    data object At : ENodeOp
    data object At2D : ENodeOp
    data object Count : ENodeOp
    data object Contains : ENodeOp
    data object Find : ENodeOp
    data object IndexOf : ENodeOp

    // ============ SET OPERATIONS ============
    data object Intersection : ENodeOp
    data object Union : ENodeOp

    // ============ LIST OPERATIONS ============
    /** Sort with optional key - key function is not representable, stored as flag */
    data class Sort(val hasKey: Boolean) : ENodeOp

    // ============ AGGREGATIONS ============
    data object Min : ENodeOp
    data object Max : ENodeOp

    // ============ LAMBDA AGGREGATIONS ============
    // These contain lambdas which cannot be represented in e-graph.
    // We mark them as "opaque" - they won't participate in rewriting.
    data class SumOver(val lambdaId: Int) : ENodeOp
    data class ProdOver(val lambdaId: Int) : ENodeOp
    data class MinOver(val lambdaId: Int) : ENodeOp
    data class MaxOver(val lambdaId: Int) : ENodeOp
    data class AndOver(val lambdaId: Int) : ENodeOp
    data class OrOver(val lambdaId: Int) : ENodeOp

    // ============ MATH FUNCTIONS ============
    data object Abs : ENodeOp
    data object Sqrt : ENodeOp
    data object Pow : ENodeOp
    data object Exp : ENodeOp
    data object Log : ENodeOp
    data object Ceil : ENodeOp
    data object Floor : ENodeOp
    data object Round : ENodeOp

    // ============ DOMAIN CONSTRAINTS ============
    data class InDomain(val domain: LongArray) : ENodeOp {
        override fun equals(other: Any?) = other is InDomain && domain.contentEquals(other.domain)
        override fun hashCode() = domain.contentHashCode()
    }

    // ============ OPTIMIZED AGGREGATIONS ============
    data object CountTrue : ENodeOp
    data object IndicatorSum : ENodeOp
}

// ============================================================================
// SECTION 3: ENode and EClass
// ============================================================================

/**
 * An e-node represents a single expression with its operation and children
 * (which are references to equivalence classes, not direct children).
 */
data class ENode(
    val op: ENodeOp,
    val children: List<EClassId>
) {
    /**
     * Canonicalize this e-node by replacing children with their canonical representatives.
     */
    fun canonicalize(find: (EClassId) -> EClassId): ENode =
        ENode(op, children.map { find(it) })
}

/**
 * An equivalence class (e-class) contains all e-nodes that are known to be equivalent.
 * Uses Arrow's NonEmptyList to guarantee at least one node exists.
 */
data class EClass(
    val id: EClassId,
    val nodes: NonEmptyList<ENode>,
    val parents: Set<Pair<ENode, EClassId>> = emptySet()
)

// ============================================================================
// SECTION 4: UnionFind with path compression
// ============================================================================

/**
 * Union-Find data structure with path compression for efficient equivalence class management.
 */
class UnionFind {
    private val parent = mutableMapOf<EClassId, EClassId>()

    /**
     * Make a new set with the given ID as its own parent.
     */
    fun makeSet(id: EClassId) {
        parent[id] = id
    }

    /**
     * Find the canonical representative of the set containing [id].
     * Uses path compression for efficiency.
     */
    fun find(id: EClassId): EClassId {
        val p = parent[id] ?: return id
        if (p == id) return id
        val root = find(p)
        parent[id] = root  // Path compression
        return root
    }

    /**
     * Union two sets, returning the new canonical representative.
     * Always makes [id1] the parent of [id2] for determinism.
     */
    fun union(id1: EClassId, id2: EClassId): EClassId {
        val root1 = find(id1)
        val root2 = find(id2)
        if (root1 != root2) {
            parent[root2] = root1
        }
        return root1
    }
}

// ============================================================================
// SECTION 5: EGraph - Core data structure
// ============================================================================

/**
 * E-graph (equivalence graph) data structure for equality saturation.
 * 
 * An e-graph compactly represents many equivalent expressions by grouping
 * them into equivalence classes (e-classes). This enables efficient
 * application of rewrite rules and extraction of optimal expressions.
 */
class EGraph private constructor(
    private val eclasses: MutableMap<EClassId, EClass>,
    private val hashcons: MutableMap<ENode, EClassId>,
    private val unionFind: UnionFind,
    private var nextId: Int,
    private val lambdaRegistry: MutableMap<Int, (ExprNode) -> ExprNode>,
    private var nextLambdaId: Int
) {
    companion object {
        /**
         * Create an empty e-graph.
         */
        fun empty(): EGraph = EGraph(
            eclasses = mutableMapOf(),
            hashcons = mutableMapOf(),
            unionFind = UnionFind(),
            nextId = 0,
            lambdaRegistry = mutableMapOf(),
            nextLambdaId = 0
        )

        /**
         * Create an e-graph from an expression, returning the root e-class ID.
         */
        fun fromExpr(expr: ExprNode): Pair<EGraph, EClassId> {
            val graph = empty()
            val rootId = graph.addExpr(expr)
            return graph to rootId
        }
    }

    /**
     * Get the number of e-classes in the graph.
     */
    val eclassCount: Int get() = eclasses.size

    /**
     * Get the total number of e-nodes across all e-classes.
     */
    val nodeCount: Int get() = eclasses.values.sumOf { it.nodes.size }

    /**
     * Find the canonical representative of an e-class.
     */
    fun find(id: EClassId): EClassId = unionFind.find(id)

    /**
     * Get an e-class by its ID (after canonicalization).
     */
    fun getClass(id: EClassId): Option<EClass> {
        val canonical = find(id)
        return eclasses[canonical]?.let { Some(it) } ?: None
    }

    /**
     * Add an e-node to the e-graph, returning its e-class ID.
     * If an equivalent e-node already exists, returns its e-class.
     */
    fun add(node: ENode): EClassId {
        val canonical = node.canonicalize(::find)
        
        // Check if this node already exists (hash-consing)
        hashcons[canonical]?.let { return find(it) }

        // Create new e-class
        val newId = EClassId(nextId++)
        unionFind.makeSet(newId)
        
        val eclass = EClass(
            id = newId,
            nodes = nonEmptyListOf(canonical)
        )
        eclasses[newId] = eclass
        hashcons[canonical] = newId

        // Update parent pointers for children
        for (childId in canonical.children) {
            val childCanonical = find(childId)
            eclasses[childCanonical]?.let { childClass ->
                eclasses[childCanonical] = childClass.copy(
                    parents = childClass.parents + (canonical to newId)
                )
            }
        }

        return newId
    }

    /**
     * Merge two e-classes, asserting they are equivalent.
     * Returns the canonical ID of the merged class.
     */
    fun merge(id1: EClassId, id2: EClassId): EClassId {
        val root1 = find(id1)
        val root2 = find(id2)
        
        if (root1 == root2) return root1

        // Union in the union-find
        val newRoot = unionFind.union(root1, root2)
        val obsolete = if (newRoot == root1) root2 else root1

        // Merge e-class data
        val class1 = eclasses[root1] ?: return newRoot
        val class2 = eclasses[root2] ?: return newRoot

        val mergedNodes = (class1.nodes.toList() + class2.nodes.toList())
            .toNonEmptyListOrNull() ?: return newRoot

        val mergedParents = class1.parents + class2.parents

        eclasses[newRoot] = EClass(
            id = newRoot,
            nodes = mergedNodes,
            parents = mergedParents
        )
        eclasses.remove(obsolete)

        return newRoot
    }

    /**
     * Rebuild the e-graph to restore invariants after merges.
     * This re-canonicalizes all e-nodes and may trigger further merges.
     */
    fun rebuild() {
        // Collect all nodes that need to be re-canonicalized
        val worklist = hashcons.keys.toMutableList()
        hashcons.clear()

        for (node in worklist) {
            val canonical = node.canonicalize(::find)
            val existingId = hashcons[canonical]
            val nodeClassId = eclasses.entries
                .find { (_, eclass) -> node in eclass.nodes.toList() }
                ?.key?.let { find(it) }

            if (existingId != null && nodeClassId != null && existingId != nodeClassId) {
                merge(existingId, nodeClassId)
            }
            
            if (nodeClassId != null) {
                hashcons[canonical] = find(nodeClassId)
            }
        }

        // Update all e-classes with canonicalized nodes
        val newEclasses = mutableMapOf<EClassId, EClass>()
        for ((id, eclass) in eclasses) {
            val canonicalId = find(id)
            if (canonicalId == id) {
                val canonicalNodes = eclass.nodes.map { it.canonicalize(::find) }
                    .distinct()
                    .toNonEmptyListOrNull() ?: continue
                newEclasses[id] = eclass.copy(nodes = canonicalNodes)
            }
        }
        eclasses.clear()
        eclasses.putAll(newEclasses)
    }

    /**
     * Register a lambda function and get its ID for opaque node representation.
     */
    internal fun registerLambda(lambda: (ExprNode) -> ExprNode): Int {
        val id = nextLambdaId++
        lambdaRegistry[id] = lambda
        return id
    }

    /**
     * Get a registered lambda by ID.
     */
    internal fun getLambda(id: Int): Option<(ExprNode) -> ExprNode> =
        lambdaRegistry[id]?.let { Some(it) } ?: None

    /**
     * Get all e-class IDs in the graph.
     */
    fun allEClassIds(): Set<EClassId> = eclasses.keys.toSet()

    // =========================================================================
    // ExprNode Conversion - EXHAUSTIVE when (no else branch!)
    // =========================================================================

    /**
     * Add an ExprNode to the e-graph, returning its e-class ID.
     * 
     * IMPORTANT: This uses exhaustive `when` without `else` branch.
     * The compiler will error if any ExprNode variant is not handled.
     */
    fun addExpr(expr: ExprNode): EClassId = when (expr) {
        // ============ LITERALS ============
        is ExprNode.Const -> add(ENode(ENodeOp.Const(expr.value), emptyList()))
        is ExprNode.ConstDouble -> add(ENode(ENodeOp.ConstDouble(expr.value), emptyList()))
        is ExprNode.Var -> add(ENode(ENodeOp.Var(expr.id, expr.type), emptyList()))
        is ExprNode.ArrayLiteral -> add(ENode(ENodeOp.ArrayLiteral(expr.values), emptyList()))
        is ExprNode.ArrayLiteralDouble -> add(ENode(ENodeOp.ArrayLiteralDouble(expr.values), emptyList()))
        is ExprNode.ArrayOf -> add(ENode(ENodeOp.ArrayOf, expr.elements.map { addExpr(it) }))
        is ExprNode.Array2D -> add(ENode(ENodeOp.Array2D, expr.rows.map { addExpr(it) }))

        // ============ ARITHMETIC ============
        is ExprNode.Sum -> add(ENode(ENodeOp.Sum, expr.operands.map { addExpr(it) }))
        is ExprNode.Sub -> add(ENode(ENodeOp.Sub, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Prod -> add(ENode(ENodeOp.Prod, expr.operands.map { addExpr(it) }))
        is ExprNode.Div -> add(ENode(ENodeOp.Div, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Mod -> add(ENode(ENodeOp.Mod, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Neg -> add(ENode(ENodeOp.Neg, listOf(addExpr(expr.operand))))

        // ============ COMPARISON ============
        is ExprNode.Eq -> add(ENode(ENodeOp.Eq, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Neq -> add(ENode(ENodeOp.Neq, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Lt -> add(ENode(ENodeOp.Lt, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Leq -> add(ENode(ENodeOp.Leq, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Gt -> add(ENode(ENodeOp.Gt, listOf(addExpr(expr.left), addExpr(expr.right))))
        is ExprNode.Geq -> add(ENode(ENodeOp.Geq, listOf(addExpr(expr.left), addExpr(expr.right))))

        // ============ LOGICAL ============
        is ExprNode.And -> add(ENode(ENodeOp.And, expr.operands.map { addExpr(it) }))
        is ExprNode.Or -> add(ENode(ENodeOp.Or, expr.operands.map { addExpr(it) }))
        is ExprNode.Not -> add(ENode(ENodeOp.Not, listOf(addExpr(expr.operand))))
        is ExprNode.Xor -> add(ENode(ENodeOp.Xor, expr.operands.map { addExpr(it) }))

        // ============ CONDITIONAL ============
        is ExprNode.If -> add(ENode(ENodeOp.If, listOf(
            addExpr(expr.condition),
            addExpr(expr.ifTrue),
            addExpr(expr.ifFalse)
        )))

        // ============ COLLECTION OPERATIONS ============
        is ExprNode.At -> add(ENode(ENodeOp.At, listOf(addExpr(expr.array), addExpr(expr.index))))
        is ExprNode.At2D -> add(ENode(ENodeOp.At2D, listOf(
            addExpr(expr.array),
            addExpr(expr.i),
            addExpr(expr.j)
        )))
        is ExprNode.Count -> add(ENode(ENodeOp.Count, listOf(addExpr(expr.collection))))
        is ExprNode.Contains -> add(ENode(ENodeOp.Contains, listOf(
            addExpr(expr.collection),
            addExpr(expr.element)
        )))
        is ExprNode.Find -> add(ENode(ENodeOp.Find, listOf(addExpr(expr.array), addExpr(expr.value))))
        is ExprNode.IndexOf -> add(ENode(ENodeOp.IndexOf, listOf(addExpr(expr.list), addExpr(expr.element))))

        // ============ SET OPERATIONS ============
        is ExprNode.Intersection -> add(ENode(ENodeOp.Intersection, expr.sets.map { addExpr(it) }))
        is ExprNode.Union -> add(ENode(ENodeOp.Union, expr.sets.map { addExpr(it) }))

        // ============ LIST OPERATIONS ============
        is ExprNode.Sort -> {
            // Lambda cannot be represented - store flag and treat as opaque
            add(ENode(ENodeOp.Sort(expr.key != null), listOf(addExpr(expr.list))))
        }

        // ============ AGGREGATIONS ============
        is ExprNode.Min -> add(ENode(ENodeOp.Min, expr.operands.map { addExpr(it) }))
        is ExprNode.Max -> add(ENode(ENodeOp.Max, expr.operands.map { addExpr(it) }))

        // ============ LAMBDA AGGREGATIONS (opaque - store lambda ID) ============
        is ExprNode.SumOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.SumOver(lambdaId), listOf(addExpr(expr.collection))))
        }
        is ExprNode.ProdOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.ProdOver(lambdaId), listOf(addExpr(expr.collection))))
        }
        is ExprNode.MinOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.MinOver(lambdaId), listOf(addExpr(expr.collection))))
        }
        is ExprNode.MaxOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.MaxOver(lambdaId), listOf(addExpr(expr.collection))))
        }
        is ExprNode.AndOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.AndOver(lambdaId), listOf(addExpr(expr.collection))))
        }
        is ExprNode.OrOver -> {
            val lambdaId = registerLambda(expr.lambda)
            add(ENode(ENodeOp.OrOver(lambdaId), listOf(addExpr(expr.collection))))
        }

        // ============ MATH FUNCTIONS ============
        is ExprNode.Abs -> add(ENode(ENodeOp.Abs, listOf(addExpr(expr.operand))))
        is ExprNode.Sqrt -> add(ENode(ENodeOp.Sqrt, listOf(addExpr(expr.operand))))
        is ExprNode.Pow -> add(ENode(ENodeOp.Pow, listOf(addExpr(expr.base), addExpr(expr.exponent))))
        is ExprNode.Exp -> add(ENode(ENodeOp.Exp, listOf(addExpr(expr.operand))))
        is ExprNode.Log -> add(ENode(ENodeOp.Log, listOf(addExpr(expr.operand))))
        is ExprNode.Ceil -> add(ENode(ENodeOp.Ceil, listOf(addExpr(expr.operand))))
        is ExprNode.Floor -> add(ENode(ENodeOp.Floor, listOf(addExpr(expr.operand))))
        is ExprNode.Round -> add(ENode(ENodeOp.Round, listOf(addExpr(expr.operand))))

        // ============ DOMAIN CONSTRAINTS ============
        is ExprNode.InDomain -> add(ENode(ENodeOp.InDomain(expr.domain), listOf(addExpr(expr.variable))))

        // ============ OPTIMIZED AGGREGATIONS ============
        is ExprNode.CountTrue -> add(ENode(ENodeOp.CountTrue, expr.conditions.map { addExpr(it) }))
        is ExprNode.IndicatorSum -> {
            // Flatten pairs into: [cond1, val1, cond2, val2, ...]
            val children = expr.terms.flatMap { (cond, value) ->
                listOf(addExpr(cond), addExpr(value))
            }
            add(ENode(ENodeOp.IndicatorSum, children))
        }
    }
}

// ============================================================================
// SECTION 6: Pattern Matching
// ============================================================================

/**
 * Pattern for matching e-graph nodes.
 */
sealed interface Pattern {
    /** Pattern variable - matches any e-class */
    data class PVar(val name: String) : Pattern

    /** Pattern operation - matches specific operation with child patterns */
    data class POp(val op: ENodeOp, val children: List<Pattern>) : Pattern

    /** Constant pattern - matches specific constant value */
    data class PConst(val value: Long) : Pattern
}

/**
 * A substitution maps pattern variable names to e-class IDs.
 */
data class Substitution(val bindings: Map<String, EClassId>) {
    operator fun get(name: String): EClassId? = bindings[name]
    
    fun with(name: String, id: EClassId): Substitution =
        Substitution(bindings + (name to id))
}

/**
 * Match a pattern against an e-class, returning all valid substitutions.
 */
fun Pattern.match(egraph: EGraph, eclassId: EClassId): List<Substitution> {
    return matchWithSubst(egraph, eclassId, Substitution(emptyMap()))
}

private fun Pattern.matchWithSubst(
    egraph: EGraph,
    eclassId: EClassId,
    subst: Substitution
): List<Substitution> {
    val canonicalId = egraph.find(eclassId)
    
    return when (this) {
        is Pattern.PVar -> {
            // Check if this variable is already bound
            val existing = subst[name]
            if (existing != null) {
                if (egraph.find(existing) == canonicalId) listOf(subst) else emptyList()
            } else {
                listOf(subst.with(name, canonicalId))
            }
        }
        
        is Pattern.PConst -> {
            // Match against constant nodes in the e-class
            egraph.getClass(canonicalId).fold(
                ifEmpty = { emptyList() },
                ifSome = { eclass ->
                    val hasMatch = eclass.nodes.any { node ->
                        node.op is ENodeOp.Const && node.op.value == value
                    }
                    if (hasMatch) listOf(subst) else emptyList()
                }
            )
        }
        
        is Pattern.POp -> {
            egraph.getClass(canonicalId).fold(
                ifEmpty = { emptyList() },
                ifSome = { eclass ->
                    eclass.nodes.toList().flatMap { node ->
                        if (matchesOp(node.op, op) && node.children.size == children.size) {
                            // Recursively match children
                            matchChildren(egraph, node.children, children, subst)
                        } else {
                            emptyList()
                        }
                    }
                }
            )
        }
    }
}

private fun matchesOp(nodeOp: ENodeOp, patternOp: ENodeOp): Boolean {
    // For data objects, direct equality works
    // For data classes with values, we need structural matching
    return when {
        nodeOp is ENodeOp.Const && patternOp is ENodeOp.Const -> nodeOp.value == patternOp.value
        nodeOp::class == patternOp::class -> nodeOp == patternOp
        else -> false
    }
}

private fun matchChildren(
    egraph: EGraph,
    nodeChildren: List<EClassId>,
    patternChildren: List<Pattern>,
    subst: Substitution
): List<Substitution> {
    if (nodeChildren.isEmpty() && patternChildren.isEmpty()) {
        return listOf(subst)
    }
    if (nodeChildren.size != patternChildren.size) {
        return emptyList()
    }
    
    var currentSubsts = listOf(subst)
    for ((childId, childPattern) in nodeChildren.zip(patternChildren)) {
        currentSubsts = currentSubsts.flatMap { s ->
            childPattern.matchWithSubst(egraph, childId, s)
        }
        if (currentSubsts.isEmpty()) break
    }
    return currentSubsts
}

// ============================================================================
// SECTION 7: Rewrite Rules
// ============================================================================

/**
 * A rewrite rule that transforms matching patterns.
 */
data class RewriteRule(
    val name: String,
    val lhs: Pattern,
    val rhs: Pattern
) {
    /**
     * Apply this rule to the e-graph, returning pairs of e-classes to merge.
     */
    fun apply(egraph: EGraph): List<Pair<EClassId, EClassId>> {
        val matches = mutableListOf<Pair<EClassId, EClassId>>()
        
        // Search all e-classes for matches
        for (eclassId in egraph.allEClassIds()) {
            val substitutions = lhs.match(egraph, eclassId)
            for (subst in substitutions) {
                // Build the RHS with the substitution
                val rhsId = instantiate(egraph, rhs, subst)
                if (rhsId != null && egraph.find(rhsId) != egraph.find(eclassId)) {
                    matches.add(eclassId to rhsId)
                }
            }
        }
        
        return matches
    }
}

/**
 * Instantiate a pattern with a substitution, adding nodes to the e-graph.
 */
private fun instantiate(egraph: EGraph, pattern: Pattern, subst: Substitution): EClassId? {
    return when (pattern) {
        is Pattern.PVar -> subst[pattern.name]
        is Pattern.PConst -> egraph.add(ENode(ENodeOp.Const(pattern.value), emptyList()))
        is Pattern.POp -> {
            val childIds = pattern.children.mapNotNull { instantiate(egraph, it, subst) }
            if (childIds.size != pattern.children.size) return null
            egraph.add(ENode(pattern.op, childIds))
        }
    }
}


// ============================================================================
// SECTION 8: Equality Saturation
// ============================================================================

/**
 * Result of running equality saturation.
 */
data class SaturationResult(
    val iterations: Int,
    val eclassCount: Int,
    val nodeCount: Int,
    val saturated: Boolean,
    val reason: String
)

/**
 * Run equality saturation on the e-graph with the given rules.
 */
fun EGraph.saturate(
    rules: List<RewriteRule>,
    maxIterations: Int = 30,
    nodeLimit: Int = 10_000
): SaturationResult {
    var iteration = 0
    var saturated = false
    
    while (iteration < maxIterations && nodeCount < nodeLimit) {
        val allMatches = rules.flatMap { rule -> rule.apply(this) }
        
        if (allMatches.isEmpty()) {
            saturated = true
            break
        }
        
        // Apply all merges
        for ((id1, id2) in allMatches) {
            merge(id1, id2)
        }
        
        // Rebuild to restore invariants
        rebuild()
        
        iteration++
    }
    
    val reason = when {
        saturated -> "saturated"
        iteration >= maxIterations -> "max iterations reached"
        nodeCount >= nodeLimit -> "node limit reached"
        else -> "unknown"
    }
    
    return SaturationResult(
        iterations = iteration,
        eclassCount = eclassCount,
        nodeCount = nodeCount,
        saturated = saturated,
        reason = reason
    )
}

// ============================================================================
// SECTION 9: Extraction (Cost-based)
// ============================================================================

/**
 * Error that can occur during extraction.
 */
sealed interface ExtractError {
    data class EmptyEClass(val id: EClassId) : ExtractError
    data class CyclicReference(val path: List<EClassId>) : ExtractError
    data class UnsupportedNode(val op: ENodeOp, val message: String) : ExtractError
}

/**
 * Cost model for extraction - determines which equivalent expression is "best".
 */
interface CostModel {
    fun cost(op: ENodeOp, childCosts: List<Long>): Long
}

/**
 * Default cost model: minimize AST size (each node costs 1).
 */
object AstSizeCost : CostModel {
    override fun cost(op: ENodeOp, childCosts: List<Long>): Long =
        1L + childCosts.sum()
}

/**
 * Cost model that estimates CP-SAT auxiliary variables created during compilation.
 * Lower cost = fewer variables = better.
 * 
 * This model reflects how the ExprCompiler creates auxiliary variables:
 * - Constants and existing variables: 0 aux vars
 * - Arithmetic (linear): 0 aux vars (CP-SAT handles directly)
 * - Comparisons: 1 boolean aux var (reification)
 * - Logical operations: 1 boolean aux var per operation
 * - Conditionals (If): 2 aux vars (one for condition, one for result)
 */
object VariableCountCost : CostModel {
    // Base cost offset to ensure all costs are positive (required for correct extraction)
    // We use a small base cost as a tiebreaker to prefer simpler expressions
    private const val BASE = 100L
    
    override fun cost(op: ENodeOp, childCosts: List<Long>): Long {
        val childSum = childCosts.sum()
        return when (op) {
            // Leaf nodes - lowest cost (prefer constants over expressions)
            is ENodeOp.Const -> BASE  // Prefer constants as they're free
            is ENodeOp.ConstDouble -> BASE
            is ENodeOp.Var -> BASE + 1  // Slightly prefer constants over vars for tiebreaking
            is ENodeOp.ArrayLiteral -> BASE
            is ENodeOp.ArrayLiteralDouble -> BASE
            
            // Arithmetic operations - usually no aux vars for linear expressions
            // CP-SAT handles linear combinations directly
            // Add small increment for complexity (tiebreaker)
            ENodeOp.Sum -> childSum + 1  // sum(a, b, c) is linear, no aux vars
            ENodeOp.Sub -> childSum + 1  // a - b is linear
            ENodeOp.Neg -> childSum + 1  // -a is linear
            ENodeOp.Prod -> childSum + BASE  // Multiplication may need aux var if non-linear
            ENodeOp.Div -> childSum + BASE  // Division may need aux
            ENodeOp.Mod -> childSum + BASE  // Modulo may need aux
            
            // Comparison operations - each creates 1 boolean aux var for reification
            ENodeOp.Eq -> childSum + BASE
            ENodeOp.Neq -> childSum + BASE
            ENodeOp.Lt -> childSum + BASE
            ENodeOp.Leq -> childSum + BASE
            ENodeOp.Gt -> childSum + BASE
            ENodeOp.Geq -> childSum + BASE
            
            // Logical operations - each creates 1 boolean aux var
            ENodeOp.And -> childSum + BASE
            ENodeOp.Or -> childSum + BASE
            ENodeOp.Not -> childSum + BASE
            ENodeOp.Xor -> childSum + BASE * 2  // XOR often decomposes to multiple ops
            
            // Conditional - creates aux vars for reification
            // iif(c, t, f) creates at least 2 aux vars
            ENodeOp.If -> childSum + BASE * 2
            
            // Array/collection access - usually no aux vars, add small cost for complexity
            ENodeOp.At -> childSum + 1
            ENodeOp.At2D -> childSum + 1
            ENodeOp.ArrayOf -> childSum + 1
            ENodeOp.Array2D -> childSum + 1
            ENodeOp.Count -> childSum + 1
            ENodeOp.Contains -> childSum + BASE  // May need reification
            ENodeOp.Find -> childSum + 1
            ENodeOp.IndexOf -> childSum + 1
            
            // Set operations - no aux vars for direct operations
            ENodeOp.Intersection -> childSum + 1
            ENodeOp.Union -> childSum + 1
            
            // Sort - no aux vars
            is ENodeOp.Sort -> childSum + 1
            
            // Aggregations - usually handled directly by CP-SAT
            ENodeOp.Min -> childSum + 1
            ENodeOp.Max -> childSum + 1
            
            // Lambda aggregations - opaque, cost is just child cost
            is ENodeOp.SumOver -> childSum + 1
            is ENodeOp.ProdOver -> childSum + 1
            is ENodeOp.MinOver -> childSum + 1
            is ENodeOp.MaxOver -> childSum + 1
            is ENodeOp.AndOver -> childSum + 1
            is ENodeOp.OrOver -> childSum + 1
            
            // Math functions - may need aux vars
            ENodeOp.Abs -> childSum + BASE
            ENodeOp.Sqrt -> childSum + BASE
            ENodeOp.Pow -> childSum + BASE
            ENodeOp.Exp -> childSum + BASE
            ENodeOp.Log -> childSum + BASE
            ENodeOp.Ceil -> childSum + BASE
            ENodeOp.Floor -> childSum + BASE
            ENodeOp.Round -> childSum + BASE
            
            // Domain constraint - no aux vars
            is ENodeOp.InDomain -> childSum + 1
            
            // Special aggregations - highly efficient in CP-SAT
            ENodeOp.CountTrue -> childSum + 1  // CP-SAT has native AddBoolOr/And
            ENodeOp.IndicatorSum -> childSum + 1  // Efficiently compiled
        }
    }
}

/**
 * Extract the best expression from an e-class according to the cost model.
 */
fun EGraph.extract(
    rootId: EClassId,
    costModel: CostModel = AstSizeCost
): Either<ExtractError, ExprNode> {
    val costs = mutableMapOf<EClassId, Long>()
    val bestNode = mutableMapOf<EClassId, ENode>()
    val visiting = mutableSetOf<EClassId>()
    
    fun computeCost(eclassId: EClassId, path: List<EClassId>): Either<ExtractError, Long> {
        val canonical = find(eclassId)
        
        // Check for cycles
        if (canonical in visiting) {
            return ExtractError.CyclicReference(path + canonical).left()
        }
        
        // Return cached cost
        costs[canonical]?.let { return it.right() }
        
        val eclass = getClass(canonical).fold(
            ifEmpty = { return ExtractError.EmptyEClass(canonical).left() },
            ifSome = { it }
        )
        
        visiting.add(canonical)
        
        var minCost = Long.MAX_VALUE
        var minNode: ENode? = null
        
        for (node in eclass.nodes) {
            // Compute child costs
            var totalChildCost = 0L
            var valid = true
            
            for (childId in node.children) {
                when (val result = computeCost(childId, path + canonical)) {
                    is Either.Left -> {
                        valid = false
                        break
                    }
                    is Either.Right -> totalChildCost += result.value
                }
            }
            
            if (valid) {
                val nodeCost = costModel.cost(node.op, node.children.map { costs[find(it)] ?: 0L })
                if (nodeCost < minCost) {
                    minCost = nodeCost
                    minNode = node
                }
            }
        }
        
        visiting.remove(canonical)
        
        if (minNode == null) {
            return ExtractError.EmptyEClass(canonical).left()
        }
        
        costs[canonical] = minCost
        bestNode[canonical] = minNode
        return minCost.right()
    }
    
    // Compute costs for all reachable e-classes
    when (val result = computeCost(rootId, emptyList())) {
        is Either.Left -> return result
        is Either.Right -> { /* continue */ }
    }
    
    // Build the expression from best nodes
    return buildExpr(rootId, bestNode)
}

private fun EGraph.buildExpr(
    eclassId: EClassId,
    bestNode: Map<EClassId, ENode>
): Either<ExtractError, ExprNode> {
    val canonical = find(eclassId)
    val node = bestNode[canonical] ?: return ExtractError.EmptyEClass(canonical).left()
    
    // Recursively build child expressions
    val childExprs = mutableListOf<ExprNode>()
    for (childId in node.children) {
        when (val result = buildExpr(childId, bestNode)) {
            is Either.Left -> return result
            is Either.Right -> childExprs.add(result.value)
        }
    }
    
    return nodeToExpr(node, childExprs)
}

/**
 * Convert an ENode back to ExprNode.
 * 
 * IMPORTANT: Exhaustive when without else branch!
 */
private fun EGraph.nodeToExpr(node: ENode, children: List<ExprNode>): Either<ExtractError, ExprNode> {
    val expr: ExprNode = when (val op = node.op) {
        // ============ LITERALS ============
        is ENodeOp.Const -> ExprNode.Const(op.value)
        is ENodeOp.ConstDouble -> ExprNode.ConstDouble(op.value)
        is ENodeOp.Var -> ExprNode.Var(op.id, op.type)
        is ENodeOp.ArrayLiteral -> ExprNode.ArrayLiteral(op.values)
        is ENodeOp.ArrayLiteralDouble -> ExprNode.ArrayLiteralDouble(op.values)
        is ENodeOp.ArrayOf -> ExprNode.ArrayOf(children)
        is ENodeOp.Array2D -> ExprNode.Array2D(children)

        // ============ ARITHMETIC ============
        is ENodeOp.Sum -> ExprNode.Sum(children)
        is ENodeOp.Sub -> ExprNode.Sub(children[0], children[1])
        is ENodeOp.Prod -> ExprNode.Prod(children)
        is ENodeOp.Div -> ExprNode.Div(children[0], children[1])
        is ENodeOp.Mod -> ExprNode.Mod(children[0], children[1])
        is ENodeOp.Neg -> ExprNode.Neg(children[0])

        // ============ COMPARISON ============
        is ENodeOp.Eq -> ExprNode.Eq(children[0], children[1])
        is ENodeOp.Neq -> ExprNode.Neq(children[0], children[1])
        is ENodeOp.Lt -> ExprNode.Lt(children[0], children[1])
        is ENodeOp.Leq -> ExprNode.Leq(children[0], children[1])
        is ENodeOp.Gt -> ExprNode.Gt(children[0], children[1])
        is ENodeOp.Geq -> ExprNode.Geq(children[0], children[1])

        // ============ LOGICAL ============
        is ENodeOp.And -> ExprNode.And(children)
        is ENodeOp.Or -> ExprNode.Or(children)
        is ENodeOp.Not -> ExprNode.Not(children[0])
        is ENodeOp.Xor -> ExprNode.Xor(children)

        // ============ CONDITIONAL ============
        is ENodeOp.If -> ExprNode.If(children[0], children[1], children[2])

        // ============ COLLECTION OPERATIONS ============
        is ENodeOp.At -> ExprNode.At(children[0], children[1])
        is ENodeOp.At2D -> ExprNode.At2D(children[0], children[1], children[2])
        is ENodeOp.Count -> ExprNode.Count(children[0])
        is ENodeOp.Contains -> ExprNode.Contains(children[0], children[1])
        is ENodeOp.Find -> ExprNode.Find(children[0], children[1])
        is ENodeOp.IndexOf -> ExprNode.IndexOf(children[0], children[1])

        // ============ SET OPERATIONS ============
        is ENodeOp.Intersection -> ExprNode.Intersection(children)
        is ENodeOp.Union -> ExprNode.Union(children)

        // ============ LIST OPERATIONS ============
        is ENodeOp.Sort -> {
            // Cannot reconstruct lambda - return without key
            if (op.hasKey) {
                return ExtractError.UnsupportedNode(op, "Sort with key lambda cannot be extracted").left()
            }
            ExprNode.Sort(children[0], null)
        }

        // ============ AGGREGATIONS ============
        is ENodeOp.Min -> ExprNode.Min(children)
        is ENodeOp.Max -> ExprNode.Max(children)

        // ============ LAMBDA AGGREGATIONS (cannot extract - opaque) ============
        is ENodeOp.SumOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.SumOver(children[0], lambda)
        }
        is ENodeOp.ProdOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.ProdOver(children[0], lambda)
        }
        is ENodeOp.MinOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.MinOver(children[0], lambda)
        }
        is ENodeOp.MaxOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.MaxOver(children[0], lambda)
        }
        is ENodeOp.AndOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.AndOver(children[0], lambda)
        }
        is ENodeOp.OrOver -> {
            val lambda = getLambda(op.lambdaId).fold(
                ifEmpty = { return ExtractError.UnsupportedNode(op, "Lambda not found").left() },
                ifSome = { it }
            )
            ExprNode.OrOver(children[0], lambda)
        }

        // ============ MATH FUNCTIONS ============
        is ENodeOp.Abs -> ExprNode.Abs(children[0])
        is ENodeOp.Sqrt -> ExprNode.Sqrt(children[0])
        is ENodeOp.Pow -> ExprNode.Pow(children[0], children[1])
        is ENodeOp.Exp -> ExprNode.Exp(children[0])
        is ENodeOp.Log -> ExprNode.Log(children[0])
        is ENodeOp.Ceil -> ExprNode.Ceil(children[0])
        is ENodeOp.Floor -> ExprNode.Floor(children[0])
        is ENodeOp.Round -> ExprNode.Round(children[0])

        // ============ DOMAIN CONSTRAINTS ============
        is ENodeOp.InDomain -> ExprNode.InDomain(children[0], op.domain)

        // ============ OPTIMIZED AGGREGATIONS ============
        is ENodeOp.CountTrue -> ExprNode.CountTrue(children)
        is ENodeOp.IndicatorSum -> {
            // Unflatten: [cond1, val1, cond2, val2, ...] -> [(cond1, val1), ...]
            val terms = children.chunked(2).map { (cond, value) -> cond to value }
            ExprNode.IndicatorSum(terms)
        }
    }
    
    return expr.right()
}

// ============================================================================
// SECTION 10: Rule DSL Builder
// ============================================================================

/**
 * DSL builder for creating rewrite rules.
 */
class RuleBuilder {
    private val variables = mutableMapOf<String, Pattern.PVar>()
    
    /** Get or create a pattern variable */
    val x: Pattern.PVar get() = getVar("x")
    val y: Pattern.PVar get() = getVar("y")
    val z: Pattern.PVar get() = getVar("z")
    val a: Pattern.PVar get() = getVar("a")
    val b: Pattern.PVar get() = getVar("b")
    val c: Pattern.PVar get() = getVar("c")
    
    private fun getVar(name: String): Pattern.PVar =
        variables.getOrPut(name) { Pattern.PVar(name) }
    
    /** Create a constant pattern */
    fun const(value: Long): Pattern = Pattern.PConst(value)
    
    /** Addition pattern */
    operator fun Pattern.plus(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.Sum, listOf(this, other))
    
    operator fun Pattern.plus(value: Long): Pattern =
        Pattern.POp(ENodeOp.Sum, listOf(this, Pattern.PConst(value)))
    
    /** Multiplication pattern */
    operator fun Pattern.times(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.Prod, listOf(this, other))
    
    operator fun Pattern.times(value: Long): Pattern =
        Pattern.POp(ENodeOp.Prod, listOf(this, Pattern.PConst(value)))
    
    /** Subtraction pattern */
    operator fun Pattern.minus(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.Sub, listOf(this, other))
    
    /** Negation pattern */
    operator fun Pattern.unaryMinus(): Pattern =
        Pattern.POp(ENodeOp.Neg, listOf(this))
    
    /** Logical AND pattern */
    infix fun Pattern.and(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.And, listOf(this, other))
    
    /** Logical OR pattern */
    infix fun Pattern.or(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.Or, listOf(this, other))
    
    /** Logical NOT pattern */
    fun not(p: Pattern): Pattern =
        Pattern.POp(ENodeOp.Not, listOf(p))
    
    /** Equality pattern */
    infix fun Pattern.eq(other: Pattern): Pattern =
        Pattern.POp(ENodeOp.Eq, listOf(this, other))
    
    infix fun Pattern.eq(value: Long): Pattern =
        Pattern.POp(ENodeOp.Eq, listOf(this, Pattern.PConst(value)))
    
    /** If-then-else pattern */
    fun iif(cond: Pattern, ifTrue: Pattern, ifFalse: Pattern): Pattern =
        Pattern.POp(ENodeOp.If, listOf(cond, ifTrue, ifFalse))
    
    /** Create a rule from LHS to RHS */
    infix fun Pattern.to(rhs: Pattern): Pair<Pattern, Pattern> = Pair(this, rhs)
}

/**
 * Create a rewrite rule using the DSL.
 */
fun rule(name: String, build: RuleBuilder.() -> Pair<Pattern, Pattern>): RewriteRule {
    val builder = RuleBuilder()
    val (lhs, rhs) = builder.build()
    return RewriteRule(name, lhs, rhs)
}

// ============================================================================
// SECTION 11: Default Rewrite Rules for CP-SAT
// ============================================================================

/**
 * Default set of rewrite rules for optimizing CP-SAT expressions.
 */
val defaultRules: List<RewriteRule> = listOf(
    // Arithmetic identities
    rule("add-zero-right") { x + 0 to x },
    rule("add-zero-left") { const(0) + x to x },
    rule("mul-one-right") { x * 1 to x },
    rule("mul-one-left") { const(1) * x to x },
    rule("mul-zero-right") { x * 0 to const(0) },
    rule("mul-zero-left") { const(0) * x to const(0) },
    rule("sub-zero") { x - const(0) to x },
    rule("sub-self") { x - x to const(0) },
    rule("neg-neg") { -(-x) to x },
    
    // Note: Commutativity rules can cause infinite loops in naive saturation.
    // They're included but should be used carefully or with smarter scheduling.
    // rule("add-comm") { x + y to y + x },
    // rule("mul-comm") { x * y to y * x },
)

/**
 * CP-SAT specific optimization rules.
 * These rules target patterns that create unnecessary boolean/auxiliary variables.
 */
val cpSatOptimizationRules: List<RewriteRule> = defaultRules + listOf(
    // Boolean simplifications - reduce intermediate boolean variables
    rule("not-not") { not(not(x)) to x },
    
    // and(x, true) represented as and(x, 1) in our model
    // We can't directly match "true" but we can simplify double negations
    // and identity operations
    
    // If-then-else simplifications
    // iif(cond, x, x) -> x (same branches)
    rule("if-same") { iif(x, y, y) to y },
    
    // iif(true, x, y) -> x - but we'd need to match const(1) as condition
    // This is handled by the pattern optimizer already
    
    // Equality simplifications
    // x == x -> 1 (always true)
    rule("eq-self") { (x eq x) to const(1) },
)

// ============================================================================
// SECTION 12: Integration helper
// ============================================================================

/**
 * Optimize an expression using e-graph equality saturation.
 */
fun ExprNode.optimizeWithEGraph(
    rules: List<RewriteRule> = defaultRules,
    maxIterations: Int = 30,
    nodeLimit: Int = 10_000,
    costModel: CostModel = VariableCountCost  // Use variable count cost by default
): Either<ExtractError, ExprNode> {
    val (egraph, rootId) = EGraph.fromExpr(this)
    egraph.saturate(rules, maxIterations, nodeLimit)
    return egraph.extract(rootId, costModel)
}

