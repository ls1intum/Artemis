TODO (Sonnet): PLANNING prompt for quiz variants (plan Sections 2.4 and 4, PLANNING row), modeled on the existing
generate_quiz_questions_* prompts. Output parsed into ChangePlan via BeanOutputConverter.

Given {sourceContext} (serialized quiz: questions, options, mappings, scoring types) and the intent fields
{targetDifficulty}, {domainText}, {additionalInstructions}, produce:
1. variantTitle — re-themed for domain changes.
2. problemStatement — the quiz-level description if present.
3. intendedChanges — PER-QUESTION intended change (indexed), e.g. "Q2: re-theme options to space-station domain,
   keep exactly one correct option".
4. invariants — keep scoring type, keep points, preserve number of correct options unless the difficulty change
   says otherwise; DnD images are carried over unchanged (text/mapping edits only, plan Section 1 non-goals).
