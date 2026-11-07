Extract the instructor's requested learner goals from the brief alone, before any exercise design is considered.

Enumerate every explicit learning objective and competency as a separate atomic goal. Quote the brief verbatim for each goal. Split a list of competencies into its
individual items; do not omit a smaller competency because another objective is more prominent. Include reading or completing an API when it is explicitly assigned
learner work. Prerequisites, forbidden topics, difficulty and course context are constraints, not additional learner implementation tasks.

Do not invent goals or add a preferred domain, algorithm or public API. Do not rewrite the instructor's words; line-wrap whitespace may be normalized. Candidate
artifacts are deliberately absent. Return only JSON:
{"goals":[{"quote":"verbatim requested learning objective or competency"}]}
