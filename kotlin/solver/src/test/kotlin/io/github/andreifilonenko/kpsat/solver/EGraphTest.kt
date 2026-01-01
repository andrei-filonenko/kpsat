@file:Suppress("LongMethod")

package io.github.andreifilonenko.kpsat.solver

import arrow.core.Either
import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.VarType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EGraphTest {

    // =========================================================================
    // Exhaustiveness Tests (Reflection-based safety net)
    // =========================================================================

    @Nested
    inner class ExhaustivenessTests {

        /**
         * All ExprNode subclasses that are handled in EGraph.addExpr().
         * If you add a new ExprNode variant, this test will fail until you add it here
         * AND handle it in the addExpr() function.
         */
        private val handledExprNodeClasses: Set<KClass<out ExprNode>> = setOf(
            // Literals
            ExprNode.Const::class,
            ExprNode.ConstDouble::class,
            ExprNode.Var::class,
            ExprNode.ArrayLiteral::class,
            ExprNode.ArrayLiteralDouble::class,
            ExprNode.ArrayOf::class,
            ExprNode.Array2D::class,
            // Arithmetic
            ExprNode.Sum::class,
            ExprNode.Sub::class,
            ExprNode.Prod::class,
            ExprNode.Div::class,
            ExprNode.Mod::class,
            ExprNode.Neg::class,
            // Comparison
            ExprNode.Eq::class,
            ExprNode.Neq::class,
            ExprNode.Lt::class,
            ExprNode.Leq::class,
            ExprNode.Gt::class,
            ExprNode.Geq::class,
            // Logical
            ExprNode.And::class,
            ExprNode.Or::class,
            ExprNode.Not::class,
            ExprNode.Xor::class,
            // Conditional
            ExprNode.If::class,
            // Collection
            ExprNode.At::class,
            ExprNode.At2D::class,
            ExprNode.Count::class,
            ExprNode.Contains::class,
            ExprNode.Find::class,
            ExprNode.IndexOf::class,
            // Set operations
            ExprNode.Intersection::class,
            ExprNode.Union::class,
            // List operations
            ExprNode.Sort::class,
            // Aggregations
            ExprNode.Min::class,
            ExprNode.Max::class,
            // Lambda aggregations
            ExprNode.SumOver::class,
            ExprNode.ProdOver::class,
            ExprNode.MinOver::class,
            ExprNode.MaxOver::class,
            ExprNode.AndOver::class,
            ExprNode.OrOver::class,
            // Math functions
            ExprNode.Abs::class,
            ExprNode.Sqrt::class,
            ExprNode.Pow::class,
            ExprNode.Exp::class,
            ExprNode.Log::class,
            ExprNode.Ceil::class,
            ExprNode.Floor::class,
            ExprNode.Round::class,
            // Domain constraints
            ExprNode.InDomain::class,
            // Optimized aggregations
            ExprNode.CountTrue::class,
            ExprNode.IndicatorSum::class,
        )

        /**
         * All ENodeOp subclasses that are handled in extraction.
         */
        private val handledENodeOpClasses: Set<KClass<out ENodeOp>> = setOf(
            // Literals
            ENodeOp.Const::class,
            ENodeOp.ConstDouble::class,
            ENodeOp.Var::class,
            ENodeOp.ArrayLiteral::class,
            ENodeOp.ArrayLiteralDouble::class,
            ENodeOp.ArrayOf::class,
            ENodeOp.Array2D::class,
            // Arithmetic
            ENodeOp.Sum::class,
            ENodeOp.Sub::class,
            ENodeOp.Prod::class,
            ENodeOp.Div::class,
            ENodeOp.Mod::class,
            ENodeOp.Neg::class,
            // Comparison
            ENodeOp.Eq::class,
            ENodeOp.Neq::class,
            ENodeOp.Lt::class,
            ENodeOp.Leq::class,
            ENodeOp.Gt::class,
            ENodeOp.Geq::class,
            // Logical
            ENodeOp.And::class,
            ENodeOp.Or::class,
            ENodeOp.Not::class,
            ENodeOp.Xor::class,
            // Conditional
            ENodeOp.If::class,
            // Collection
            ENodeOp.At::class,
            ENodeOp.At2D::class,
            ENodeOp.Count::class,
            ENodeOp.Contains::class,
            ENodeOp.Find::class,
            ENodeOp.IndexOf::class,
            // Set operations
            ENodeOp.Intersection::class,
            ENodeOp.Union::class,
            // List operations
            ENodeOp.Sort::class,
            // Aggregations
            ENodeOp.Min::class,
            ENodeOp.Max::class,
            // Lambda aggregations
            ENodeOp.SumOver::class,
            ENodeOp.ProdOver::class,
            ENodeOp.MinOver::class,
            ENodeOp.MaxOver::class,
            ENodeOp.AndOver::class,
            ENodeOp.OrOver::class,
            // Math functions
            ENodeOp.Abs::class,
            ENodeOp.Sqrt::class,
            ENodeOp.Pow::class,
            ENodeOp.Exp::class,
            ENodeOp.Log::class,
            ENodeOp.Ceil::class,
            ENodeOp.Floor::class,
            ENodeOp.Round::class,
            // Domain constraints
            ENodeOp.InDomain::class,
            // Optimized aggregations
            ENodeOp.CountTrue::class,
            ENodeOp.IndicatorSum::class,
        )

        @Test
        fun `all ExprNode subclasses must be handled in EGraph conversion`() {
            val allSubclasses = ExprNode::class.sealedSubclasses.toSet()

            val unhandled = allSubclasses - handledExprNodeClasses
            assertTrue(
                unhandled.isEmpty(),
                "Unhandled ExprNode subclasses in EGraph.addExpr(): ${unhandled.map { it.simpleName }}\n" +
                    "Add these to handledExprNodeClasses set AND implement in addExpr()"
            )

            val extra = handledExprNodeClasses - allSubclasses
            assertTrue(
                extra.isEmpty(),
                "Extra classes in handledExprNodeClasses that don't exist in ExprNode: ${extra.map { it.simpleName }}"
            )
        }

        @Test
        fun `all ENodeOp subclasses must be handled in extraction`() {
            val allSubclasses = ENodeOp::class.sealedSubclasses.toSet()

            val unhandled = allSubclasses - handledENodeOpClasses
            assertTrue(
                unhandled.isEmpty(),
                "Unhandled ENodeOp subclasses in nodeToExpr(): ${unhandled.map { it.simpleName }}\n" +
                    "Add these to handledENodeOpClasses set AND implement in nodeToExpr()"
            )

            val extra = handledENodeOpClasses - allSubclasses
            assertTrue(
                extra.isEmpty(),
                "Extra classes in handledENodeOpClasses that don't exist in ENodeOp: ${extra.map { it.simpleName }}"
            )
        }

        @Test
        fun `ExprNode and ENodeOp should have same number of variants`() {
            val exprNodeCount = ExprNode::class.sealedSubclasses.size
            val eNodeOpCount = ENodeOp::class.sealedSubclasses.size

            assertEquals(
                exprNodeCount, 
                eNodeOpCount,
                "ExprNode has $exprNodeCount variants but ENodeOp has $eNodeOpCount variants. " +
                    "They should match 1:1."
            )
        }
    }

    // =========================================================================
    // Core EGraph Operations Tests
    // =========================================================================

    @Nested
    inner class CoreOperationsTests {

        @Test
        fun `empty egraph has zero nodes and classes`() {
            val egraph = EGraph.empty()
            assertEquals(0, egraph.eclassCount)
            assertEquals(0, egraph.nodeCount)
        }

        @Test
        fun `adding constant creates one eclass`() {
            val egraph = EGraph.empty()
            val id = egraph.add(ENode(ENodeOp.Const(42), emptyList()))

            assertEquals(1, egraph.eclassCount)
            assertEquals(1, egraph.nodeCount)
            assertEquals(id, egraph.find(id))
        }

        @Test
        fun `adding same constant twice returns same eclass (hash-consing)`() {
            val egraph = EGraph.empty()
            val id1 = egraph.add(ENode(ENodeOp.Const(42), emptyList()))
            val id2 = egraph.add(ENode(ENodeOp.Const(42), emptyList()))

            assertEquals(id1, id2)
            assertEquals(1, egraph.eclassCount)
        }

        @Test
        fun `adding different constants creates different eclasses`() {
            val egraph = EGraph.empty()
            val id1 = egraph.add(ENode(ENodeOp.Const(1), emptyList()))
            val id2 = egraph.add(ENode(ENodeOp.Const(2), emptyList()))

            assertNotEquals(id1, id2)
            assertEquals(2, egraph.eclassCount)
        }

        @Test
        fun `merge unifies two eclasses`() {
            val egraph = EGraph.empty()
            val id1 = egraph.add(ENode(ENodeOp.Const(1), emptyList()))
            val id2 = egraph.add(ENode(ENodeOp.Const(2), emptyList()))

            val merged = egraph.merge(id1, id2)
            assertEquals(egraph.find(id1), egraph.find(id2))
            assertEquals(merged, egraph.find(id1))
        }

        @Test
        fun `find is idempotent`() {
            val egraph = EGraph.empty()
            val id = egraph.add(ENode(ENodeOp.Const(42), emptyList()))

            assertEquals(egraph.find(id), egraph.find(egraph.find(id)))
        }
    }

    // =========================================================================
    // ExprNode Conversion Tests
    // =========================================================================

    @Nested
    inner class ExprNodeConversionTests {

        @Test
        fun `addExpr handles simple constant`() {
            val expr = ExprNode.Const(42)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            assertEquals(1, egraph.eclassCount)
            assertTrue(egraph.getClass(rootId).isSome())
        }

        @Test
        fun `addExpr handles variable`() {
            val expr = ExprNode.Var(0, VarType.INT)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            assertEquals(1, egraph.eclassCount)
            assertTrue(egraph.getClass(rootId).isSome())
        }

        @Test
        fun `addExpr handles sum expression`() {
            val expr = ExprNode.Sum(listOf(
                ExprNode.Const(1),
                ExprNode.Const(2),
                ExprNode.Const(3)
            ))
            val (egraph, rootId) = EGraph.fromExpr(expr)

            // 3 constants + 1 sum = 4 eclasses
            assertEquals(4, egraph.eclassCount)
            assertTrue(egraph.getClass(rootId).isSome())
        }

        @Test
        fun `addExpr handles nested expression`() {
            // (x + 1) * 2
            val expr = ExprNode.Prod(listOf(
                ExprNode.Sum(listOf(
                    ExprNode.Var(0, VarType.INT),
                    ExprNode.Const(1)
                )),
                ExprNode.Const(2)
            ))
            val (egraph, _) = EGraph.fromExpr(expr)

            // var, 1, sum, 2, prod = 5 eclasses
            assertEquals(5, egraph.eclassCount)
        }

        @Test
        fun `addExpr shares common subexpressions`() {
            // x + x (same variable should be shared)
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Sum(listOf(x, x))
            val (egraph, _) = EGraph.fromExpr(expr)

            // x (shared), sum = 2 eclasses
            assertEquals(2, egraph.eclassCount)
        }
    }

    // =========================================================================
    // Extraction Tests
    // =========================================================================

    @Nested
    inner class ExtractionTests {

        @Test
        fun `extract returns original constant`() {
            val expr = ExprNode.Const(42)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `extract returns original variable`() {
            val expr = ExprNode.Var(5, VarType.BOOL)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `extract returns original sum`() {
            val expr = ExprNode.Sum(listOf(
                ExprNode.Const(1),
                ExprNode.Const(2)
            ))
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `extract returns original nested expression`() {
            val expr = ExprNode.Prod(listOf(
                ExprNode.Sum(listOf(
                    ExprNode.Var(0, VarType.INT),
                    ExprNode.Const(1)
                )),
                ExprNode.Const(2)
            ))
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `round-trip preserves If expression`() {
            val expr = ExprNode.If(
                ExprNode.Var(0, VarType.BOOL),
                ExprNode.Const(1),
                ExprNode.Const(0)
            )
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `round-trip preserves comparison operations`() {
            val expr = ExprNode.And(listOf(
                ExprNode.Lt(ExprNode.Var(0, VarType.INT), ExprNode.Const(10)),
                ExprNode.Geq(ExprNode.Var(1, VarType.INT), ExprNode.Const(0))
            ))
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val result = egraph.extract(rootId)
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }
    }

    // =========================================================================
    // Pattern Matching Tests
    // =========================================================================

    @Nested
    inner class PatternMatchingTests {

        @Test
        fun `PVar matches any eclass`() {
            val expr = ExprNode.Const(42)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val pattern = Pattern.PVar("x")
            val matches = pattern.match(egraph, rootId)

            assertEquals(1, matches.size)
            assertEquals(rootId, matches[0]["x"])
        }

        @Test
        fun `PConst matches constant with same value`() {
            val expr = ExprNode.Const(42)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val pattern = Pattern.PConst(42)
            val matches = pattern.match(egraph, rootId)

            assertEquals(1, matches.size)
        }

        @Test
        fun `PConst does not match different constant`() {
            val expr = ExprNode.Const(42)
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val pattern = Pattern.PConst(99)
            val matches = pattern.match(egraph, rootId)

            assertTrue(matches.isEmpty())
        }

        @Test
        fun `POp matches operation with correct children`() {
            val expr = ExprNode.Sum(listOf(
                ExprNode.Const(1),
                ExprNode.Const(2)
            ))
            val (egraph, rootId) = EGraph.fromExpr(expr)

            val pattern = Pattern.POp(
                ENodeOp.Sum,
                listOf(Pattern.PVar("a"), Pattern.PVar("b"))
            )
            val matches = pattern.match(egraph, rootId)

            assertEquals(1, matches.size)
            assertTrue(matches[0]["a"] != null)
            assertTrue(matches[0]["b"] != null)
        }
    }

    // =========================================================================
    // Rewrite Rule Tests
    // =========================================================================

    @Nested
    inner class RewriteRuleTests {

        @Test
        fun `rule DSL creates valid rule`() {
            val addZero = rule("add-zero") { x + 0 to x }

            assertEquals("add-zero", addZero.name)
            assertIs<Pattern.POp>(addZero.lhs)
            assertIs<Pattern.PVar>(addZero.rhs)
        }

        @Test
        fun `mul-zero rule simplifies expression`() {
            // x * 0 should become 0
            val expr = ExprNode.Prod(listOf(
                ExprNode.Var(0, VarType.INT),
                ExprNode.Const(0)
            ))
            
            val result = expr.optimizeWithEGraph(
                rules = listOf(rule("mul-zero") { x * 0 to const(0) }),
                maxIterations = 10
            )

            assertIs<Either.Right<ExprNode>>(result)
            // After optimization, the best expression should be const(0)
            assertEquals(ExprNode.Const(0), result.value)
        }

        @Test
        fun `add-zero rule simplifies expression`() {
            // x + 0 should become x
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Sum(listOf(x, ExprNode.Const(0)))
            
            val result = expr.optimizeWithEGraph(
                rules = listOf(rule("add-zero") { this.x + 0 to this.x }),
                maxIterations = 10
            )

            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }
    }

    // =========================================================================
    // Saturation Tests
    // =========================================================================

    @Nested
    inner class SaturationTests {

        @Test
        fun `saturation terminates when no rules match`() {
            val expr = ExprNode.Const(42)
            val (egraph, _) = EGraph.fromExpr(expr)

            val result = egraph.saturate(emptyList())

            assertTrue(result.saturated)
            assertEquals(0, result.iterations)
        }

        @Test
        fun `saturation respects max iterations`() {
            val expr = ExprNode.Const(42)
            val (egraph, _) = EGraph.fromExpr(expr)

            // Use a rule that would always match if there was something to match
            val result = egraph.saturate(
                rules = defaultRules,
                maxIterations = 5
            )

            assertTrue(result.iterations <= 5)
        }

        @Test
        fun `saturation respects node limit`() {
            val expr = ExprNode.Sum(listOf(
                ExprNode.Var(0, VarType.INT),
                ExprNode.Const(1)
            ))
            val (egraph, _) = EGraph.fromExpr(expr)

            val result = egraph.saturate(
                rules = defaultRules,
                nodeLimit = 100
            )

            assertTrue(egraph.nodeCount <= 100)
        }
    }

    // =========================================================================
    // Union-Find Tests
    // =========================================================================

    @Nested
    inner class UnionFindTests {

        @Test
        fun `find returns same id for single element`() {
            val uf = UnionFind()
            val id = EClassId(0)
            uf.makeSet(id)

            assertEquals(id, uf.find(id))
        }

        @Test
        fun `union merges two sets`() {
            val uf = UnionFind()
            val id1 = EClassId(0)
            val id2 = EClassId(1)
            uf.makeSet(id1)
            uf.makeSet(id2)

            uf.union(id1, id2)

            assertEquals(uf.find(id1), uf.find(id2))
        }

        @Test
        fun `path compression works`() {
            val uf = UnionFind()
            val ids = (0..9).map { EClassId(it) }
            ids.forEach { uf.makeSet(it) }

            // Create chain: 0 <- 1 <- 2 <- ... <- 9
            for (i in 1..9) {
                uf.union(ids[0], ids[i])
            }

            // All should find the same root
            val root = uf.find(ids[0])
            ids.forEach { assertEquals(root, uf.find(it)) }
        }
    }

    // =========================================================================
    // CP-SAT Optimization Examples Tests
    // =========================================================================

    @Nested
    inner class CpSatOptimizationExamplesTests {

        @Test
        fun `x plus 0 simplifies to x`() {
            // x + 0 should become just x
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Sum(listOf(x, ExprNode.Const(0)))
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }

        @Test
        fun `x times 0 simplifies to 0`() {
            // x * 0 should become 0
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Prod(listOf(x, ExprNode.Const(0)))
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(ExprNode.Const(0), result.value)
        }

        @Test
        fun `x times 1 simplifies to x`() {
            // x * 1 should become x
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Prod(listOf(x, ExprNode.Const(1)))
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }

        @Test
        fun `x minus x simplifies to 0`() {
            // x - x should become 0
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Sub(x, x)
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(ExprNode.Const(0), result.value)
        }

        @Test
        fun `double negation simplifies to original`() {
            // -(-x) should become x
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Neg(ExprNode.Neg(x))
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }

        @Test
        fun `if-then-else with same branches simplifies`() {
            // iif(cond, x, x) should become x
            val cond = ExprNode.Var(0, VarType.BOOL)
            val x = ExprNode.Var(1, VarType.INT)
            val expr = ExprNode.If(cond, x, x)
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }

        @Test
        fun `x equals x simplifies to 1 (true)`() {
            // x == x should become 1 (always true)
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Eq(x, x)
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(ExprNode.Const(1), result.value)
        }

        @Test
        fun `complex nested expression simplification`() {
            // (x + 0) * 1 - 0 should become x
            val x = ExprNode.Var(0, VarType.INT)
            val expr = ExprNode.Sub(
                ExprNode.Prod(listOf(
                    ExprNode.Sum(listOf(x, ExprNode.Const(0))),
                    ExprNode.Const(1)
                )),
                ExprNode.Const(0)
            )
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(x, result.value)
        }

        @Test
        fun `optimization preserves semantics for non-simplifiable expressions`() {
            // x + y should remain x + y (no simplification possible)
            val x = ExprNode.Var(0, VarType.INT)
            val y = ExprNode.Var(1, VarType.INT)
            val expr = ExprNode.Sum(listOf(x, y))
            
            val result = expr.optimizeWithEGraph(cpSatOptimizationRules)
            
            assertIs<Either.Right<ExprNode>>(result)
            assertEquals(expr, result.value)
        }

        @Test
        fun `cpSatOptimizationRules includes default rules plus boolean rules`() {
            // Verify the rule set is properly composed
            assertTrue(cpSatOptimizationRules.size >= defaultRules.size)
            
            // Should have the additional boolean rules
            val ruleNames = cpSatOptimizationRules.map { it.name }
            assertTrue(ruleNames.contains("not-not"))
            assertTrue(ruleNames.contains("if-same"))
            assertTrue(ruleNames.contains("eq-self"))
        }
    }
}

