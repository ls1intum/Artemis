import tsPlugin from '@typescript-eslint/eslint-plugin';
import angularPlugin from '@angular-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import storybookPlugin from 'eslint-plugin-storybook';
import typescriptParser from '@typescript-eslint/parser';
import angularTemplateParser from '@angular-eslint/template-parser';
import angular from 'angular-eslint';
import tseslint from 'typescript-eslint';
import eslint from '@eslint/js';
import localRulesPlugin from './rules/index.mjs';

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
const tumUiConsumerImportPatterns = [
    {
        group: ['@tumaet/ui-angular/**'],
        message: 'Import TUM UI symbols from the @tumaet/ui-angular public entry point, not a package-internal path.',
    },
    {
        group: ['app/shared-ui/tum-ui/**'],
        message: 'Import TUM UI symbols from the @tumaet/ui-angular public entry point.',
    },
];
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
            'build/',
            'coverage/',
            'docker/',
            'docs/',
            'documentation/',
            'gradle/',
            'local/',
            'node/',
            'node_modules/',
            'openapi/',
            'out/',
            'patches/',
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
            'packages/tum-ui/coverage/',
            'packages/tum-ui/dist/',
            'packages/tum-ui/dist-pack/',
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
        files: ['packages/tum-ui/**/*.mjs'],
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                tsconfigRootDir: import.meta.dirname,
                project: [
                    './tsconfig.json',
                    './tsconfig.app.json',
                    './tsconfig.spec.json',
                    './packages/tum-ui/tsconfig.lib.json',
                    './packages/tum-ui/tsconfig.spec.json',
                    './packages/tum-ui/.storybook/tsconfig.json',
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
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: tumUiConsumerImportPatterns,
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            '@typescript-eslint/no-unnecessary-type-assertion': 'error',
            '@typescript-eslint/consistent-type-assertions': ['error', { assertionStyle: 'as', objectLiteralTypeAssertions: 'never' }],
        },
    },
    // Keep diagnostics out of the console and off `globalThis` in production code.
    //   - `no-console`: bare `console.*` is invisible in production; route real diagnostics to Sentry
    //     (`captureException` from `@sentry/angular`). Specs may log freely (excluded below).
    //   - `no-restricted-globals` on `globalThis`: prod is already `globalThis`-free; this is a regression guard.
    //     Use `window` for browser globals and Sentry for diagnostics. It is a separate rule from the Monaco
    //     `no-restricted-syntax` block above, so the two do not clobber each other. Specs use `globalThis` for
    //     mocking (excluded below).
    {
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-console': 'error',
            'no-restricted-globals': [
                'error',
                {
                    name: 'globalThis',
                    message: 'Do not use globalThis in production. Use `window` for browser globals, and Sentry captureException for diagnostics instead of globalThis.console.',
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
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
        files: ['src/main/webapp/**/*.ts', 'packages/tum-ui/**/*.ts'],
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
        files: ['src/main/webapp/app/**/*.ts', 'packages/tum-ui/src/lib/**/*.ts', 'src/test/javascript/**/*.ts'],
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
        files: ['src/main/webapp/app/**/*.ts', 'packages/tum-ui/src/lib/**/*.ts'],
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
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: [
                        ...tumUiConsumerImportPatterns,
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
                        ...noDirectCloneDeepImports,
                    ],
                    patterns: [
                        ...tumUiConsumerImportPatterns,
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
        files: ['src/test/javascript/**', 'src/main/webapp/app/**/*.spec.ts', 'packages/tum-ui/src/**/*.spec.ts'],
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
                project: ['./tsconfig.spec.json'],
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
        files: ['packages/tum-ui/src/lib/**/*.ts'],
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
                    ],
                    patterns: [
                        {
                            group: ['app', 'app/**', 'test', 'test/**', '!storybook/test', 'primeng', 'primeng/**', '@ng-bootstrap/**', 'bootstrap', 'bootstrap/**'],
                            message: 'TUM UI must not depend on Artemis or its application UI frameworks. Move host-specific code to app/shared-ui/tum-ui-integration.',
                        },
                    ],
                },
            ],
            '@angular-eslint/directive-selector': [
                'error',
                {
                    type: 'attribute',
                    prefix: 'tumUi',
                    style: 'camelCase',
                },
            ],
            '@angular-eslint/component-selector': [
                'error',
                {
                    type: 'element',
                    prefix: 'tum-ui',
                    style: 'kebab-case',
                },
            ],
        },
    },
    {
        // Attribute-selector components preserve native element semantics while owning a template
        // or component-scoped styles, so the element-selector convention does not apply.
        files: [
            'packages/tum-ui/src/lib/button/tum-ui-button.directive.ts',
            'packages/tum-ui/src/lib/chart/tum-ui-chart-axes.component.ts',
            'packages/tum-ui/src/lib/table-directive/tum-ui-table-sortable-column.component.ts',
        ],
        rules: {
            '@angular-eslint/component-selector': 'off',
        },
    },
    {
        files: ['src/main/webapp/**/*.html', 'packages/tum-ui/**/*.html'],
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
            '@angular-eslint/template/click-events-have-key-events': 'off',
            '@angular-eslint/template/interactive-supports-focus': 'off',
            '@angular-eslint/template/label-has-associated-control': 'off',
            '@angular-eslint/template/alt-text': 'off',
            '@angular-eslint/template/elements-content': 'off',
            '@angular-eslint/template/prefer-control-flow': 'error',
            '@angular-eslint/template/prefer-self-closing-tags': 'error',
        },
    },
    {
        files: ['packages/tum-ui/src/lib/**/*.html'],
        rules: {
            '@angular-eslint/template/click-events-have-key-events': 'error',
            '@angular-eslint/template/interactive-supports-focus': 'error',
            '@angular-eslint/template/label-has-associated-control': 'error',
            '@angular-eslint/template/alt-text': 'error',
            '@angular-eslint/template/elements-content': 'error',
        },
    },
    {
        // These composite widgets manage option focus through aria-activedescendant.
        files: ['packages/tum-ui/src/lib/autocomplete/tum-ui-autocomplete.component.html', 'packages/tum-ui/src/lib/select/tum-ui-select.component.html'],
        rules: {
            '@angular-eslint/template/click-events-have-key-events': 'off',
            '@angular-eslint/template/interactive-supports-focus': 'off',
        },
    },
    {
        // Forbid raw Tailwind color palette classes (e.g. text-green-500) and hand-written PrimeNG component root
        // classes (e.g. class="p-button") in ALL client templates: Tailwind + PrimeNG are loaded app-wide, so both
        // are wrong everywhere — use semantic brand tokens and real PrimeNG components instead. The stylelint
        // hex/--bs- guard (.stylelintrc.json) is scoped per migrated module. See client-development.mdx (### Styling).
        files: ['src/main/webapp/app/**/*.html', 'packages/tum-ui/src/lib/**/*.html'],
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
        // Regression lock: these modules are fully migrated to Tailwind + PrimeNG, so Bootstrap CSS classes are
        // forbidden in their templates. Add each module here once it is fully Bootstrap-free. See client-development.mdx
        // (### Styling).
        files: [
            'src/main/webapp/app/admin/**/*.html',
            'src/main/webapp/app/course/request/**/*.html',
            'src/main/webapp/app/exercise/result/**/*.html',
            'src/main/webapp/app/iris/manage/settings/**/*.html',
            'src/main/webapp/app/shared-ui/date-time-picker/**/*.html',
            'src/main/webapp/app/atlas/shared/standardized-competencies/**/*.html',
            'src/main/webapp/app/localci/build-queue/**/*.html',
            'src/main/webapp/app/shared-ui/user-import/**/*.html',
            'src/main/webapp/app/shared-ui/user-registration-modal/**/*.html',
            // Admin-reachable global shell + delete-dialog chain (rendered on every admin page / during admin deletes).
            'src/main/webapp/app/shared-ui/confirm-entity-name/**/*.html',
            'src/main/webapp/app/shared-ui/delete-dialog/**/*.html',
            'src/main/webapp/app/core/alert/**/*.html',
            'src/main/webapp/app/core/layouts/footer/**/*.html',
            // Only the modal shell is migrated; its search subcomponents go with the navbar/search follow-up.
            'src/main/webapp/app/core/navbar/global-search/components/modal/global-search-modal.component.html',
            'src/main/webapp/app/course/overview/setup-passkey-modal/**/*.html',
            'src/main/webapp/app/notification/course-notification/course-notification-popup-overlay/**/*.html',
            'src/main/webapp/app/localci/build-agent-summary/**/*.html',
            'src/main/webapp/app/localci/build-agent-details/**/*.html',
            'src/main/webapp/app/localci/build-job-statistics/**/*.html',
            'src/main/webapp/app/shared-ui/components/buttons/copy-to-clipboard-button/**/*.html',
            'src/main/webapp/app/quiz/manage/apollon-diagrams/**/*.html',
            'src/main/webapp/app/exam/manage/exercise-groups/**/*.html',
            'src/main/webapp/app/exercise/exercise-action-bar/**/*.html',
            'src/main/webapp/app/exercise/exam-exercise-row-buttons/**/*.html',
            'src/main/webapp/app/course/manage/user-management-dropdown/**/*.html',
            'packages/tum-ui/src/lib/**/*.html',
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
);
