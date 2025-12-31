# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-01-01

### Added

#### DSL Expression System
- Composable expression AST (`Expr`, `ExprNode`) for building constraint models
- Arithmetic operators: `+`, `-`, `*`, `/`, `%`, unary `-`
- Comparison operators: `eq`, `neq`, `lt`, `leq`, `gt`, `geq`
- Logical operators: `and`, `or`, `not`, `xor`, `implies`, `iff`
- Conditional expressions: `iif(cond, ifTrue, ifFalse)`, `then`/`otherwise` syntax
- Domain constraints: `inDomain(longArray)`
- Math functions: `abs`, `sqrt`, `pow`, `exp`, `log`, `ceil`, `floor`, `round`

#### Functional Aggregations
- `sum`, `prod`, `min`, `max` over collections and ranges
- `forAll`, `exists` quantifiers
- `allDifferent` constraint
- `count`, `exactly`, `atLeast`, `atMost` cardinality constraints
- Collection operations: `containsElem`, `isIn`, array indexing

#### CP-SAT Backend Integration
- Full integration with Google OR-Tools CP-SAT solver
- Expression compiler translating DSL AST to CP-SAT constraints
- Expression optimizer for constant folding and simplification

#### Constraint Solver Builder
- Fluent builder API for model construction
- Hard constraints (must be satisfied)
- Soft constraints with weights and priorities
- Lexicographic multi-objective optimization (`minimize`, `maximize`)
- Penalty normalization across different constraint scales

#### Runtime Constraint Configuration
- Dynamic weight adjustment: `setWeight(name, weight)`
- Priority modification: `setPriority(name, priority)`
- Enable/disable constraints: `enable(name)`, `disable(name)`
- Constraint introspection: `getHardConstraints()`, `getSoftConstraints()`, `getObjectives()`

#### Direct Evaluation
- `DirectEvaluator` for testing constraint logic without invoking the solver
- Supports all expression types for unit testing

#### Documentation
- Comprehensive README with philosophy, examples, and API reference
- Interactive Jupyter notebook with N-Queens and RCPSP examples
- Generated API documentation (Dokka)

### Technical Details
- Kotlin 2.0.21 with context receivers
- JDK 21 target
- OR-Tools 9.11.4210
- Arrow 1.2.4 for functional error handling

