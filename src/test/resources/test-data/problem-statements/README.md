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
- `markdown-features.md` specifically pins the measured markdown gaps
  between the two pipelines (syntax highlighting, GitHub-style alerts, bare
  URL linkification). Do not remove its fenced code block, its `[!NOTE]`
  alert, or its bare URL without also updating the gap counts asserted in
  `problem-statement-parity.spec.ts` — removing them silently weakens the
  gate rather than closing a gap.
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

Review the diff before committing — the fixture pins the renderer's exact
HTML output, so any intentional renderer change requires a reviewed,
deliberate regeneration.
