TODO (Opus): PLANNING prompt for programming-exercise variants (plan Sections 2.4 and 3, PLANNING row).
Rendered via HyperionPromptTemplateService; output parsed into ChangePlan via BeanOutputConverter.

Must instruct the model to produce, given {sourceContext} (rendered exercise: problem statement +
template/solution/test repos) and the intent fields {targetDifficulty}, {domainText}, {additionalInstructions}:
1. variantTitle — a NEW title fitting the transformed exercise (domain change ⇒ re-themed title).
2. problemStatement — the fully rewritten problem statement; tasks must reference test names that will exist.
3. intendedChanges — ordered, concrete, repo-scoped changes ("rename `BankAccount` → `CargoBay` across all repos",
   "remove task 3 and tests `testSortDescending*`").
4. invariants — what must NOT change (grading semantics, task count/weights unless difficulty change says otherwise,
   "test names referenced in problem statement tasks must exist in test repo").

Bias (plan Section 3, transformation order + build-dependency constraint): prefer plans that keep the test surface
stable when the intent allows it — domain re-theme ⇒ rename-only test changes; never touch tests gratuitously.
Follow the tone/structure conventions of the existing prompts in prompts/hyperion/ (e.g.
generate_draft_problem_statement_system.st) and keep placeholders in StringTemplate {placeholder} syntax.
