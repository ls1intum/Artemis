Analyze the programming exercise for semantic consistency issues focused on identifier naming consistency across problem statement, template, solution, and tests.
Programming language: <programming_language>

Context:
<rendered_context>

Focus on the following category only:
- IDENTIFIER_NAMING_INCONSISTENCY

Return a JSON object with the following schema strictly:
{
    "issues": [
        {
            "severity": "LOW" | "MEDIUM" | "HIGH",
            "category": "IDENTIFIER_NAMING_INCONSISTENCY",
            "description": string,
            "suggestedFix": string,
            "relatedLocations": [
                {
                    "type": "PROBLEM_STATEMENT" | "TEMPLATE_REPOSITORY" | "SOLUTION_REPOSITORY",
                    "filePath": string,
                    "startLine": number | null,
                    "endLine": number | null
                }
            ]
        }
    ]
}
