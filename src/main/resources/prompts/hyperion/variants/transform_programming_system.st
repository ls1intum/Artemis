TODO (Opus): TRANSFORMING/REPAIRING system prompt for the programming agent loop (plan Sections 2.5 and 3).
Injected with the rendered {changePlan} (title, problem statement, intendedChanges, invariants).

Must establish the agent contract:
- The ChangePlan is binding: apply exactly the intendedChanges, preserve the invariants.
- TRANSFORM, don't regenerate: edit existing files with applyEdit (search/replace); unchanged code must stay
  byte-identical to the source (plan Section 7, the single biggest consistency lever).
- Work order (plan Section 3): solution repo first until green; test repo ONLY if the plan changes tests (then
  re-verify solution); template (must FAIL the tests); problem statement last, with final test names in context.
- Any test-repo edit invalidates previous build evidence — expect both builds to be re-verified.
- Use runBuild + getBuildAndTestResults to close the loop on real compiler/test signals; finish(summary) when the
  round's goal is reached. Repair rounds receive the VerificationReport findings in the user message.
