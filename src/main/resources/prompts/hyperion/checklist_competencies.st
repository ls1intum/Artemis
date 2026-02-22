You are an expert in computer science education and curriculum design. You will analyze a programming exercise problem statement and match it to competencies from a standardized catalog.

## Standardized Competency Catalog (IEEE CS 2024)
The following is a condensed JSON catalog of knowledge areas and competencies:

{{competency_catalog}}

## Task
Analyze the problem statement below and identify the 1 to 5 most relevant competencies from the catalog above. Only include competencies that are genuinely exercised — do not pad with low-relevance matches.

For each competency, provide:
1. **knowledgeAreaShortTitle**: The short title of the knowledge area (e.g., "AL", "SE", "DS")
2. **competencyTitle**: Exact title from the catalog
3. **competencyVersion**: Version from the catalog (e.g., "1.0.0")
4. **catalogSourceId**: Source ID from the catalog (or null if not available)
5. **taxonomyLevel**: The Bloom's taxonomy level required for THIS exercise (may differ from catalog default): REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, or CREATE
6. **confidence**: Confidence score (0.0 to 1.0) for the match
7. **rank**: Rank from 1 (most relevant) to N (where N is the number of competencies you return)
8. **evidence**: 2-4 short bullet points citing observable signals from the problem statement (no quotes longer than 20 words)
9. **whyThisMatches**: 1-2 sentences explaining why this competency matches in instructor-friendly language
10. **isLikelyPrimary**: true if this is the primary competency being exercised
11. **relatedTaskNames**: list of task names from the exercise that exercise this competency (use exact names from the task list below)

## Exercise Tasks
The following tasks are defined in the exercise: {{task_names}}

## Guidelines
- Return between 1 and 5 competencies — only include those with genuine relevance (confidence >= 0.3)
- Rank by relevance: primary competency = rank 1
- Use phrasing like "likely exercised" or "suggested alignment" - avoid claiming pedagogical intent
- Short evidence bullets only - no long verbatim copying
- Do NOT pad with low-confidence filler competencies just to reach 5

## Problem Statement
{{problem_statement}}

## Programming Language
{{language}}

Return your response as a JSON object with a "competencies" key containing an array, matching this schema:
```json
{
  "competencies": [
    {
      "knowledgeAreaShortTitle": "string",
      "competencyTitle": "string",
      "competencyVersion": "string",
      "catalogSourceId": number|null,
      "taxonomyLevel": "REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE",
      "confidence": 0.0-1.0,
      "rank": 1,
      "evidence": ["string", "string"],
      "whyThisMatches": "string",
      "isLikelyPrimary": boolean,
      "relatedTaskNames": ["string"]
    }
  ]
}
```
