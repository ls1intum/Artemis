import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import angularPlugin from '@angular-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import storybookPlugin from 'eslint-plugin-storybook';
import typescriptParser from '@typescript-eslint/parser';
import angularTemplateParser from '@angular-eslint/template-parser';
import angular from 'angular-eslint';
import tseslint from 'typescript-eslint';
import eslint from '@eslint/js';
import localRulesPlugin from './config/eslint/rules/index.mjs';
import buttonInlineTemplates from './config/eslint/rules/button-inline-templates.mjs';
// Builds `no-restricted-imports` patterns that block importing a sibling client layer
// (e.g. `shared-ui` or `editor`) from another layer — covering both the absolute alias path
// (`app/<layer>/...`) and relative parent-traversal paths (`../<layer>/...`, `../../<layer>/...`, …).
//
// Each relative depth is listed explicitly on purpose: ESLint's minimatch wildcard does NOT
// traverse `..` segments, so a single globstar pattern fails to flag nested imports such as
// `../../shared-ui/foo` (verified). Enumerating depths up to 6 covers every realistic file location
// under `app/foundation/` and `app/shared-ui/`.
const blockLayerImportPatterns = (layer) => [
    `app/${layer}/**`,
    `../${layer}/**`,
    `../../${layer}/**`,
    `../../../${layer}/**`,
    `../../../../${layer}/**`,
    `../../../../../${layer}/**`,
    `../../../../../../${layer}/**`,
];
// The client is zoneless (`provideZonelessChangeDetection()` in app.config.ts). `NgZone` must never be
// reintroduced: under zoneless it is a `NoopNgZone`, so `run`/`runOutsideAngular`/`runGuarded` are no-ops
// that do NOT schedule change detection — using them creates silent stale-render bugs. This restriction is
// added to every `no-restricted-imports` block (the foundation/ and shared-ui/ blocks override the rule, so
// it must be repeated there to stay airtight).
const noNgZoneImport = {
    name: '@angular/core',
    importNames: ['NgZone'],
    message:
        'NgZone is forbidden: the client is zoneless (provideZonelessChangeDetection). Drive change detection with signals (signal/computed/effect), markForCheck, afterNextRender, or output emits — NgZone.run/runOutsideAngular are no-ops under zoneless.',
};
// Object copying goes through one choke point: `deepClone` / `cloneWith` / `hydrate` in
// `app/foundation/util/deep-clone.util`. Importing lodash's `cloneDeep` directly bypasses it, which splits the
// codebase between two spellings of the same operation and leaves nowhere to change the implementation later.
// The wrapper itself holds the single sanctioned import (line-level disabled). Like `noNgZoneImport`, this must
// be repeated in every `no-restricted-imports` block, because the foundation/ and shared-ui/ blocks override the
// rule rather than extending it. Companion to `localRules/prefer-deep-clone`.
const noDirectCloneDeepImports = [
    {
        name: 'lodash-es',
        importNames: ['cloneDeep', 'cloneDeepWith'],
        message: "Import deepClone / cloneWith / hydrate from 'app/foundation/util/deep-clone.util' instead of lodash cloneDeep.",
    },
    {
        name: 'lodash-es/cloneDeep',
        message: "Import deepClone / cloneWith / hydrate from 'app/foundation/util/deep-clone.util' instead of lodash cloneDeep.",
    },
];
// `@tumaet/apollon` (the root entry) is the whole modeling editor: React, Base UI, xyflow and the editor code, about 1.3 MB of raw JavaScript in
// the production bundle. A lazy route that imports a single value from the root loads all of it, even if it never shows a diagram. The model
// helpers that do not need the editor live in the light entry `@tumaet/apollon/model` (a few KB). Types are erased at build time and stay free
// to import from the root. Like `noNgZoneImport`, this must be repeated in every `no-restricted-imports` block of the application, because
// the foundation/ and shared-ui/ blocks override the rule rather than extending it.
const noApollonModelValueImports = {
    name: '@tumaet/apollon',
    importNames: ['importDiagram', 'readModelFromYDoc', 'writeModelToYDoc', 'hasModelInYDoc', 'clearModelFromYDoc', 'MODEL_DOC_ORIGIN'],
    allowTypeImports: true,
    message:
        "Import the model helpers from '@tumaet/apollon/model': the root entry '@tumaet/apollon' is the whole modeling editor and loads about 1.3 MB of JavaScript with the importing chunk.",
};
// The entry points of TUM AET UI: one per component, listed in the `exports` of the package. A bundler that splits code per file loads everything that a
// lazy route takes from the package root, so the client imports from the entry point of each component. Both rules follow the `exports`.
const tumAetUiEntryPoints = Object.keys(JSON.parse(readFileSync(resolve(import.meta.dirname, 'packages/tum-aet-ui/package.json'), 'utf8')).exports)
    .filter((subpath) => subpath !== '.')
    .map((subpath) => `@tumaet/ui-angular/${subpath.slice(2)}`);
