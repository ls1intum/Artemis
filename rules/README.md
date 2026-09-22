# Angular design-system linting

The Angular integration uses the six rule implementations in pinned `@shadcn/lint@0.1.5`.
It does not copy the upstream Tailwind classifier, theme resolver, contracts, or diagnostics.

- `angular-design-system.mjs` adapts Angular template sites to upstream rule callbacks.
- `angular-design-system-expressions.mjs` translates supported Angular expressions to the expression
  shapes those callbacks consume. Unknown expressions remain unknown; no JSX source is generated.
- `angular-design-system-project.mjs` discovers selectors, inherited signal/decorator inputs, and
  directive composition using TypeScript symbols and Angular's selector matcher. Components and
  directives that supply host classes/styles own appearance.
- `angular-design-system-host.mjs` adapts protected TypeScript host metadata and `@HostBinding`.
- `angular-design-system-inline-styles.mjs` checks `@Component.styles` through the same CSS/SCSS
  evaluator used by external Stylelint, including ordinary application components.
- `angular-design-system-lintable-templates.mjs` rejects inline templates the standard Angular ESLint
  processor cannot extract, rather than silently skipping them.
- `angular-design-system-owner.mjs` resolves conservative same-file readonly template values.
- `angular-design-system-stylelint.mjs` parses stylesheet subjects and nested selectors, discovers
  their component owners, and delegates declaration policy to upstream `no-inline-styles`.
- `tum-ui-design-system.mjs` supplies Artemis's source roots, actual Tailwind theme, and layout policy.

The template plugin factory accepts `root`, `components`, `sources`, `theme`, and `scope`.
`components` and `sources` are source directories relative to `root`; `sources` defaults to
`components`. `scope` defaults to `all`. Artemis opts into `components` while legacy application
classes remain outside design-system hosts. Implementation files are excluded through ESLint and
Stylelint configuration, not rule exemptions.

## Upstream patch

`patches/@shadcn__lint@0.1.5.patch` exposes three syntax-adapter callbacks, component size/variant
metadata, the upstream class-attribute predicate, and explicit project-theme registration. Without these seams the public package only
collects JSX sites and assumes shadcn project configuration. Original JSX behavior remains the default.
This is a local integration, not official upstream Angular support.

Use [pnpm's patch workflow](https://pnpm.io/cli/patch) when upgrading the dependency. Review changes
against the [upstream collector](https://github.com/shadcn-ui/lint/blob/main/packages/lint/src/sites/collect.ts)
and [rule documentation](https://github.com/shadcn-ui/lint/tree/main/docs/rules), then regenerate the
patch with `pnpm patch-commit`. Do not edit installed `node_modules` without updating the committed
patch and lockfile. An upstream adapter API would remove this patch, not the Angular syntax adapter.

Run `pnpm run test:rules`, `pnpm run lint`, and `pnpm run stylelint`. The integration suite compares
Angular verdicts with the real JSX plugin for all six rules, checks original diagnostic locations,
and compiles custom Tailwind utilities/variants to detect accidental fallback to grammar-only checks.
Stylelint covers all application CSS/SCSS, including global styles outside `app/`.
The Stylelint suite covers selector subjects, nesting, negative selectors, decoded CSS escapes,
private namespaces, and component discovery. SCSS cases are compared with actual Sass compiler
output for [nested properties](https://sass-lang.com/documentation/style-rules/declarations/#nesting)
and [`@at-root`](https://sass-lang.com/documentation/at-rules/at-root/). Known protected mixins,
`@extend`, `@apply`, and interpolation fail explicitly when their declarations cannot be verified.
Consumer layout migrations still need visual checks; rule parity does not establish visual parity.

## Analysis boundaries

This is static linting, not an Angular runtime or a complete CSS cascade simulator. Source roots
define the protected component set; Angular import scopes and transitive class forwarding through
application wrappers are not reconstructed. Host bindings are checked when their selector identifies
a protected control, not for an arbitrary behavioral directive whose eventual host is unknown. Same-file readonly field resolution is deliberately
conservative about mutation and escape. Imported values, calls, and computed class names cannot be
verified and are reported on protected template hosts rather than evaluated. Bare names matching a
template-local declaration are conservatively unresolved; `this.field` explicitly addresses the
component. Owner metadata comes from saved TypeScript files, so save initializer changes before
rechecking an external or extracted template. Inline extraction uses Angular ESLint's processor. The extraction guard rejects unsupported inline
forms, including aliased decorators and indirect template metadata. Use a normal `@Component` import
and literal metadata or an external template. Typed symbol resolution also recognizes Angular
decorators re-exported through local barrels without treating unrelated decorators as Angular.

Selector matching is not CSS cascade analysis. Generic selectors, inheritance, runtime DOM changes,
and selectors manufactured entirely by Sass outside a statically protected context are not completely
modeled. Inside known protected contexts, unreadable composition is an error rather than a pass. Keep visual/browser
coverage. Do not solve findings with blanket allowlists, selector rewrites, or inline-style migration.
