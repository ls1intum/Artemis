# Client style comparison

Scripts that compare the styles of the client before and after a change, in the build output and in a
browser. Use them for a change to a global stylesheet, the theme tokens, TUM AET UI or many component
stylesheets.

The guide is in the developer documentation:
[Compare the styles of two clients](../../../documentation/docs/developer/client-style-comparison.mdx).

| Script                        | Purpose                                                                          |
| ----------------------------- | -------------------------------------------------------------------------------- |
| `compare-builds.mjs`          | Compares the declarations of two builds (global stylesheet and component styles) |
| `capture.mjs`                 | Records the computed styles of the pages and dialogs of a running client         |
| `diff.mjs`                    | Compares two captures and groups the differences                                 |
| `matched-rules.mjs`           | Prints the rules that match one element, with their cascade layer                |
| `cascade-audit.mjs`           | Lists where a cascade layer loses although its selector is the more specific one |
| `important-audit.mjs`         | Lists the `!important` declarations that change nothing on the visited pages     |
| `pages.mjs`                   | The pages and overlays that `capture.mjs` and the two audits visit               |
| `lib.mjs`                     | Shared helpers: options, login, the recorded properties                          |
| `cascade-audit-browser.mjs`   | The part of the cascade audit that runs in the page                              |
| `important-audit-browser.mjs` | The part of the `!important` audit that runs in the page                         |

Run a script with `--help` to see its usage, for example:

```bash
node supporting_scripts/client/style-comparison/capture.mjs --help
```

The scripts use Playwright and PostCSS from the dependencies of the repository (`pnpm install`); the
browser comes from `pnpm exec playwright install chromium`. Captures are written to
`build/style-comparison`, which Git ignores.
