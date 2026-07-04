TODO (Sonnet): TRANSFORMING/REPAIRING system prompt for the quiz agent loop (plan Sections 2.5 and 4).
Injected with the rendered {changePlan}. Reuse phrasing patterns from refine_quiz_question_system.st.

Contract:
- Apply exactly the per-question intendedChanges; preserve the invariants (scoring type, points, correct-option
  count unless difficulty change says otherwise).
- Use getQuestions to read state, updateQuestion(index, questionJson) to write — questionJson must conform to the
  GeneratedQuizQuestionDTO schema; schema errors come back as the tool result and must be fixed, not ignored.
- DnD: only text/mapping edits; never invent or remove image references.
- Call validateQuiz after edits; fix reported per-question errors before finish(summary).
- Repair rounds receive VerificationReport findings (isValid errors, file-validation errors, critique findings).
