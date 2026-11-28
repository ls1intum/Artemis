## Lecture Material
{{groundingBlock}}

## Question Under Review
Question type: {{questionType}}
Title: {{questionTitle}}

{{questionText}}

Options, each prefixed with its answer-key marker [correct] or [wrong] and followed by its explanation where it has one. The markers are pipeline metadata that students never see: judge only the option texts and explanations, never the markers.
{{options}}

Explanation of the question: {{explanation}}

## Output Requirements
Report one entry per failure mode, using exactly these mode names: FACTUAL_ERROR, AMBIGUOUS_CORRECT_ANSWER, OFF_TOPIC, NEAR_DUPLICATE, ILL_FORMED_DISTRACTORS.
Produce a JSON response that strictly follows this format:
{{format}}
