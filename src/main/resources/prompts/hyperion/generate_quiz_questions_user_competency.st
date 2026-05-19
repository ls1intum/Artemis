## Course Context
Course: {{courseTitle}}
Course Description: {{courseDescription}}

{{lectureSection}}

## Competencies to Assess
The following competencies should be covered across the generated questions.
Across all questions, every competency must appear at least once:
{{competencyList}}

{{relationSection}}

Questions should require students to integrate and connect the listed competencies rather than treating each in isolation.

## Generation Configuration
Further Instructions: {{optionalPrompt}}
Language: {{language}}
Question Types: {{questionTypes}}
Number of Questions: {{numberOfQuestions}}
Difficulty (0 = very easy, 100 = very hard): {{difficulty}}

## Output Requirements
Each question in the output must include a `title`, `questionText`, and `options`. Only include `hint` or `explanation` fields if explicitly requested.
Produce a JSON response that strictly follows this format:
{{format}}
