import tsPlugin from '@typescript-eslint/eslint-plugin';
import angularPlugin from '@angular-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
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

// Existing `ngOnChanges` migration backlog. Keep the new rule baseline-clean by excluding unchanged
// files that still need a focused computed()/effect() migration. Remove entries as the hooks are migrated.
const remainingNgOnChangesMigrationBacklog = [
    'src/main/webapp/app/atlas/manage/forms/common-course-competency-form.component.ts',
    'src/main/webapp/app/atlas/manage/forms/competency/competency-form.component.ts',
    'src/main/webapp/app/atlas/manage/forms/prerequisite/prerequisite-form.component.ts',
    'src/main/webapp/app/atlas/overview/competency-accordion/competency-accordion.component.ts',
    'src/main/webapp/app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component.ts',
    'src/main/webapp/app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component.ts',
    'src/main/webapp/app/exercise/feedback/feedback.component.ts',
    'src/main/webapp/app/exercise/rating/rating.component.ts',
    'src/main/webapp/app/exercise/statistics/doughnut-chart/doughnut-chart.component.ts',
    'src/main/webapp/app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component.ts',
    'src/main/webapp/app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component.ts',
    'src/main/webapp/app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component.ts',
    'src/main/webapp/app/plagiarism/manage/plagiarism-run-details/plagiarism-run-details.component.ts',
    'src/main/webapp/app/plagiarism/manage/plagiarism-sidebar/plagiarism-sidebar.component.ts',
    'src/main/webapp/app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component.ts',
    'src/main/webapp/app/plagiarism/manage/plagiarism-split-view/split-pane-header/split-pane-header.component.ts',
    'src/main/webapp/app/plagiarism/manage/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component.ts',
    'src/main/webapp/app/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component.ts',
    'src/main/webapp/app/quiz/manage/re-evaluate/quiz-re-evaluate.component.ts',
    'src/main/webapp/app/quiz/manage/update/quiz-exercise-update.component.ts',
];

export default tseslint.config(
    {
        // Only src/main/webapp/ and src/test/javascript/ contain lintable client code.
        // The lint command targets src/main/webapp explicitly, but these ignores also
        // protect against scanning irrelevant directories when ESLint is invoked
        // without explicit paths (e.g. by IDEs or lint-staged).
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
            // Specific file exclusions within linted directories
            'src/main/webapp/app/openapi/**',
            'src/main/webapp/content/scripts/pdf.worker.min.mjs',
            'src/test/javascript/spec/stub.js',
            // Root-level config files (not part of the Angular client)
            '*.js',
            '*.mjs',
        ],
    },
    eslint.configs.recommended,
    {
        files: ['src/main/webapp/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                project: ['./tsconfig.json', './tsconfig.app.json', './tsconfig.spec.json'],
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
            ...angularPlugin.configs.recommended.rules,
            '@typescript-eslint/no-non-null-assertion': 'off',
            '@typescript-eslint/no-unsafe-return': 'off',
            '@typescript-eslint/no-unsafe-member-access': 'off',
            '@typescript-eslint/no-unsafe-call': 'off',
            '@typescript-eslint/no-floating-promises': 'off',
            '@typescript-eslint/no-unsafe-assignment': 'off',
            '@angular-eslint/no-output-on-prefix': 'off',
            '@typescript-eslint/ban-ts-comment': 'warn',
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
                    ],
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
        },
    },
    // Force JSON.parse results to carry an explicit type. `JSON.parse` is declared to return `any`, which
    // silently disables type checking on everything derived from it — a typo like `obj.colour` compiles and
    // yields `undefined` at runtime. Route parsing through `parseJson<T>()` (app/foundation/util/json.util),
    // whose generic defaults to `unknown`, so a caller cannot touch the result's properties without stating
    // the expected shape. Warn-level for now: existing call sites are migrated incrementally before this is
    // raised to `error`. The wrapper itself holds the single sanctioned `JSON.parse` (line-level disabled),
    // and test code may parse fixtures freely (specs excluded below).
    {
        files: ['src/main/webapp/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'no-restricted-properties': [
                'warn',
                {
                    object: 'JSON',
                    property: 'parse',
                    message:
                        'Avoid untyped JSON.parse(): its result is `any`, so property access is unchecked. Use parseJson<T>() from app/foundation/util/json.util and pass the expected type.',
                },
            ],
        },
    },
    // Discourage `ngOnChanges` across Angular client files that have a clean baseline. Prefer computed() for derived
    // state and effect() for genuine side effects. `ngOnChanges` still works in Angular 21 (it fires for signal inputs),
    // so this is a consistency preference, not a correctness rule. Existing migration-backlog files are excluded above
    // until converted; genuinely unavoidable new cases should use a justified line-level disable.
    // Full rationale + decision table:
    // documentation/docs/developer/guidelines/client-development.mdx ("Reacting to input changes & lifecycle hooks").
    {
        files: ['src/main/webapp/app/**/*.ts'],
        ignores: ['**/*.spec.ts', ...remainingNgOnChangesMigrationBacklog],
        rules: {
            'localRules/prefer-signal-reactivity-over-ngonchanges': 'warn',
        },
    },
    // Zoneless correctness: a mutable component/directive field that the template reads must be a signal,
    // otherwise reassigning it outside a synchronous render / event handler (subscribe, setTimeout, a helper
    // reached from one, …) schedules no change detection and the view silently goes stale. Fields the template
    // never reads, injected services, and constants are exempt; genuine [(ngModel)]/[(x)] two-way targets that
    // cannot be signals use a justified line-level disable. Full rationale:
    // documentation/docs/developer/guidelines/client-development.mdx ("Zoneless change detection & signal-based state").
    {
        files: ['src/main/webapp/app/**/*.ts'],
        ignores: ['**/*.spec.ts'],
        rules: {
            'localRules/prefer-signal-template-state': 'error',
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
                    ],
                    patterns: [
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
                    ],
                    patterns: [
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
        files: ['src/test/javascript/**', 'src/main/webapp/app/**/*.spec.ts'],
        rules: {
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
        files: ['src/test/**/mock-*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
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
        files: ['src/main/webapp/**/*.html'],
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
);
