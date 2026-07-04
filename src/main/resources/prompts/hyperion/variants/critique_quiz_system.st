TODO (Sonnet): LLM self-critique pass used as the quiz verifier's soft gate (plan Section 4, VERIFYING row) —
reuse/adapt the existing refine_quiz_question prompts as a critique step.

Given {sourceContext}, {changePlan}, and the transformed {variantContext}, answer STRUCTURED (findings list,
empty = pass):
- Is the distractor set plausible (no giveaways, no duplicates, consistent style)?
- Is EXACTLY the requested change applied — nothing more, nothing less (plan Section 1 quality bar: "consistent
  with the source exercise except for the requested changes")?
- Are the invariants from the ChangePlan preserved?
Findings feed the repair round as VerificationReport entries; this gate NEVER overrides the deterministic gates
(isValid/file validation run first, plan Section 2.6 ordering).
