# SPEC.md (the specification)

Role: the ONE planning artifact. Written before any code, from the instructor's brief; every later stage
implements THIS and is checked against it. It answers the questions the brief usually leaves open: what will
the student actually compute, which types exist and which of them the student writes, and how the work is
graded. A spec whose graded work is copying literals produces a hollow exercise no matter how well the later
stages execute. Skipped entirely when the instructor already provided a real problem statement — that
statement is the spec. Updated whenever a later stage proves it wrong, so it always describes the final
exercise truthfully.

## Choose an archetype first

Name the exercise's shape before writing rules. The archetype is a lens for the brief, never an alternative
to it: every explicit requirement in the brief (a named design pattern, student-created types, a stated
domain) binds the spec — pick the archetype that fits those requirements, not the one easiest to specify.
Pick the archetype whose non-hollow contract your rules will honor, or declare "none of these" with a
justification:

- **calculator-with-rules** — outputs computed from inputs through 2+ interacting rules (rates, thresholds,
  rounding). Non-hollow when: changing any input changes the output through a rule, not a lookup.
- **state-machine** — behavior depends on accumulated state; operations move it. Non-hollow when: the same
  call gives different results depending on history.
- **pattern-with-computed-variants** — interchangeable implementations behind one abstraction. Non-hollow
  when: each variant COMPUTES differently on the same input; variants that differ only by a returned constant
  are forbidden.
- **collection-aggregation** — derive totals/groupings/extremes from a collection with validity rules.
  Non-hollow when: results depend on multiple elements interacting (ordering, filtering, ties).
- **invariant-preservation** — operations must keep a stated property true (balance, capacity, ordering).
  Non-hollow when: at least one operation could plausibly violate the invariant and must actively protect it.
- **parser/formatter-with-grammar** — text transformed by composable rules. Non-hollow when: outputs derive
  from input structure, not from a table of canned answers.

## Numbered rules

State every graded behaviour as a numbered rule (R1, R2, ...) precise enough to test: inputs, computation,
boundaries, error behaviour. Each rule must be one a plausible wrong implementation would get wrong. Rules
carry the computation; do not hide it in the examples.

## Worked-examples table

A markdown table under `## Worked Examples` with at least these columns: the rule(s) exercised, the input,
the expected result. At least two rows per central rule with DIFFERENT expected results, so the table proves
branching instead of asserting a constant. Verify every row's arithmetic by actually computing it in the
sandbox (a throwaway script under /tmp) before writing it down — a wrong number here poisons every later
stage. The solution stage will replay these rows against the real implementation.

## Design table

A markdown table under `## Design` with one row per type: its name, its role, and a `Template status` that is
EXACTLY one of `given`, `stubbed`, `student-creates` — the gates enforce these tokens literally. `given`
ships complete; `stubbed` ships with signatures, Javadoc, and TODO bodies; `student-creates` is ABSENT from
the template (students design the file themselves; it is graded through seeded structural checks and
reflection-based tests, and the template gate rejects a template that still contains it). A brief that asks
students to design or create a type normally demands `student-creates`, but Java must still have enough
declarations for a provided scaffold to compile. When a brief combines a provided Strategy context with a
student-designed interface and concrete strategies, the compile-safe allocation is mandatory: the interface is
`student-creates`; concrete strategies are `student-creates`; the context is `stubbed`. Keep the context class as
the provided scaffold, but omit fields and methods whose signatures require the absent interface. Put one
class-body TODO breadcrumb in that context telling students to add the field, setter, and delegation API after
they create the interface. Tests reach the student-defined interface and the context wiring reflectively. An empty
interface declaration still pre-creates the type, so it is not faithful when the brief explicitly assigns creating
that interface to students.
For every piece of MUTABLE STATE, say below the table which type owns it and whether it survives object
replacement (a swap, reset, or re-registration): tests may only demand what this ownership makes possible.
Pin the public API here too — signatures only; later stages copy them, they do not renegotiate silently.

The design table also carries the requested difficulty and learning objective. Judge difficulty by the work
left to the student, not by the number of files or formulas. When the brief teaches a collaboration or design
pattern, leave students meaningful work in that collaboration (defining an abstraction when appropriate,
wiring it, or making the context delegate); do not hand them the complete design and call formula transcription
an intermediate pattern exercise. Conversely, provide routine holders, accessors, and build plumbing unless
those are explicitly part of the objective.

