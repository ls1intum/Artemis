# Angular design-system linting

The linter is an Angular-native source port of the six design-system policies in
[shadcn lint](https://github.com/shadcn-ui/lint). It does **not** depend on `@shadcn/lint`,
patch its package, load its bundle, or translate Angular templates into JSX.

## Structure

- `design-system/rules/` owns the six policies, contracts, diagnostics, and suggestions.
- `design-system/grammar/` classifies utilities against the pinned `cn/config` grammar. Tailwind's
  palette and default scales come from the installed framework, not copied theme data.
- `design-system/project/` reads the explicitly configured theme and its imports. There is no
  `components.json`, React import discovery, or guessed stylesheet fallback.
- `design-system/tailwind/` asks the actual Tailwind compiler which utilities and variants generate
  CSS. A worker bridges its asynchronous API to synchronous ESLint rule callbacks. A broken
  configured theme fails verification instead of silently falling back to grammar-only checks.
- `angular-design-system-project.mjs` discovers selectors, inherited signal/decorator inputs, and
  directive composition using TypeScript symbols and Angular's selector matcher.
- `angular-design-system.mjs` supplies template sites to the native policies. Its expression and
  owner helpers conservatively resolve same-file readonly values and preserve source locations.
- `angular-design-system-host.mjs` reads Angular host metadata and `@HostBinding`, sharing records
  between appearance checks and the private-class guard.
- `angular-design-system-stylelint.mjs` checks stylesheet subjects and nested selectors against
  discovered components. Inline `@Component.styles` uses the same evaluator. Variant `@apply`
  declarations on ordinary wrappers are checked through the configured Tailwind compiler too.
- `angular-design-system-class-styles.mjs` checks Tailwind variant classes on ordinary ancestors
  as well as protected controls. It reuses the compiler's generated CSS and the stylesheet/private
  namespace checks, including custom variants and escaped selectors; it does not parse variant
  selector strings with a separate grammar.
- `angular-design-system-inline-colors.mjs` rejects literal UI colors on ordinary elements and
  Angular hosts, reusing the style adapter, color grammar and variable resolver. Conditional values
  and local CSS-variable indirection are checked; runtime domain colors remain allowed.
- `angular-design-system-lintable-templates.mjs` rejects inline templates Angular ESLint cannot
  extract, rather than silently skipping them.
- `tum-ui-design-system.mjs` supplies Artemis's roots, theme, and layout policy.

The plugin factory accepts `root`, `components`, `sources`, `theme`, and `scope`. Source directories
are relative to `root`; `sources` defaults to `components`. `scope` defaults to `all`; Artemis uses
`components` while legacy application classes remain outside design-system hosts. The additional
`no-literal-inline-colors` rule also checks ordinary elements without banning their layout styles.
CSS/SCSS checks
cover all application styles, including global styles outside `app/`. Package implementation files
are excluded by ESLint/Stylelint configuration, not component-specific rule exemptions.

Diagnostics identify the authored class or property and the protected component. Stylesheet
appearance errors list discovered public appearance inputs and the component source to change
when no suitable input exists. Inline styles include CSS coordinates while ESLint retains the
actual TypeScript string location. Unknown bindings report once per source site; compiler failures
remain errors, not permission to skip verification.

## Source and maintenance

The adapted policy, grammar, theme, and compiler-bridge source originates from
[`shadcn-ui/lint` commit 093ae9d](https://github.com/shadcn-ui/lint/tree/093ae9db214772afe0de40299d224c7b5e24bdeb/packages/lint/src),
corresponding to `@shadcn/lint@0.1.5`. Its MIT notice is retained in `design-system/LICENSE`.
The grammar validators retain upstream's attribution to `shadcn-ui/cn`, also MIT-licensed.

This is maintained source, not generated output. JSX collection, React forwarding, component-import
recognition, wrapper discovery, project-specific `cn` loading, and unused recognition options were
removed. Angular metadata supplies component identity and size/variant inputs. Rules require the
Angular callbacks in `parserServices.designSystem`; they have no React fallback. Optional diagnostic
notes use `settings.designSystem.note`.

When adopting a policy fix, compare the corresponding upstream source and tests, adapt it here,
and add a regression. Do not replace the port with an npm dependency or a copied bundle.
`design-system/fixtures/upstream-verdicts.json` contains independently captured reference diagnostics
from the original package, with version and commit provenance. Tests compare the port against those
saved results, not against itself. New Angular-specific behavior needs its own fixtures.

Run `pnpm run test:rules`, `pnpm run lint`, and `pnpm run stylelint`. Tests cover the six policies,
source locations, actual Tailwind utilities/variants, Angular metadata, and CSS/SCSS selectors.
Sass cases are compared with compiler output for
[nested properties](https://sass-lang.com/documentation/style-rules/declarations/#nesting) and
[`@at-root`](https://sass-lang.com/documentation/at-rules/at-root/).

## Analysis boundaries

This is static linting, not an Angular runtime or a complete CSS cascade simulator. Source roots
define the protected component set; Angular import scopes and transitive class forwarding through
application wrappers are not reconstructed. Appearance checks apply to host bindings when their
selector identifies a protected control. Static private class names are also rejected on ordinary
Angular hosts, but unknown dynamic classes there are not globally banned.

Readonly resolution is conservative about mutation and escape. Imported values, calls, and computed
class names are reported on protected hosts rather than evaluated. Template-local names remain
unresolved; `this.field` explicitly addresses the component. Owner metadata comes from saved
TypeScript files. Use literal `@Component` metadata or an external template when the extraction
guard rejects an unsupported inline form, including aliased or barrel-imported decorators and indirect metadata. Inline templates use a
direct `Component` import from `@angular/core`; external templates do not have this restriction.

Generated variant selectors are checked when their CSS explicitly identifies a protected control
or private class. Generic selectors, inheritance, runtime DOM changes, and selectors manufactured entirely by Sass
outside a statically protected context are not completely modeled. Within known protected contexts,
unreadable mixins, extension, and interpolation are errors rather than passes. Keep browser coverage;
do not solve findings with blanket allowlists, selector rewrites, or inline-style migration.
