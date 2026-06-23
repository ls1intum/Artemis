import eslint from '@eslint/js';
import typescriptParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';

export default [
    {
        ignores: ['build/**', '.docusaurus/**', 'node_modules/**'],
    },
    eslint.configs.recommended,
    {
        files: ['src/**/*.js'],
        languageOptions: {
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
                ecmaFeatures: {
                    jsx: true,
                },
            },
            globals: {
                console: 'readonly',
                document: 'readonly',
                window: 'readonly',
                Element: 'readonly',
                URL: 'readonly',
            },
        },
        rules: {
            'no-undef': 'error',
            'no-unused-vars': 'error',
        },
    },
    {
        files: ['src/**/*.{ts,tsx}'],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                ecmaVersion: 'latest',
                sourceType: 'module',
                ecmaFeatures: {
                    jsx: true,
                },
            },
            globals: {
                console: 'readonly',
                document: 'readonly',
                window: 'readonly',
                Element: 'readonly',
                URL: 'readonly',
            },
        },
        plugins: {
            '@typescript-eslint': tsPlugin,
        },
        rules: {
            'no-undef': 'off',
            'no-unused-vars': 'off',
            '@typescript-eslint/no-unused-vars': 'error',
        },
    },
];