Reconcile that ownership with compilation before approving the design. Every testing-strategy seam described
as student work must map to a `stubbed` or `student-creates` row. A given or stubbed type cannot expose an
omitted `student-creates` type in a template signature: the template would not compile. Make the dependent type
student-created too, or omit only the student-owned dependent members and replace them with stable class-body
TODO breadcrumbs. Never make the same API accept the real interface in the solution and `Object` in the template;
shared solution/template signatures stay identical.

Before accepting the rules, perform a scope subtraction pass: remove validation, exception, state, purity,
immutability, thread-safety, and architecture obligations that the brief did not request and that are not strictly
necessary to define the chosen strategies. Choosing the theme and the strategies' contrasting computations is
necessary when the brief leaves them open; adding unrelated defensive policy is not.

## Testing strategy

A table under `## Testing Strategy` with one row per SEAM — an independently actionable unit of student work.
Give the first column stable IDs `S1`, `S2`, ...; the template TODOs, grading plan, and statement tasks carry
these IDs through the rest of generation. Never one seam per test; never one seam for the whole exercise unless it genuinely is one. Each row lists the
behaviour partitions its tests need, a weight tier (core rules weigh more than edge polish; weights 1–3, and
the test stage writes the machine-readable plan), and LAST the hidden-variant decision, written as `yes` or
`no` — that cell is read mechanically, so prose there reads as "no". A hidden variant (visibility
AFTER_DUE_DATE) repeats a partition with fresh witness values, because students overfit to visible tests; it
grades silently and is never bound to a task in the statement.

## Diagram

Under `## Diagram`, an honest yes/no with a reason grounded in the design: yes when several collaborating
types or student-created types make an architecture overview genuinely helpful; no when a single class
carries the work. The reason must follow from the design itself — "no, because the statement has no diagram"
is circular and invalid; the statement follows this decision, not the other way around.

## Off-limits at spec time

No `[task]` bindings, no test names, no PlantUML — those belong to later stages. No grading/verifier
internals.

## What may vary

Section granularity follows the exercise; a compact exercise may have three rules, a rich one ten. The
archetype menu is a lens, not a cage — "none of these" with a reason is a legitimate choice. Extra table
columns (state before/after, notes) are free. Do not inflate a one-class exercise into a pattern, and do not
collapse a pattern exercise into one class.

## Exemplar (FORM only — never copy its topic, API, or design)

```markdown
# Cafe Loyalty Rewards

Archetype: pattern-with-computed-variants — reward strategies compute points differently on the same
purchase; the account delegates and owns the balance.

## Rules

- R1: A purchase earns 1 point per whole dollar spent; fractions never round up.
- R2: Purchases of $50.00 or more earn a 10-point bonus on top of R1.
- R3: Redeeming costs 50 points and reduces the total by $5.00; redeeming more times than the balance
  affords throws an IllegalStateException and leaves the balance unchanged.

## Worked Examples

| Rules | Input | Expected |
|-------|-------|----------|
| R1 | purchase $12.75 | 12 points earned |
| R1 | purchase $0.40 | 0 points earned |
| R1+R2 | purchase $50.00 | 60 points earned |
| R3 | balance 90, redeem 1 | balance 40, discount $5.00 |
| R3 | balance 90, redeem 2 | IllegalStateException, balance stays 90 |

## Design

| Type | Role | Template status |
|------|------|-----------------|
| LoyaltyAccount | Owns the mutable points balance; delegates earning to the current strategy | stubbed |
| RewardStrategy | Strategy interface: computes points for one purchase | student-creates |
| FlatRateStrategy | R1+R2: rate plus threshold bonus | student-creates |
| Purchase | Immutable amount holder | given |

State: the balance lives in LoyaltyAccount and survives strategy swaps — the strategy is stateless.

Public API:
- `interface RewardStrategy { int pointsFor(Purchase purchase); }`
- `class LoyaltyAccount { void setStrategy(RewardStrategy s); void record(Purchase p); int getPoints(); }`

## Testing Strategy

| Seam | Partitions | Weight | Hidden variant |
|------|-----------|--------|----------------|
| S1 | implement flat-rate rewards: typical amount; sub-dollar rounds to 0; threshold bonus at exactly $50 | 3 | yes |
| S2 | wire strategy into the account: recording delegates; swapping keeps the balance | 2 | no |
| S3 | redeem safely: happy path; over-redeem throws, balance unchanged | 2 | yes |

## Diagram

Yes — students create the strategy interface and one implementation and must see how they plug into the
provided account class.
```

An equally honest "no": `No — the whole exercise is one class with two methods; a diagram would restate two
signatures.`
