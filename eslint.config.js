const tsPlugin = require('@typescript-eslint/eslint-plugin');
const angularPlugin = require('@angular-eslint/eslint-plugin');
const prettierPlugin = require('eslint-plugin-prettier');
const jestPlugin = require('eslint-plugin-jest');
const jestExtendedPlugin = require('eslint-plugin-jest-extended');
const typescriptParser = require('@typescript-eslint/parser');
const angularTemplateParser = require('@angular-eslint/template-parser');

module.exports = [
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
        ],
    },
    {
        files: ['src/**/*.ts'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                project: ['./tsconfig.json', './tsconfig.app.json', './tsconfig.spec.json', 'src/test/playwright/tsconfig.json'],
            },
        },
        plugins: {
            '@typescript-eslint': tsPlugin,
            '@angular-eslint': angularPlugin,
            prettier: prettierPlugin,
        },
        rules: {
            ...prettierPlugin.configs.recommended.rules,
            ...tsPlugin.configs.recommended.rules,
            ...angularPlugin.configs.recommended.rules,
            '@angular-eslint/directive-selector': [
                'warn',
                {
                    type: 'attribute',
                    prefix: 'jhi',
                    style: 'camelCase',
                },
            ],
            '@angular-eslint/component-selector': [
                'warn',
                {
                    type: 'element',
                    prefix: 'jhi',
                    style: 'kebab-case',
                },
            ],
            '@typescript-eslint/no-non-null-assertion': 'off',
            '@typescript-eslint/no-unsafe-return': 'off',
            '@typescript-eslint/no-unsafe-member-access': 'off',
            '@typescript-eslint/no-unsafe-call': 'off',
            '@typescript-eslint/no-floating-promises': 'off',
            '@typescript-eslint/no-unsafe-assignment': 'off',
            '@angular-eslint/no-output-on-prefix': 'off',
            '@typescript-eslint/ban-ts-comment': 'warn',
            '@typescript-eslint/no-empty-function': 'off',
            '@typescript-eslint/no-non-null-asserted-optional-chain': 'warn',
            '@typescript-eslint/no-explicit-any': 'off',
            'no-case-declarations': 'off',
            'prefer-const': 'warn',
            'prefer-spread': 'warn',
            'no-var': 'error',
            'sort-imports': [
                'error',
                {
                    ignoreDeclarationSort: true,
                },
            ],
            '@typescript-eslint/no-unused-vars': ['error', { caughtErrors: 'none' }],
        },
    },
    {
        files: ['*.html'],
        languageOptions: {
            parser: angularTemplateParser,
        },
        plugins: {
            '@angular-eslint': angularPlugin,
            prettier: prettierPlugin,
        },
        rules: {
            // ...angularPlugin.configs['template/recommended'].rules,
            'prettier/prettier': ['error', { parser: 'angular' }],
            '@angular-eslint/template/prefer-control-flow': 'error',
            '@angular-eslint/template/prefer-self-closing-tags': 'error',
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
        files: ['src/test/javascript/**'],
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
            '@typescript-eslint/ban-ts-comment': 'off',
            '@typescript-eslint/no-var-requires': 'off',
        },
    },
];
