# SPEC.md (the specification)

Role: the ONE planning artifact. Written before any code, from the instructor's brief; every later stage
implements THIS and is checked against it. It answers the questions the brief usually leaves open: what will
the student actually compute, which types exist and which of them the student writes, and how the work is
graded. A spec whose graded work is copying literals produces a hollow exercise no matter how well the later
stages execute. Skipped entirely when the instructor already provided a real problem statement — that
statement is the spec. Before approval it receives one focused semantic review against the brief. After approval
it is read-only: later stages repair executable artifacts against the contract instead of weakening the contract
to fit a downstream mistake.

## Numbered rules

State every graded behaviour as a numbered rule (R1, R2, ...) precise enough to test: inputs, computation,
boundaries, error behaviour. Each rule must be one a plausible wrong implementation would get wrong. Rules
carry the computation; do not hide it in the examples.

Mentally execute every loop, repeated policy, and state transition on the smallest permitted input and at a
boundary where the next operation cannot proceed. Define progress, finite termination, minimum cardinality,
and stop-versus-skip behaviour whenever the permitted input domain does not already make them unambiguous.
For randomized behaviour, either expose a controllable randomness source or state complete deterministic
invariants that meaningful non-flaky tests can assert. Do not add a seed or preferred boundary policy by habit;
choose only what the exercise's domain and learning objective need.

## Worked-examples table

A markdown table under `## Worked Examples` with at least these columns: the rule(s) exercised, the input,
the expected result. Include at least two rows total, chosen together to expose the central interaction and an
important alternative or boundary instead of repeating the same witness. State every initial value needed to
derive the outcome inside the example; neither the reviewer nor the student may infer hidden starting state.
Different policies may legitimately share an aggregate final value when another observable column (such as
order, trace, or chosen action) demonstrates their difference. Verify every row's arithmetic by actually computing it in the
sandbox (a throwaway script under /tmp) before writing it down — a wrong number here poisons every later
stage. An example meant to demonstrate a branch, strategy change, or state transition must use a witness for
which the competing behaviours produce observably different outcomes. When variants exist, name the concrete
policy in the input and replay its decisions, not only the final invariant. The executable builder will replay
these rows against the real implementation.

## Design table

A markdown table under `## Design` with one row per type: its name, its role, and a `Template status` that is
EXACTLY one of `given`, `stubbed`, `student-creates` — the gates enforce these tokens literally. `given`
ships complete; `stubbed` normally ships with signatures, Javadoc, and TODO bodies; `student-creates` is ABSENT from
the template (students design the file themselves; it is graded through seeded structural checks and
reflection-based tests, and the template gate rejects a template that still contains it). A brief that asks
students to design or create a type normally demands `student-creates`, but Java must still have enough
declarations for a provided scaffold to compile. `student-creates` is not a difficulty lever. When the exercise fixes a type and API and asks students only to implement its
behavior, prefer a documented `stubbed` scaffold; reserve omission for whole-type creation genuinely assigned by
the brief. An exact approved API can grade creation of that type, but it is not open-ended API design. Choose
ownership from the brief and compile-safe dependency graph rather than applying one mandatory Strategy layout.
When a provided collaborator refers to an omitted type, omit only the dependent members necessary for the starter
to compile and anchor their insertion point in the statement and reflective tests. Put a separate seam on the
collaborator only when students implement independently actionable collaborator work; do not reuse an absent
type's seam ID as a class-body breadcrumb. An empty interface declaration still pre-creates the type, so it is not
faithful when the brief explicitly assigns creating that interface to students.
A type marked `given` must ship complete, so none of its Public API signatures may name a `student-creates` type
that is absent from the template. Resolve that ownership dependency in the specification; never make a given type
empty or silently reassign it to students later merely to make the starter compile.
For every piece of MUTABLE STATE, say below the table which type owns it and whether it survives object
replacement (a swap, reset, or re-registration): tests may only demand what this ownership makes possible.
## Public API

After the Design table, add `## Public API` with the exact contract-visible constructors and methods that the
solution, template, tests, and statement will share, plus only fields deliberately exposed and graded as API.
List signatures only, grouped by owner type. Do not turn private state into public/reflection API merely because
the implementation uses it. Later stages copy this contract; they do not invent constructors, validation, or
alternate signatures after the specification freezes.

The design table also carries the requested difficulty and learning objective. Judge difficulty by the work
left to the student, not by the number of files or formulas. When the brief teaches a collaboration or design
pattern, leave students meaningful work in that collaboration (defining an abstraction when appropriate,
wiring it, or making the context delegate); do not hand them the complete design and call formula transcription
an intermediate pattern exercise. Judge difficulty relative to the requested objective: subtract copied
declarations, literals, and supplied holders, not learner-owned reasoning intrinsic to the concept being taught.
A meaningful abstraction, interchangeable policies, context selection or replacement, and delegation can
themselves carry intermediate reasoning when students own and tests observe the collaboration. Conversely,
provide incidental holders, accessors, and build plumbing unless those are explicitly part of the objective.
Do not add an unrelated mathematical, collection, or state algorithm merely to make a pattern exercise harder.

State one end-to-end observable path through the requested abstraction. For Strategy, say how a context or
client holds or selects the strategy, calls it through the abstraction, and uses its result. Concrete strategies
tested only in isolation do not teach that collaboration. A context may be `given`, but its supplied delegation
does not count as learner-owned collaboration: another `stubbed` or `student-creates` owner must then make
selection, injection, replacement, or delegation consequential student work, and the tests must observe that
work through the end-to-end path. Strategy alternatives must satisfy the same responsibility for overlapping valid inputs and be
meaningfully substitutable through the abstraction. A fixed tag that dispatches mutually exclusive operations
to their only valid handlers is not enough by itself. Each alternative needs a distinct observable policy with
deterministic tie and boundary behavior. Use one coherent oracle model: property-based outcomes with separately
testable policy identity, or a fully deterministic algorithm with exact examples. Do not combine "any valid
result" with a vague heuristic, and do not require global impossibility detection from an incomplete heuristic.
For a brief explicitly teaching a pattern, leave at least one learner-owned collaboration seam—selection,
injection, replacement, or delegation—in addition to concrete policy bodies.

