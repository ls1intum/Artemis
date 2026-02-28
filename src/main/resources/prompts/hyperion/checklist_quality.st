Analyze the following programming exercise problem statement for quality issues.

You are a strict, deterministic reviewer. Your goal is to produce REPRODUCIBLE results: analyzing the same problem statement twice must yield the same issues.

## Issue Categories — Report ONLY issues that match these precise definitions

### CLARITY — A requirement is ambiguous or underspecified
Flag ONLY when:
- A task describes behavior without specifying expected input/output format (e.g., "handle errors appropriately" without saying how)
- A quantitative constraint is missing where one is needed (e.g., "the list can be large" — how large?)
- A pronoun or reference is ambiguous (e.g., "it should return the result" — which result?)
- Two reasonable students could interpret a requirement differently and produce different correct solutions

Do NOT flag:
- Stylistic preferences (e.g., "could be worded better")
- Common programming terms used in their standard meaning
- Requirements that are clear from context even if terse
- Sections that explain concepts rather than specify requirements

### COHERENCE — The problem statement contradicts itself or uses inconsistent terminology
Flag ONLY when:
- Two statements directly contradict each other (e.g., "return null" in one place vs. "never return null" elsewhere)
- The same concept is referred to by different names without clarification (e.g., "node" vs. "vertex" used interchangeably without stating they are equivalent)
- A task references a concept, class, or method not introduced anywhere in the problem statement

Do NOT flag:
- Variation in phrasing that does not cause confusion
- Using both a full name and an abbreviation (e.g., "Binary Search Tree (BST)" then "BST" later)

### COMPLETENESS — Critical information is missing, making a task unsolvable or untestable
Flag ONLY when:
- A task is impossible to implement without information that is neither provided nor reasonably inferable from context
- Expected behavior for a key edge case (null input, empty collection, boundary values) that the exercise explicitly manipulates is never specified
- A referenced class, interface, or method that the student must use is not defined or described anywhere

Do NOT flag:
- Edge cases that are obvious from context or standard practice
- Missing info that is irrelevant to the tasks
- Suggestions for "nice-to-have" additions (e.g., "could also mention performance requirements")
- Standard language/framework behavior that students are expected to know

## Severity Levels
- **HIGH**: A student cannot complete a task or will produce wrong results due to this issue
- **MEDIUM**: A student will likely be confused and may need to guess, but can still attempt the task
- **LOW**: A minor gap that a careful student would notice but could work around

## Reporting Rules
1. If no issues match the precise criteria above, return an EMPTY array. A well-written problem statement with no issues is the EXPECTED case — do not search for problems to report.
2. Do NOT invent hypothetical issues. Every issue must cite a specific phrase, sentence, or section in the problem statement.
3. Each issue must be distinct — do not report the same underlying problem multiple times under different categories.
4. Be conservative: when in doubt whether something is truly an issue, do NOT report it.
5. Limit your report to at most 5 issues. If you find more, report only the highest-severity ones.

## For Each Issue Provide
1. **category**: CLARITY, COHERENCE, or COMPLETENESS
2. **severity**: LOW, MEDIUM, or HIGH (use the definitions above strictly)
3. **description**: Quote the problematic text, then explain in 1 sentence why it meets the issue criteria
4. **location**: Line range where the issue occurs (if identifiable)
5. **suggestedFix**: A concrete, minimal rewording that resolves the issue
6. **impactOnLearners**: One sentence explaining the specific student confusion this causes

## Problem Statement
{{problem_statement}}

Return your response as a JSON object with an "issues" key containing an array, matching this schema:
```json
{
  "issues": [
    {
      "category": "CLARITY|COHERENCE|COMPLETENESS",
      "severity": "LOW|MEDIUM|HIGH",
      "description": "string",
      "location": { "startLine": number, "endLine": number } | null,
      "suggestedFix": "string",
      "impactOnLearners": "string"
    }
  ]
}
```
