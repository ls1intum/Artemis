## Course Context
Course: {{courseTitle}}
Course Description: {{courseDescription}}

## Generation Configuration
Topic: {{topic}}
Further Instructions: {{optionalPrompt}}
Language: {{language}}
Question Types: {{questionTypes}}
Number of Questions: {{numberOfQuestions}}
Difficulty (0 = very easy, 100 = very hard): {{difficulty}}

## Output Requirements
Each question in the output must include a `title`, `questionText`, and `options`. Only include `hint` or `explanation` fields if explicitly requested.
Produce a JSON response that strictly follows this format:
{{format}}
