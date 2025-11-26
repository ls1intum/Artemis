import tsPlugin from '@typescript-eslint/eslint-plugin';
import angularPlugin from '@angular-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import unicornPlugin from 'eslint-plugin-unicorn';
import jestPlugin from 'eslint-plugin-jest';
import jestExtendedPlugin from 'eslint-plugin-jest-extended';
import typescriptParser from '@typescript-eslint/parser';
import angularTemplateParser from '@angular-eslint/template-parser';
import angular from 'angular-eslint';
import tseslint from 'typescript-eslint';
import eslint from '@eslint/js';
import localRulesPlugin from './rules/index.mjs';

export default tseslint.config(
    {
        ignores: [
            '.cache/',
            '.git/',
            '.github/',
            '.gradle/',
            '.idea/',
            '.venv/',
            '.jhipster/',
            'build/',
            'documentation/build',
            'documentation/.docusaurus',
            'local/',
            'coverage/',
            'docker/',
            'docs/',
            'gradle/',
            'local/',
            'node/',
            'node_modules/',
            'out/',
            'repos/',
            'repos-download/',
            'src/main/generated/',
            'src/main/resources/',
            'target/',
            'uploads/',
            'local/',
            'supporting_scripts/',
            'src/test/javascript/spec/stub.js',
            '.lintstagedrc.js',
            'jest.config.js',
            'prebuild.mjs',
            'rules/**/*.js',
            'src/main/webapp/content/scripts/pdf.worker.min.mjs',
            'src/main/webapp/app/openapi/**',
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
            localRules: localRulesPlugin,
            unicorn: unicornPlugin,
        },
        // TODO: adapt the rules of the newest jhipster version, e.g. no-inferrable-types, restrict-plus-operands, etc.
        rules: {
            ...prettierPlugin.configs.recommended.rules,
            ...tsPlugin.configs.recommended.rules,
            ...angularPlugin.configs.recommended.rules,

            // Prefer undefined over null (runtime)
            'unicorn/no-null': 'error',
            // Prefer undefined over null (types)
            '@typescript-eslint/no-restricted-types': [
                'error',
                {
                    types: {
                        null: {
                            message: 'Use `undefined` instead of `null`.',
                        },
                    },
                },
            ],

            // "no-restricted-syntax": [
            //     "error",
            //     {
            //         selector: "ObjectExpression > SpreadElement",
            //         message: "Do not use object spread. Use Object.assign instead."
            //     }
            // ],
            // vs.
            // Prefer object spread over Object.assign
            // 'prefer-object-spread': 'error',

            // Disallow ALL assertions (`as`, angle-brackets, `const` assertions)
            '@typescript-eslint/consistent-type-assertions': [
                'error',
                {
                    assertionStyle: 'never',
                },
            ],

            // Enforce explicit function return types, with some exceptions
            "@typescript-eslint/explicit-function-return-type": [
                "error",
                {
                    "allowExpressions": false,
                    "allowTypedFunctionExpressions": true,
                    "allowHigherOrderFunctions": true,
                    "allowConciseArrowFunctionExpressionsStartingWithVoid": true
                }
            ],
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
            '@typescript-eslint/no-deprecated': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/ban-ts-comment': 'off',
            '@typescript-eslint/no-var-requires': 'off',
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