const tumAetUiConsumerImportPatterns = [
    {
        regex: '^@tumaet/ui-angular$',
        message:
            'Import from the entry point of the component, for example @tumaet/ui-angular/button, not from the package root: the root pulls the code of every component into the bundle.',
    },
    {
        group: ['@tumaet/ui-angular/**', ...tumAetUiEntryPoints.map((entryPoint) => `!${entryPoint}`)],
        message: 'Import from an entry point of TUM AET UI (@tumaet/ui-angular/<component>), not from a package-internal path.',
    },
];
// The client modules, all of which use TUM AET UI and Tailwind. Bootstrap, ng-bootstrap and PrimeNG are removed from the
// client; every lock below is generated from this list and keeps them out:
// the Bootstrap class rules for external and inline templates, the Bootstrap classes built in code, and the
// ban on importing PrimeNG or ng-bootstrap. config/stylelint/stylelint.config.json names the same modules for SCSS, and
// the `migration-source-coverage` test keeps both lists and the `@source` entries of tailwind.css consistent.
const migratedModules = [
    'account',
    'admin',
    'aiworker',
    'assessment',
    'atlas',
    'calendar',
    'communication',
    'core',
    'course',
    'editor',
    'exam',
    'exercise',
    'fileupload',
    'foundation',
    'hyperion',
    'iris',
    'lecture',
    'localci',
    'localvc',
    'logos',
    'lti',
    'modeling',
    'notification',
    'plagiarism',
    'presentation',
    'programming',
    'quiz',
    'shared',
    'shared-ui',
    'sharing',
    'text',
    'tutorialgroup',
    'videosource',
];
// Components that are migrated although the rest of their module is not. They get the same locks as a module.
const migratedComponentFolders = [];
const migratedFolders = (extension) => [...migratedModules, ...migratedComponentFolders].map((folder) => `src/main/webapp/app/${folder}/**/*.${extension}`);
const legacyUiLibraryImportPattern = {
    group: ['primeng', 'primeng/**', '@ng-bootstrap/**', 'bootstrap', 'bootstrap/**'],
    message:
        'The client uses TUM AET UI (@tumaet/ui-angular) and Tailwind. PrimeNG, ng-bootstrap and Bootstrap are removed and must not be imported; host dialogs declaratively in tumaet-ui-dialog.',
};
export default tseslint.config(
    {
        ignores: [
            // Top-level directories
            '.cache/',
            '.git/',
            '.github/',
            '.gradle/',
            '.idea/',
            '.jhipster/',
            '.venv/',
            'bin/',
            'build/',
            'coverage/',
            'deployment/docker/',
            'docs/',
            'documentation/',
            'gradle/',
            'local/',
            'node/',
            'node_modules/',
            'config/openapi/',
            'out/',
            'config/pnpm/patches/',
            'repos/',
            'repos-download/',
            'supporting_scripts/',
            'target/',
            'templates/',
            'uploads/',
            // Source directories not containing Angular client code
            'src/main/generated/',
            'src/main/java/',
            'src/main/resources/',
            'src/test/java/',
            'src/test/playwright/',
            'src/test/resources/',
            'src/test/vitest/',
            'packages/tum-aet-ui/coverage/',
            'packages/tum-aet-ui/dist/',
            'packages/tum-aet-ui/dist-pack/',
            // Specific file exclusions within linted directories
            'src/main/webapp/app/openapi/**',
            'src/test/javascript/spec/stub.js',
            // Root-level config files (not part of the Angular client)
            '*.js',
            '*.mjs',
        ],
    },
    eslint.configs.recommended,
    ...storybookPlugin.configs['flat/recommended'],
    {
        files: ['packages/tum-aet-ui/**/*.mjs'],
        languageOptions: {
            globals: {
                clearTimeout: 'readonly',
                console: 'readonly',
                process: 'readonly',
                setTimeout: 'readonly',
            },
        },
    },
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                tsconfigRootDir: import.meta.dirname,
                project: [
                    './tsconfig.json',
                    './config/client/tsconfig.app.json',
                    './config/client/tsconfig.spec.json',
                    './packages/tum-aet-ui/tsconfig.lib.json',
                    './packages/tum-aet-ui/tsconfig.spec.json',
                    './packages/tum-aet-ui/.storybook/tsconfig.json',
                    './packages/tum-aet-ui/consumer/tsconfig.json',
                    './packages/tum-aet-ui/consumer/app/tsconfig.json',
                ],
            },
            globals: {
                NodeJS: 'readonly',
                navigator: 'readonly',
                document: 'readonly',
                window: 'readonly',
                setTimeout: 'readonly',
                setInterval: 'readonly',
                clearTimeout: 'readonly',
                clearInterval: 'readonly',
                sessionStorage: 'readonly',
                localStorage: 'readonly',
                addEventListener: 'readonly',
                Image: 'readonly',
                module: 'readonly',
                require: 'readonly',
                process: 'readonly',
                location: 'readonly',
                self: 'readonly',
                history: 'readonly',
                confirm: 'readonly',
                plugin: 'readonly',
                requestAnimationFrame: 'readonly',
                alert: 'readonly',
                Buffer: 'readonly',
                getComputedStyle: 'readonly',
                MarkdownIt: 'readonly',
            },
        },
        plugins: {
            '@typescript-eslint': tsPlugin,
            '@angular-eslint': angularPlugin,
            prettier: prettierPlugin,
            localRules: localRulesPlugin,
        },
        // TODO: adapt the rules of the newest jhipster version, e.g. no-inferrable-types, restrict-plus-operands, etc.
        rules: {
            ...prettierPlugin.configs.recommended.rules,
            ...tsPlugin.configs.recommended.rules,
            // angular-eslint 22 removed `configs` from `@angular-eslint/eslint-plugin`; the recommended
            // rules now live in the `angular-eslint` meta-package's flat `tsRecommended` config array
            // (an array of flat-config objects, only one of which carries `rules`).
            ...Object.assign({}, ...angular.configs.tsRecommended.map((c) => c.rules ?? {})),
            '@typescript-eslint/no-non-null-assertion': 'off',
            '@typescript-eslint/no-unsafe-return': 'off',
            '@typescript-eslint/no-unsafe-member-access': 'off',
            '@typescript-eslint/no-unsafe-call': 'off',
            '@typescript-eslint/no-floating-promises': 'off',
            '@typescript-eslint/no-unsafe-assignment': 'off',
            '@angular-eslint/no-output-on-prefix': 'off',
            // Class fields initialize in declaration order, so a getter or method that reads an injected service from
            // an earlier field initializer sees `undefined` if the inject() field comes later. TypeScript only catches
            // the direct reference, not the one behind a getter or method, so keep every inject() field first.
            '@angular-eslint/inject-at-top': 'error',
            // Angular 22's @Service() is the shorthand for @Injectable({ providedIn: 'root' }) and what `ng generate
            // service` emits. It also rejects constructor injection at compile time, which matches prefer-inject.
            // Services with any other provider metadata keep @Injectable. Autofixable, with one trap: a @Pipe that is
            // also injected as a service is still reported, but @Service() cannot share a class with another Angular
            // decorator, so the autofix breaks the build with NG1006. Only the AOT compiler (`ng build`) catches that;
            // Vitest compiles JIT and tsc ignores decorators. Keep @Injectable there with a line-level disable and do
            // not run the autofix on it.
            '@angular-eslint/prefer-service-decorator': 'error',
            // A computed(), linkedSignal(), effect() or afterRenderEffect() that reads no signal never re-runs: either a
            // signal read is missing (`count` instead of `count()`), or the value is a constant and should be a field.
            // Version 22.5.0 crashes ("config.args is not iterable") on a bare call named like an Object.prototype
            // member, such as a destructured Signal Forms `valueOf`; config/pnpm/patches/@angular-eslint__eslint-plugin@22.5.0.patch
            // backports the upstream fix until a release contains it.
            '@angular-eslint/reactive-context-must-read-signal': 'error',
            // A computed() whose function returns nothing is always undefined.
            '@angular-eslint/computed-must-return': 'error',
            // takeUntilDestroyed() without a DestroyRef throws NG0203 outside an injection context (ngOnInit, methods).
            '@angular-eslint/no-implicit-take-until-destroyed': 'error',
            '@angular-eslint/no-uncalled-signals': 'error',
            '@angular-eslint/no-async-lifecycle-method': 'error',
            '@angular-eslint/no-duplicates-in-metadata-arrays': 'error',
            // Production client code must not silently disable the type checker. `@ts-ignore` is banned outright
            // (convert to `@ts-expect-error` with a description, or fix the underlying type); `@ts-expect-error`
            // is allowed only with a description. Specs relax this to 'off' in the test-file block below.
            '@typescript-eslint/ban-ts-comment': 'error',
            '@typescript-eslint/no-deprecated': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/no-non-null-asserted-optional-chain': 'warn',
            '@typescript-eslint/no-explicit-any': 'off',
            '@typescript-eslint/no-unused-vars': [
                'error',
                {
                    vars: 'all',
                    varsIgnorePattern: '^_', // Ignore variables prefixed with `_`
                    args: 'none',
                    ignoreRestSiblings: true,
                    caughtErrors: 'none',
                },
            ],
            // The core `no-redeclare` rule does not understand TypeScript function overloads and reports every
            // signature after the first as a redeclaration (e.g. the `hydrate` overloads in deep-clone.util.ts).
            // Swap in the typescript-eslint extension, which is overload-aware and otherwise equivalent; genuine
            // redeclarations remain errors, and tsc catches them independently.
            'no-redeclare': 'off',
            '@typescript-eslint/no-redeclare': 'error',
            'no-unused-private-class-members': 'error',
            'no-case-declarations': 'off',
            'prefer-const': 'warn',
            'prefer-spread': 'warn',
            'no-var': 'error',
            'no-prototype-builtins': 'off',
            'sort-imports': [
                'error',
                {
                    ignoreDeclarationSort: true,
                },
            ],
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        {
                            name: 'dayjs',
                            message: "Please import from 'dayjs/esm' instead.",
                        },
                        {
                            name: 'lodash',
                            message: "Please import from 'lodash-es' instead.",
                        },
                        noNgZoneImport,
                        noApollonModelValueImports,
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: tumAetUiConsumerImportPatterns,
                },
            ],
            'no-restricted-syntax': [
                'error',
                {
                    // Monaco's editor.addCommand registers a command in the process-global CommandsRegistry whose handler
                    // retains the editor; it is not released on editor.dispose(), which leaks the editor and its entire
                    // DOM subtree (see PR #12976). Use editor.addAction, which returns a disposable that must be stored
                    // and disposed on destroy.
                    selector: "CallExpression[callee.property.name='addCommand']",
                    message:
                        'Do not use editor.addCommand (it leaks the editor via Monaco’s process-global command registry). Use editor.addAction, store the returned disposable, and dispose it on destroy.',
                },
            ],
            'localRules/require-signal-reference-ngb-modal-input': 'error',
            'localRules/enforce-signal-apis': 'error',
            'localRules/enforce-cleanup-on-destroy': 'warn',
            'localRules/no-navigation-in-effect': 'error',
            'localRules/no-as-unknown-cast': 'error',
            'localRules/no-as-any-cast': 'error',
            // Registered here as well as for .html, so inline `template:` strings are covered too.
            'localRules/require-chart-accessible-name': 'error',
        },
    },
    // Force JSON.parse results to carry an explicit type. `JSON.parse` is declared to return `any`, which
    // silently disables type checking on everything derived from it — a typo like `obj.colour` compiles and
    // yields `undefined` at runtime. Route parsing through `parseJson<T>()` (app/foundation/util/json.util),
    // whose generic defaults to `unknown`, so a caller cannot touch the result's properties without stating
    // the expected shape. All production call sites route through the wrapper, so this is an `error`. The
    // wrapper itself holds the single sanctioned `JSON.parse` (line-level disabled), and test code may parse
    // fixtures freely (specs excluded below).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-restricted-properties': [
                'error',
                {
                    object: 'JSON',
                    property: 'parse',
                    message:
                        'Avoid untyped JSON.parse(): its result is `any`, so property access is unchecked. Use parseJson<T>() from app/foundation/util/json.util and pass the expected type.',
                },
            ],
            // Template literals must not stringify `any`, objects, nullish, etc. (which produce "[object Object]" /
            // "undefined"). Numbers are allowed (allowNumber default); everything else must be converted explicitly.
            '@typescript-eslint/restrict-template-expressions': 'error',
        },
    },
    // Forbid `any` in all production client code. `any` opts a value out of type checking entirely, so it is
    // banned across `src/main/webapp` (production). Specs may still use `any` for mocks/fixtures (excluded below).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            '@typescript-eslint/no-explicit-any': 'error',
        },
    },
    // Curb unsafe `as` casts in production code without banning `as` outright (downcasts the type system cannot
    // infer — e.g. `event.target as HTMLInputElement` — remain the honest tool and stay allowed). Two targeted rules:
    //   - `no-unnecessary-type-assertion`: removes redundant casts that do not change the type (noise, and they
    //     silently hide the day the underlying type shifts). Auto-fixable.
    //   - `consistent-type-assertions` with `objectLiteralTypeAssertions: 'never'`: forbids `{ … } as T` on object
    //     literals, which bypasses excess-property checking. Use `satisfies T` (verifies shape, keeps the inferred
    //     type) or a type annotation instead. `assertionStyle: 'as'` keeps `as const` and ordinary downcasts legal.
    // The stronger `as any` / `as unknown` bans live in the localRules block above. Specs may cast freely (excluded).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            '@typescript-eslint/no-unnecessary-type-assertion': 'error',
            '@typescript-eslint/consistent-type-assertions': ['error', { assertionStyle: 'as', objectLiteralTypeAssertions: 'never' }],
        },
    },
    // Keep diagnostics out of the console and off `globalThis` in production code.
    //   - `no-console`: bare `console.*` is invisible in production; route real diagnostics to the error reporter
    //     (`captureException` from `app/core/error-reporting/capture-exception`). Specs may log freely (excluded below).
    //   - `no-restricted-globals` on `globalThis`: prod is already `globalThis`-free; this is a regression guard.
    //     Use `window` for browser globals and the error reporter for diagnostics. It is a separate rule from the Monaco
    //     `no-restricted-syntax` block above, so the two do not clobber each other. Specs use `globalThis` for
    //     mocking (excluded below).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-console': 'error',
            'no-restricted-globals': [
                'error',
                {
                    name: 'globalThis',
                    message:
                        'Do not use globalThis in production. Use `window` for browser globals, and captureException from the error reporter for diagnostics instead of globalThis.console.',
                },
            ],
        },
    },
    // Require every Promise to be handled in production code. A floating Promise silently swallows rejections
    // (unhandled errors) and hides ordering bugs. Handle it: `await` it (in an async function, typically with a
    // try/catch that routes to `onError`), attach `.then(...)/.catch(...)`, or mark it deliberately fire-and-forget
    // with the `void` operator (`ignoreVoid: true`). `ignoreIIFE` allows `(async () => { … })()`. This overrides the
    // `'off'` default above for production code; specs may float promises for brevity (excluded below).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            '@typescript-eslint/no-floating-promises': ['error', { ignoreVoid: true, ignoreIIFE: true }],
        },
    },
    // Type-safety ratchet for production code (specs excluded below): catch real bug classes the compiler's
    // `strictNullChecks`/`noImplicitAny` miss. `restrict-plus-operands` rejects `+` on mismatched/uncertain
    // operand types (silent string/number coercion); `no-base-to-string` rejects stringifying a value whose
    // `toString()` yields `"[object Object]"` (template literals, `String(x)`, concatenation). Both preserve
    // behavior once fixed — they surface where a conversion was accidental. Companion to `restrict-template-expressions`.
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            '@typescript-eslint/restrict-plus-operands': 'error',
            '@typescript-eslint/no-base-to-string': 'error',
        },
    },
    // Ban `ngOnChanges` across application, package, and test code. Angular 21 still calls the hook for signal
    // inputs, so this is a consistency rule. A genuinely unavoidable previous-value or lifecycle-ordering case
    // requires a justified line-level disable; see the client-development guide.
    {
        files: ['src/main/webapp/app/**/*.ts', 'packages/tum-aet-ui/src/lib/**/*.ts', 'src/test/javascript/**/*.ts'],
        rules: {
            'localRules/prefer-signal-reactivity-over-ngonchanges': 'error',
        },
    },
    // Zoneless correctness: a mutable component/directive field that the template reads must be a signal,
    // otherwise reassigning it outside a synchronous render / event handler (subscribe, setTimeout, a helper
    // reached from one, …) schedules no change detection and the view silently goes stale. Fields the template
    // never reads, injected services, and constants are exempt; genuine [(ngModel)]/[(x)] two-way targets that
    // cannot be signals use a justified line-level disable. Full rationale:
    // documentation/docs/developer/guidelines/client-development.mdx ("Zoneless change detection & signal-based state").
    {
        files: ['src/main/webapp/app/**/*.ts', 'packages/tum-aet-ui/src/lib/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'localRules/prefer-signal-template-state': 'error',
        },
    },
    // Copy objects with deepClone, never with object spread, Object.assign or structuredClone. All three
    // alternatives are wrong in ways the type checker cannot see: structuredClone drops prototypes, so a cloned
    // dayjs date loses its methods while isDayjs() still returns true (the bug behind PR #11910); spread and
    // Object.assign copy one level, so nested objects and arrays stay shared and editing the copy edits the
    // original. Object.assign(target, source) additionally mutates in place, which emits no signal notification
    // because a signal compares with Object.is. Use deepClone(x), cloneWith(x, { … }) or hydrate(new X(), dto)
    // from app/foundation/util/deep-clone.util. Array spread, call spread and object rest destructuring are
    // unaffected; specs may build fixtures freely (excluded below). Companion to the `no-restricted-imports`
    // entry that keeps lodash cloneDeep behind the same wrapper. Full rationale:
    // documentation/docs/developer/guidelines/client-development.mdx ("Cloning objects").
    {
        files: ['src/main/webapp/app/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'localRules/prefer-deep-clone': 'error',
        },
    },
    // Route guards and resolvers use router-native redirects, never by calling Router.navigate() or
    // navigateByUrl(). Navigating from inside a guard or resolver cancels the running navigation on the spot and starts
    // a new one: that loses the original replaceUrl / skipLocationChange (a guarded URL opened directly stays in the
    // history, so Back redirects forward again), makes a caller awaiting the original navigation receive false, and
    // still navigates when another guard on the route rejects it. A `return false` or `EMPTY` after the call changes
    // nothing. A guard returns or emits router.createUrlTree(...) or a RedirectCommand; a resolver may also throw a
    // RedirectCommand from inside an RxJS operator or a promise callback. Throwing from a guard bypasses guard-result
    // ordering, so guards must return or emit their redirect. The rule follows a guard
    // into the helper methods and same-file functions it reaches, but not into injected services. Full rationale:
    // documentation/docs/developer/guidelines/client-development.mdx ("Redirecting from guards and resolvers").
    {
        files: ['src/main/webapp/**/*.ts'],
        rules: {
            'localRules/no-navigation-in-guard-or-resolver': 'error',
        },
    },
    // `ComponentRef.setInput(name: string, value: unknown)` is not type checked: a misspelled input only logs NG0303 at runtime. Declare
    // the component in a template (a `@switch` for a closed set) or use the typed `setInputs` helper. Specs set inputs on fixtures and
    // stay exempt. Rationale: documentation/docs/developer/guidelines/client-development.mdx ("Setting inputs of a component created in code").
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-aet-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'localRules/no-component-ref-set-input': 'error',
        },
    },
    // Module-boundary rules: enforce the foundation ← shared-ui ← editor layering.
    // foundation/ is the base layer (no DOM/UI), shared-ui/ holds generic UI primitives,
    // editor/ holds the code/markdown editor stacks. The intent:
    //   - foundation may not import from shared-ui or editor
    //   - shared-ui may not import from editor
    //   - editor may import from foundation and shared-ui (e.g. ColorSelector inside the markdown toolbar)
    {
        files: ['src/main/webapp/app/foundation/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        { name: 'dayjs', message: "Please import from 'dayjs/esm' instead." },
                        { name: 'lodash', message: "Please import from 'lodash-es' instead." },
                        noNgZoneImport,
                        noApollonModelValueImports,
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: [
                        ...tumAetUiConsumerImportPatterns,
                        {
                            // Block both absolute (app/shared-ui/**) and relative (../shared-ui, ../../shared-ui, …) imports
                            // so the layer cannot be bypassed with a relative path.
                            group: blockLayerImportPatterns('shared-ui'),
                            message:
                                'app/foundation/ must not depend on app/shared-ui/. foundation/ is the base infrastructure layer (no DOM/UI). If a UI primitive is needed here, the file probably belongs in app/shared-ui/ instead.',
                        },
                        {
                            group: blockLayerImportPatterns('editor'),
                            message:
                                'app/foundation/ must not depend on app/editor/. foundation/ is the base infrastructure layer. Extract the editor-side dependency to a neutral constant or move the consuming file into app/editor/.',
                        },
                        legacyUiLibraryImportPattern,
                    ],
                },
            ],
        },
    },
    {
        files: ['src/main/webapp/app/shared-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        { name: 'dayjs', message: "Please import from 'dayjs/esm' instead." },
                        { name: 'lodash', message: "Please import from 'lodash-es' instead." },
                        noNgZoneImport,
                        noApollonModelValueImports,
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: [
                        ...tumAetUiConsumerImportPatterns,
                        {
                            // Block both absolute (app/editor/**) and relative (../editor, ../../editor, …) imports.
                            group: blockLayerImportPatterns('editor'),
                            message:
                                'app/shared-ui/ must not depend on app/editor/. shared-ui/ holds generic UI primitives; the editor stack is specialised and sits above shared-ui/.',
                        },
                    ],
                },
            ],
        },
    },
    {
        files: ['src/test/javascript/**', 'src/main/webapp/app/**/*.spec.ts', 'packages/tum-aet-ui/src/**/*.spec.ts'],
        plugins: {
            localRules: localRulesPlugin,
        },
        rules: {
            // Legacy Angular decorators (@Input/@Output/@ViewChild/@ContentChild/...) are banned in test code too —
            // test helpers, stubs, and mocks must use signal-based APIs (input()/output()/viewChild()/contentChild()).
            'localRules/enforce-signal-apis': 'error',
            '@typescript-eslint/no-deprecated': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/ban-ts-comment': 'off',
            '@typescript-eslint/no-require-imports': 'off',
            '@typescript-eslint/no-unused-vars': [
                'warn',
                {
                    vars: 'all',
                    varsIgnorePattern: '^_',
                    args: 'none',
                    ignoreRestSiblings: true,
                    caughtErrors: 'none',
                },
            ],
            'no-unused-private-class-members': 'error',
            'no-unused-vars': 'off',
            'no-undef': 'off',
        },
    },
    {
        // The client test infrastructure under src/test/javascript (helpers, stubs, mocks) is TypeScript and must be
        // parsed so ESLint actually lints it — otherwise files match no parser config and are silently "File ignored".
        // Together with the enforce-signal-apis rule in the block above and `pnpm lint` targeting src/test/javascript,
        // this makes the legacy-decorator ban real for test code. Rules stay relaxed as befits test doubles.
        files: ['src/test/javascript/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                tsconfigRootDir: import.meta.dirname,
                project: ['./config/client/tsconfig.spec.json'],
            },
        },
        plugins: {
            '@typescript-eslint': tsPlugin,
        },
        rules: {
            '@typescript-eslint/no-unused-vars': 'off',
            'no-unused-vars': 'off',
            'no-undef': 'off',
        },
    },
    {
        files: ['packages/tum-aet-ui/src/lib/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        {
                            name: 'dayjs',
                            message: "Please import from 'dayjs/esm' instead.",
                        },
                        {
                            name: 'lodash',
                            message: "Please import from 'lodash-es' instead.",
                        },
                        noNgZoneImport,
                        {
                            name: '@tumaet/ui-angular',
                            message: 'Inside TUM AET UI, import from a relative path, not the package entry point.',
                        },
                    ],
                    patterns: [
                        {
                            group: ['app', 'app/**', 'test', 'test/**', '!storybook/test', 'primeng', 'primeng/**', '@ng-bootstrap/**', 'bootstrap', 'bootstrap/**'],
                            message: 'TUM AET UI must not depend on Artemis or its application UI frameworks. Move host-specific code to app/shared-ui/tum-aet-ui-integration.',
                        },
                        {
                            group: ['@tumaet/ui-angular/**'],
                            message: 'Inside TUM AET UI, import from a relative path, not the entry point of a component.',
                        },
                    ],
                },
            ],
            '@angular-eslint/directive-selector': [
                'error',
                {
                    type: 'attribute',
                    prefix: 'tumAetUi',
                    style: 'camelCase',
                },
            ],
            '@angular-eslint/component-selector': [
                'error',
                {
                    type: 'element',
                    prefix: 'tumaet-ui',
                    style: 'kebab-case',
                },
            ],
        },
    },
    {
        // Attribute-selector components preserve native element semantics while owning a template
        // or component-scoped styles, so the element-selector convention does not apply.
        files: [
            'packages/tum-aet-ui/src/lib/button/tumaet-ui-button.directive.ts',
            'packages/tum-aet-ui/src/lib/chart/tumaet-ui-chart-axes.component.ts',
            'packages/tum-aet-ui/src/lib/table-directive/tumaet-ui-table-sortable-column.component.ts',
        ],
        rules: {
            '@angular-eslint/component-selector': 'off',
        },
    },
    {
        files: ['src/main/webapp/**/*.html', 'packages/tum-aet-ui/**/*.html'],
        languageOptions: {
            parser: angularTemplateParser,
        },
        extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
        plugins: {
            '@angular-eslint': angularPlugin,
            prettier: prettierPlugin,
        },
        rules: {
            'prettier/prettier': ['error', { parser: 'angular' }],
            '@angular-eslint/template/click-events-have-key-events': 'error',
            '@angular-eslint/template/interactive-supports-focus': 'error',
            '@angular-eslint/template/button-has-type': 'error',
            '@angular-eslint/template/label-has-associated-control': 'off',
            '@angular-eslint/template/alt-text': 'error',
            '@angular-eslint/template/no-positive-tabindex': 'error',
            '@angular-eslint/template/elements-content': 'off',
            '@angular-eslint/template/prefer-control-flow': 'error',
            '@angular-eslint/template/prefer-self-closing-tags': 'error',
            // A @switch without @default silently renders nothing for an unmatched value. Write `@default never;`
            // when the cases cover the whole union or enum (the template type check then fails when a member is
            // added), otherwise `@default {}` to state that nothing is rendered on purpose.
            '@angular-eslint/template/require-switch-default': 'error',
            // Setting outerHTML replaces the node Angular is bound to, so the next update fails. Use [innerHTML].
            '@angular-eslint/template/no-outerhtml': 'error',
            // Prefer [style], [style.prop] and [style.prop.unit] bindings (or a static style attribute) over NgStyle,
            // as the Angular style guide recommends. A bound object is compared by reference, so replace it rather
            // than mutating it, and write a unit suffix such as `top.px` as its own [style.top.px] binding.
            '@angular-eslint/template/prefer-style-binding': 'error',
            '@angular-eslint/template/no-any': 'error',
        },
    },
    {
        files: ['packages/tum-aet-ui/src/lib/**/*.html'],
        rules: {
            '@angular-eslint/template/click-events-have-key-events': 'error',
            '@angular-eslint/template/interactive-supports-focus': 'error',
            '@angular-eslint/template/label-has-associated-control': 'error',
            '@angular-eslint/template/alt-text': 'error',
            '@angular-eslint/template/elements-content': 'error',
        },
    },
    {
        files: ['src/main/webapp/app/exercise/team/**/*.html'],
        rules: {
            '@angular-eslint/template/label-has-associated-control': 'error',
        },
    },
    {
        // These composite widgets manage option focus through aria-activedescendant.
        files: ['packages/tum-aet-ui/src/lib/autocomplete/tumaet-ui-autocomplete.component.html', 'packages/tum-aet-ui/src/lib/select/tumaet-ui-select.component.html'],
        rules: {
            '@angular-eslint/template/click-events-have-key-events': 'off',
            '@angular-eslint/template/interactive-supports-focus': 'off',
        },
    },
    {
        // Product action semantics apply to external and extracted inline application templates.
        files: ['src/main/webapp/app/**/*.html', 'src/main/webapp/app/**/*.button-intent'],
        languageOptions: { parser: angularTemplateParser },
        plugins: { localRules: localRulesPlugin },
        rules: { 'localRules/enforce-button-intent': 'error' },
    },
    {
        files: ['src/main/webapp/app/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        processor: buttonInlineTemplates,
    },
    {
        // Forbid raw Tailwind color palette classes (e.g. text-green-500) and root classes of the removed PrimeNG
        // library (e.g. class="p-button") in ALL client templates: use semantic tokens and TUM AET UI components
        // instead. The stylelint
        // hex/--bs- guard (config/stylelint/stylelint.config.json) is scoped per migrated module. See client-development.mdx (### Styling).
        files: ['src/main/webapp/app/**/*.html', 'packages/tum-aet-ui/src/lib/**/*.html'],
        languageOptions: {
            parser: angularTemplateParser,
        },
        plugins: {
            localRules: localRulesPlugin,
        },
        rules: {
            'localRules/no-raw-tailwind-color-palette': 'error',
            'localRules/no-primeng-component-classes': 'error',
            'localRules/require-chart-accessible-name': 'error',
            // A property binding is re-evaluated on every change-detection pass, so `.bind()` there hands the
            // consumer a new function identity every pass. Applies to all templates for the same reason as the two
            // rules above: change detection works the same way everywhere.
            'localRules/no-bind-in-template-binding': 'error',
        },
    },
    {
        // Regression lock: Bootstrap is removed from the client, so its CSS classes are forbidden in the templates of
        // these modules. A new module joins through `migratedModules`. See client-development.mdx
        // (### Styling).
        files: [
            'src/main/webapp/app/shared-ui/form/title-channel-name/**/*.html',
            'src/main/webapp/app/shared-ui/user-import/**/*.html',
            'src/main/webapp/app/shared-ui/confirm-entity-name/**/*.html',
            'src/main/webapp/app/shared-ui/delete-dialog/**/*.html',
            'src/main/webapp/app/shared-ui/profile-picture/**/*.html',
            'src/main/webapp/app/shared-ui/components/buttons/copy-to-clipboard-button/**/*.html',
            'src/main/webapp/app/quiz/manage/apollon-diagrams/**/*.html',
            'src/main/webapp/app/quiz/manage/question-reevaluation-actions/**/*.html',
            'src/main/webapp/app/quiz/manage/statistics/question-statistics-chart/**/*.html',
            'packages/tum-aet-ui/src/lib/**/*.html',
            'src/main/webapp/app/quiz/manage/detail/**/*.html',
            'src/main/webapp/app/quiz/manage/manage-buttons/**/*.html',
            'src/main/webapp/app/quiz/manage/lifecyle-buttons/**/*.html',
            'src/main/webapp/app/shared-ui/table-view/**/*.html',
            'src/main/webapp/app/shared-ui/components/buttons/button/**/*.html',
            'src/main/webapp/app/shared-ui/components/resizable-panels/**/*.html',
            'src/main/webapp/app/shared-ui/components/buttons/confirm-autofocus-button/**/*.html',
            'src/main/webapp/app/shared-ui/loading-indicator-overlay/**/*.html',
            'src/main/webapp/app/shared-ui/loading-indicator-container/**/*.html',
            'src/main/webapp/app/shared-ui/components/checklist-check/**/*.html',
            'src/main/webapp/app/shared-ui/information-box/**/*.html',
            'src/main/webapp/app/shared-ui/detail-overview-list/detail-overview-list.component.html',
            // The application shell itself; its providers in app.config.ts and app.main.ts follow with the last module.
            'src/main/webapp/app/app.component.html',
            ...migratedFolders('html'),
        ],
        languageOptions: {
            parser: angularTemplateParser,
        },
        plugins: {
            localRules: localRulesPlugin,
        },
        rules: {
            'localRules/no-bootstrap-classes': 'error',
        },
    },
    {
        // The same locks for `template:` strings: button-inline-templates.mjs hands every inline template to ESLint a second
        // time as `.lock-template`. Only the migrated modules are listed, so no other module is affected.
        files: migratedFolders('lock-template'),
        languageOptions: {
            parser: angularTemplateParser,
        },
        plugins: {
            localRules: localRulesPlugin,
        },
        rules: {
            'localRules/no-bootstrap-classes': 'error',
            'localRules/no-primeng-component-classes': 'error',
            'localRules/no-raw-tailwind-color-palette': 'error',
        },
    },
    {
        // Bootstrap class names that code builds, keeps in a map or looks up in the DOM: the template rules cannot see them.
        files: migratedFolders('ts'),
        ignores: ['**/*.spec.ts'],
        plugins: {
            localRules: localRulesPlugin,
        },
        rules: {
            'localRules/no-bootstrap-classes-in-code': 'error',
        },
    },
    // The client uses TUM AET UI and Tailwind: the removed PrimeNG, ng-bootstrap and Bootstrap may not be imported anywhere in these modules, including specs.
    // Like the other `no-restricted-imports` blocks, this one overrides the rule, so the shared restrictions are repeated.
    {
        files: [
            ...migratedFolders('ts').filter((folder) => !folder.includes('/foundation/')),
            'src/main/webapp/app/foundation/**/*.spec.ts',
            'src/main/webapp/app/atlas/shared/competency-selection/**/*.ts',
            'src/main/webapp/app/shared-ui/form/title-channel-name/**/*.ts',
        ],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    paths: [
                        { name: 'dayjs', message: "Please import from 'dayjs/esm' instead." },
                        { name: 'lodash', message: "Please import from 'lodash-es' instead." },
                        noNgZoneImport,
                        noApollonModelValueImports,
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: [...tumAetUiConsumerImportPatterns, legacyUiLibraryImportPattern],
                },
            ],
        },
    },
);
