Analyze the following programming exercise problem statement for quality issues related to CLARITY, COHERENCE, and COMPLETENESS.

## Issue Categories
- **CLARITY**: Ambiguous requirements, unclear inputs/outputs, vague language
- **COHERENCE**: Contradictory statements, inconsistent terminology
- **COMPLETENESS**: Missing definitions, undefined edge cases, missing environment assumptions

## For Each Issue Provide
1. **category**: CLARITY, COHERENCE, or COMPLETENESS
2. **severity**: INFO (minor), WARNING (notable), or ERROR (critical)
3. **description**: What is wrong (1-2 sentences)
4. **location**: Line range where the issue occurs (if identifiable)
5. **suggestedFix**: Concrete suggestion to fix the issue
6. **impactOnLearners**: How this increases extraneous cognitive load (1 sentence)

## Guidelines
- Be specific and actionable
- Focus on issues that affect student understanding
- Don't nitpick style - focus on substantive problems
- If no issues found, return an empty array

## Problem Statement
{{problem_statement}}

Return your response as a JSON array:
```json
[
  {
    "category": "CLARITY|COHERENCE|COMPLETENESS",
    "severity": "INFO|WARNING|ERROR",
    "description": "string",
    "location": { "startLine": number, "endLine": number } | null,
    "suggestedFix": "string",
    "impactOnLearners": "string"
  }
]
```
