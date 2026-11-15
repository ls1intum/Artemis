# Solution (reference implementation)

Role: the graded reference implementation and the ground truth for "correct behavior given the stated
contract". It must compile, pass every behavioural test, and itself exemplify any design the exercise
teaches — never special-case or bypass an abstraction it defines; if the clean design cannot pass a test,
the design or the test is wrong, not the call site.

## Diff discipline

Solution = template + the student's work, nothing else. Javadoc and every non-TODO comment stay byte-identical
between template and solution; implementing a task replaces its `// TODO` line with code plus any
`implements`/imports it demands. Never author documentation only in the solution, never delete a template
comment while implementing. Why: graders and students both read the diff as "exactly what was expected of
me" — every stray hunk pollutes that signal.

## Code style

Idiomatic and minimal: the shortest correct implementation a competent instructor would accept, not a
showcase of every language feature. Add a comment only where it teaches something a reader could not infer
from the code itself (a non-obvious invariant, a rounding rule, why a boundary is inclusive) — never comment
what the code already says plainly, and never leave authoring notes to graders or reviewers in the code.

## Never reconcile to a wrong test or example

If a test assertion or a statement's worked example turns out to be wrong (inconsistent arithmetic, a
contradicted rule), fix the test or the example — never bend the solution to match a flawed check.

## What may vary

Internal structure (helper methods, iteration style, data structures) is free as long as the public API
matches the design and the diff stays task-shaped. Other languages follow their own idiom for the same
rules.

## Model the practices being taught

Use descriptive domain identifiers, parameterized collections rather than raw types, and existing domain
objects rather than parallel primitive representations. Prefer final fields for stable dependencies and
immutable values where appropriate; keep genuinely changing state mutable. Parameters may be final when it
clarifies intent, never as unrelated graded work. Prefer format strings for structured output, including
labeled numeric results; use a fixed locale when numeric formatting is part of the output contract. Simple
concatenation can remain where formatting would introduce untaught concepts. This is a reference-code choice,
not a new restriction on equivalent student solutions.

Given support should be safe within its documented contract: initialize owned state, preserve established
invariants, and do not accidentally expose mutable internals. Do not add new student-graded validation,
exception, or immutability obligations under the guise of defensive programming. Express existing guarantees
in Javadoc without algorithm spoilers. Use the same terminology and documentation in both repositories.
Omit author/version tags. Document newly added student-created members in the solution; these have no template
counterpart, unlike documentation of supplied members, which remains byte-identical.
