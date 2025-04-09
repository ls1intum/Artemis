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
import boundaries from 'eslint-plugin-boundaries';

export default tseslint.config(
    {
        ignores: [
            '.cache/',
            '.git/',
            '.github/',
            '.gradle/',
            '.idea/',
            '.jhipster/',
            'build/',
            'coverage/',
            'docker/',
            'docs/',
            'gradle/',
            'node/',
            'node_modules/',
            'out/',
            'repos/',
            'repos-download/',
            'src/main/generated/',
            'src/main/resources/',
            'target/',
            'uploads/',
            'supporting_scripts/',
            'stub.js',
            '.lintstagedrc.js',
            'jest.config.js',
            'prebuild.mjs',
            'rules/**/*.js',
        ],
    },
    eslint.configs.recommended,
    {
        files: ['src/main/webapp/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                project: ['./tsconfig.json', './tsconfig.app.json', './tsconfig.spec.json', 'src/test/playwright/tsconfig.json'],
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
            // '@typescript-eslint/no-deprecated': 'warn',
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
                        }
                    ]
                }
            ]
        },
    },
    {
        files: ['src/test/**/mock-*.ts'],
        languageOptions: {
            parser: typescriptParser,
        },
        plugins: {
            '@typescript-eslint': tsPlugin,
        },
        rules: {
            '@typescript-eslint/no-unused-vars': 'off',
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
            'jest/expect-expect': 'off',
            'jest/no-conditional-expect': 'off',
            // '@typescript-eslint/no-deprecated': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/ban-ts-comment': 'off',
            '@typescript-eslint/no-var-requires': 'off',
            '@typescript-eslint/no-unused-vars': 'off',
            'no-unused-private-class-members': 'error',
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
    {
        files: ['src/main/webapp/app/**/*.ts'],
        plugins: {
            boundaries
        },
        settings: {
            // Define element types based on module folder structure.
            "boundaries/elements": [
                // For modules with three subfolders (overview, manage, shared):
                {
                    type: "module-overview",
                    pattern: "src/main/webapp/app/*/overview/**",
                    capture: ["module"]
                },
                {
                    type: "module-manage",
                    pattern: "src/main/webapp/app/*/manage/**",
                    capture: ["module"]
                },
                {
                    type: "module-shared",
                    pattern: "src/main/webapp/app/*/shared/**",
                    capture: ["module"]
                },
                // For flat modules, split into internal vs. shared.
                // Assume that flat modules place their shared code in a subfolder named `shared`
                // and that anything outside that folder is considered internal.
                {
                    type: "module-flat-shared",
                    pattern: "src/main/webapp/app/{core,buildagent,communication}/shared/**",
                    capture: ["module"]
                },
                {
                    type: "module-flat-internal",
                    pattern: "src/main/webapp/app/{core,buildagent,communication}/**",
                    // Exclude the shared folder from being considered “internal”
                    // (Some plugins support an “ignore” property; if not, you can use a pattern that excludes it.)
                    ignore: "src/main/webapp/app/{core,buildagent,communication}/shared/**",
                    capture: ["module"]
                },
                // Global shared module – treated as unrestricted
                {
                    type: "global-shared",
                    pattern: "src/main/webapp/app/shared/**"
                }
            ]
        },
        rules: {
            // Enforce boundaries with custom rules:
            "boundaries/element-types": ["error", {
                default: "allow",
                rules: [
                    // 1. Prevent a file in a module's overview from importing from its own manage folder, and vice versa.
                    { from: "module-overview", disallow: ["module-manage", { module: "${from.module}" }] },
                    { from: "module-manage",   disallow: ["module-overview", { module: "${from.module}" }] },

                    // 2. Overview or manage files must only import from the shared folder of any module.
                    //    Disallow cross-module import of overview, manage, or flat module files.
                    {
                        from: ["module-overview", "module-manage"],
                        disallow: [
                            ["module-overview", { module: "!${from.module}" }],
                            ["module-manage",   { module: "!${from.module}" }],
                            ["module-flat",     { module: "!${from.module}" }]
                        ]
                    }
                ]
            }]
        }
    }
);
