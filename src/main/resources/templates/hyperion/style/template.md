# Template (student starting point)

Role: what the student opens first. It must compile, fail every task-bound behavioural test at its intended
TODO, and teach the contract on its own — students work from it, using the statement only as reference.

## Javadoc

Every stubbed member carries complete javadoc (or the language's doc idiom) restating its student-visible
contract: purpose, parameters, return, error behavior. This javadoc is byte-identical between template and
solution (see `style/solution.md`); never leave a member undocumented because "the statement already says
it" — the template is where students actually read while coding.

## TODO placement

Anchor each stubbed seam with `// TODO: <exact Observable responsibility from the Testing Strategy>` INSIDE its owner member body, placing it
at the assigned change location (above the placeholder throw for a new unfinished body) — never between the javadoc and the signature,
never between an annotation and the signature. Never put the specification's seam ID (`S1`, ...) in the comment: students read the
TODO, but the statement never defines seam IDs, and the Testing Strategy already links each seam to its owner. Every unfinished member of a
stubbed owner carries a TODO; the same wording may repeat within that owner when one task spans several members. A TODO marks unfinished student work only:
never leave one on code that is already complete, and never leave authoring or design notes in any file.

The exception is a stubbed owner whose approved Public API marks a member `/** @studentCreates */`, either because declaring it is the learning objective or because its signature needs an absent type. Omit only the marked members; retain the owner's supplied fields, constructors, and working operations. Keep it compile-safe and put
one TODO in its class body where students add the missing declarations. Never restore the missing type, use `Object`, edit the specification, or anchor the absent type's work there.

## Types students must create

When the design says students define a type themselves, the template must NOT ship that type: omit its file and
keep the template compiling without it. Its statement task and reflective/structural tests are the truthful
anchors; do not put a TODO for it on unrelated collaborator code merely because the type has no template file.

When a provided context will eventually refer to an omitted student-created interface, keep the context class
but omit only the student-owned members explicitly marked `@studentCreates` in the approved contract. Do not ship an empty interface merely to make those signatures compile, and do not
weaken only the template API to `Object`; use the class-body owner TODO above, and let tests inspect the completed API reflectively.

## Data-holder plumbing

Provide routine constructors, fields, and accessors already implemented unless implementing them is itself an
explicit, tested learning objective. Keep student work focused on the stated objective, not boilerplate. Do
not pre-place fields or helpers the template itself never uses — unused scaffolding steers students toward a
design the tests may not even reward.

## Honesty

Never inspect stack traces, test names, or any grading context to change behavior. Supplied behavior may
remain implemented, including within a method the learner modifies.

## What may vary

For modification tasks, preserve the existing implementation and mark the assigned change with its TODO.
Do not replace working context with an empty body. Preservation checks must pass on both repositories and
carry zero credit; assessed tests must still fail on the starter.

For a new unfinished body, the placeholder (`throw new UnsupportedOperationException("Not implemented")` is the Java default) follows
the language's idiom; a returned placeholder value is acceptable only if every test rejects it. TODO wording
mirrors this exercise's tasks. How much is stubbed versus given follows the design's template-status
column, not a fixed ratio.

## Teaching-quality defaults

Keep prerequisite scaffolding complete: initialize supplied collections, implement routine data holders, and
provide simple enums and exception types when their declaration is not the objective. Do not make students
write inheritance syntax or generic declarations before the brief says they have learned them. Provided code
may hide such mechanisms behind a small documented domain API; avoid needless wrapper hierarchies.

Document existing class invariants and each operation's preconditions, postconditions, and state changes in
ordinary language. Describe only guarantees the approved contract actually makes. Prefer `field` consistently
(`field (attribute)` once if useful); follow explicit course terminology. Never add author/version tags or
fabricated attribution. An entry point, when present, demonstrates a useful supported scenario in the solution;
do not ship an empty `main` as if it were a working demo, or solve the learner-owned scenario in the starter.

Ownership tags are specification metadata, not Java annotations or student Javadoc. For a member students
create, put its contract in the statement and a concise insertion TODO in the existing class. The solution
adds its documented declaration; documentation of already supplied members stays unchanged.
