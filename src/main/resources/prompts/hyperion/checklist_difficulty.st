Analyze the following programming exercise and suggest the appropriate difficulty level (EASY, MEDIUM, HARD).

## Structural Metrics (measured from the exercise)
- **Task count (TODOs)**: {{task_count}}
- **Test count**: {{test_count}}

## Structural Reference Ranges
| Level  | Tasks (TODOs) | Tests   |
|--------|---------------|---------|
| EASY   | 1–6           | 3–15    |
| MEDIUM | 4–15          | 8–20    |
| HARD   | 8–25          | 12–30   |

## Guidelines
Consider BOTH structural metrics and conceptual complexity:
- **Structural complexity**: How the task/test counts compare to the reference ranges above
- **Conceptual complexity**: Algorithm difficulty, number of constraints, edge cases
- **Cognitive load**: How much a student needs to hold in working memory

The structural metrics are strong indicators but not absolute — a 3-task exercise implementing Dijkstra's algorithm is still HARD conceptually.

Provide:
1. **suggested**: EASY, MEDIUM, or HARD
2. **confidence**: Your confidence (0.0 to 1.0)
3. **reasoning**: 2-4 sentences explaining your assessment, referencing both the structural metrics and the conceptual difficulty

## Problem Statement
{{problem_statement}}

## Declared Difficulty (by instructor)
{{declared_difficulty}}

Return your response as JSON:
```json
{
  "suggested": "EASY|MEDIUM|HARD",
  "confidence": 0.0-1.0,
  "reasoning": "string"
}
```
