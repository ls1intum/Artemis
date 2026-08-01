# Problem statement rendering parity corpus

The `.md` files in this directory are the corpus for
`ProblemStatementRenderingParityTest` (server) and
`problem-statement-parity.spec.ts` (client, under
`src/main/webapp/app/programming/shared/instructions-render/ssr/`). Together
they form the differential parity gate for the server-side problem-statement
renderer: every file here is rendered once through the server pipeline and
once through the legacy client markdown pipeline, and the two results are
compared.

## Authoring rules

- Files must use the same `[task][name](refs)` syntax that production
  problem statements use. `ProblemStatementRenderingParityTest` extracts task
  references with the same pattern the server renderer uses, builds one
  passing test-feedback entry per distinct reference, and asserts every task
  resolves to `data-test-status="success"`. A file with no task reference
  fails the test outright (the corpus is only useful if every file actually
  exercises task resolution).
- Each file carries a feature area, and the client spec's
  "keeps the corpus exercising the features the gate is about" test fails if
  one of them disappears. Removing content here silently weakens the gate
  rather than closing a gap:
    - `markdown-features.md`: fenced code block, `[!NOTE]` alert, bare URL,
      table.
    - `name-based-tasks.md`: ordered list of name-based tasks, a table, a
      PlantUML diagram.
    - `inline-structure.md`: emphasis, strong emphasis, inline code,
      backslash escapes, a nested list with a task between two text nodes,
      root-relative and external links, root-relative and external images.
- Two known open divergences must stay out of the corpus, because the gate
  would fail on them. Both are held by executable tests in the
  "deliberate divergences from the legacy task component" block of
  `problem-statement-parity.spec.ts`, which turn red when either pipeline
  changes; this list is only navigation, the tests are the record.
    - `~~strikethrough~~`: the server emits `<del>` (commonmark-java, which
      matches GFM) and the legacy pipeline emits `<s>` (markdown-it).
    - `[task][name](refs)` inside a fenced code block: the legacy pipeline
      escapes the marker in the raw markdown and shows the backslashes, the
      server masks code blocks first and shows the block verbatim.
- Adding a new `.md` file here automatically extends the guardrail: both the
  server test (`@MethodSource` over this directory) and the client spec
  (`readdirSync` over this directory) pick up every `.md` file without any
  other code change.

## `rendered/`

Generated server-side HTML fixtures, one per corpus file, used as the
baseline for the client-side diff. Do not hand-edit these. A normal test run
of `ProblemStatementRenderingParityTest` compares against them; it never
overwrites them. Regenerate deliberately with:

```
./gradlew test --tests ProblemStatementRenderingParityTest -Dartemis.regenerateProblemStatementFixtures=true -x webapp
```

Review the diff before committing: the fixture pins the renderer's exact
HTML output, so any intentional renderer change requires a reviewed,
deliberate regeneration.
