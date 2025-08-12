Analyze the programming exercise for structural consistency issues between the problem statement, template code, solution code, and tests.
Programming language: <programming_language>

Context:
<rendered_context>

Focus on the following categories only:
- METHOD_RETURN_TYPE_MISMATCH
- METHOD_PARAMETER_MISMATCH
- CONSTRUCTOR_PARAMETER_MISMATCH
- ATTRIBUTE_TYPE_MISMATCH
- VISIBILITY_MISMATCH

Return a JSON object with the following schema strictly:
{
    "issues": [
        {
            "severity": "LOW" | "MEDIUM" | "HIGH",
            "category": "METHOD_RETURN_TYPE_MISMATCH" | "METHOD_PARAMETER_MISMATCH" | "CONSTRUCTOR_PARAMETER_MISMATCH" | "ATTRIBUTE_TYPE_MISMATCH" | "VISIBILITY_MISMATCH",
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