When the brief requests a non-standard or unusual theme, make that choice before naming the public API. Reject
the first familiar textbook example and choose a domain whose constraints genuinely cause the variants'
different computations or interactions. Erase the domain nouns as a check: if the unchanged rules reveal a
familiar example, redesign the behaviour rather than adding themed vocabulary, another trivial variant, or an
arbitrary selector policy. For an intermediate exercise, subtract bare declarations, fixed one-line forwarding,
literal copying, and one-operation formula transcription, but not learner-owned reasoning about the requested
collaboration. A clearly specified multi-step collection or state algorithm still carries implementation
reasoning through its control flow, data-structure operations, progress, termination, and state tracking;
clarity does not make it trivial. Every difficulty contributor must strengthen the requested objective and have
a causal domain rationale. Complexity that would remain essentially unchanged without the requested abstraction
is not evidence of fit. If no meaningful reasoning remains, redesign one central interaction rather than
increasing the number of types or tasks.

Reconcile that ownership with compilation before approving the design. Every testing-strategy seam described
as student work must map to a `stubbed` or `student-creates` row. A given or stubbed type cannot expose an
omitted `student-creates` type in a template signature: the template would not compile. Make the dependent type
student-created too, or omit only the student-owned dependent members. Given types and all non-student-owned members of stubbed types stay identical in both
repositories. Only types marked `student-creates` and the minimum dependent members assigned to the same seam
are absent from the template. Never make a shared API accept the real interface in the solution and `Object` in
the template.

Before approval, compare every Design ownership row against every later Public API and template sentence. A
`student-creates` row is contradicted by prose saying that the template supplies that declaration, signature, or
method body even when the table itself is correct.

Before accepting the rules, perform a scope subtraction pass: remove validation, exception, state, purity,
immutability, thread-safety, and architecture obligations that the brief did not request and that are not strictly
necessary to define the chosen strategies. Choosing the theme and the strategies' contrasting computations is
necessary when the brief leaves them open; adding unrelated defensive policy is not.
Preserve each stated boundary's trigger and timing. Do not turn a call-time rejection into a constructor
precondition, or approve any error path that has no legal setup through the public API.

## Testing strategy

A table under `## Testing Strategy` with one row per SEAM — an independently actionable unit of student work.
Give the first column stable IDs `S1`, `S2`, ...; the template TODOs, grading plan, and statement tasks carry
these IDs through the rest of generation. The second column, `Owner type`, is one exact bare type from the Design table: the type where students implement that seam.
The third column, `Observable responsibility`, states the student-owned behavior, collaboration, or state transition the seam grades and groups the input partitions its tests
need. Keep it an owner-controlled responsibility: do not make a concrete policy's seam responsible for state changes owned by the context that calls it. Each visible seam test
must be independently diagnosable using given support or a tiny fake/recording collaborator; it must not fail first in another independently actionable student seam. When work is
genuinely cumulative, group it into one task rather than giving a later task feedback that an earlier task controls. Use this exact header so the ownership-to-behavior link remains
machine-readable:

`| Seam | Owner type | Observable responsibility | Weight | Hidden variant |`

A `stubbed` owner carries the seam's TODO inside its own source; a `student-creates` owner is absent and has no template TODO—the statement and tests anchor that work. Never put
its ID on an unrelated collaborator merely to complete the ID set. If a collaborator has separately actionable student work, give it a separate seam owned by that collaborator.
Each seam grades student-owned executable behavior, not the presence or exact signature of a supplied declaration or a placeholder that is meant to keep throwing. An ordinary
abstract interface method has no student-owned body: mark the interface `given` when students only implement it, or `student-creates` when the brief assigns its design; do not
label that declaration `stubbed` merely to create a structural seam.
Never one seam per test; never one seam for the whole exercise unless it genuinely is one. Each row has a numeric weight tier (`3` for core learning objectives, `2` for supporting
behaviour, `1` for edge polish; every planned test for the seam carries that tier), and LAST the hidden-variant decision, written as `yes` or
`no` — that cell is read mechanically. Every seam needs at least one `ALWAYS`-visible test. A `yes` adds an
`AFTER_DUE_DATE` variant with fresh witness values because students overfit to visible tests; a `no` must not hide one. The hidden variant
grades silently and is never bound to a task in the statement. Every row is required graded student work; keep
optional enrichment outside this table, the test plan, and the Artemis tasks.

## Diagram

Under `## Diagram`, an honest yes/no with a reason grounded in the design: yes when several collaborating
types or student-created types make an architecture overview genuinely helpful; no when a single class
carries the work. The reason must follow from the design itself — "no, because the statement has no diagram"
is circular and invalid; the statement follows this decision, not the other way around.

## Off-limits at spec time

No `[task]` bindings, no test names, no PlantUML — those belong to later stages. No grading/verifier
internals.

## What may vary

Section granularity follows the exercise; a compact exercise may have three rules, a rich one ten. Extra
table columns (state before/after, notes) are free. Do not inflate a one-class exercise into a pattern, and
do not collapse a pattern exercise into one class.

Follow the section contracts above directly. Do not infer requirements, APIs, or a design from another
exercise.
