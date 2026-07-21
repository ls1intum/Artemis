# SPEC.md (the behavioural specification)

Role: the content decision. Written before any design or code, from the instructor's brief; every later stage
implements THIS. It exists to answer one question the brief usually leaves open: what will the student
actually compute? A spec whose graded work is copying literals produces a hollow exercise no matter how well
the later stages execute. Skipped entirely when the instructor already provided a real problem statement —
that statement is the spec.

## Choose an archetype first

Name the exercise's shape before writing rules. Pick the archetype whose non-hollow contract your rules will
honor, or declare "none of these" with a justification:

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

## Off-limits at spec time

No `[task]` bindings, no test names, no PlantUML/diagrams, no class design beyond what the rules force —
structure is the DESIGN stage's job. No grading/verifier internals.

## What may vary

Section names and rule granularity follow the exercise; a compact exercise may have three rules, a rich one
ten. The archetype menu is a lens, not a cage — "none of these" with a reason is a legitimate choice. The
table's extra columns (state before/after, notes) are free.

## Exemplar (FORM only — never copy its topic, API, or design)

```markdown
# Cafe Loyalty Rewards

Archetype: calculator-with-rules — points are computed from purchase amounts through rate and threshold
rules; redeeming interacts with the banked balance.

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
```
