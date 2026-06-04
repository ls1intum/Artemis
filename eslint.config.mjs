import tsPlugin from '@typescript-eslint/eslint-plugin';
import angularPlugin from '@angular-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import jestPlugin from 'eslint-plugin-jest';
import jestExtendedPlugin from 'eslint-plugin-jest-extended';
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
            localRules: localRulesPlugin
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
            '@typescript-eslint/no-unused-vars': ['error', {
                vars: 'all',
                varsIgnorePattern: '^_', // Ignore variables prefixed with `_`
                args: 'none',
                ignoreRestSiblings: true,
                caughtErrors: 'none',
            },],
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
                            message: "Please import from 'dayjs/esm' instead."
                        },
                        {
                            name: 'lodash',
                            message: "Please import from 'lodash-es' instead."
                        }
                    ]
                }
            ],
            'localRules/require-signal-reference-ngb-modal-input': 'error',
            'localRules/enforce-signal-apis-in-migrated-modules': 'error',
            'localRules/enforce-cleanup-on-destroy': 'warn',
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
                    ],
                    patterns: [
                        {
                            // Block both absolute (app/shared-ui/**) and relative (../shared-ui, ../../shared-ui, …) imports
                            // so the layer cannot be bypassed with a relative path.
                            group: blockLayerImportPatterns('shared-ui'),
                            message: 'app/foundation/ must not depend on app/shared-ui/. foundation/ is the base infrastructure layer (no DOM/UI). If a UI primitive is needed here, the file probably belongs in app/shared-ui/ instead.',
                        },
                        {
                            group: blockLayerImportPatterns('editor'),
                            message: 'app/foundation/ must not depend on app/editor/. foundation/ is the base infrastructure layer. Extract the editor-side dependency to a neutral constant or move the consuming file into app/editor/.',
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
                    ],
                    patterns: [
                        {
                            // Block both absolute (app/editor/**) and relative (../editor, ../../editor, …) imports.
                            group: blockLayerImportPatterns('editor'),
                            message: 'app/shared-ui/ must not depend on app/editor/. shared-ui/ holds generic UI primitives; the editor stack is specialised and sits above shared-ui/.',
                        },
                    ],
                },
            ],
        },
    },
    {
        files: ['src/test/javascript/**','src/main/webapp/app/**/*.spec.ts'],
        plugins: {
            jest: jestPlugin,
            'jest-extended': jestExtendedPlugin,
        },
        rules: {
            ...jestPlugin.configs.recommended.rules,
            ...jestPlugin.configs.style.rules,
            ...jestExtendedPlugin.configs.all.rules,
            // jest-extended matchers are intentionally NOT registered in the Vitest setup (only Jest registers
            // 'jest-extended/all'). These rules auto-fix native matchers to jest-extended forms (e.g. toBe(true) ->
            // toBeTrue(), toEqual([]) -> toBeArray()) which throw "Invalid Chai property" under Vitest. Disable the
            // matcher-conversion auto-fixes so specs keep native matchers that work under both runners.
            // (prefer-to-have-been-called-once stays enabled: toHaveBeenCalledOnce is native to Vitest.)
            'jest-extended/prefer-to-be-true': 'off',
            'jest-extended/prefer-to-be-false': 'off',
            'jest-extended/prefer-to-be-array': 'off',
            'jest-extended/prefer-to-be-object': 'off',
            'jest/expect-expect': 'off',
            'jest/no-conditional-expect': 'off',
            '@typescript-eslint/no-deprecated': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/ban-ts-comment': 'off',
            '@typescript-eslint/no-require-imports': 'off',
            '@typescript-eslint/no-unused-vars': ['warn', {
                vars: 'all',
                varsIgnorePattern: '^_',
                args: 'none',
                ignoreRestSiblings: true,
                caughtErrors: 'none',
            }],
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
