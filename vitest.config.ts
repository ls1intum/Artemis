/**
 * Vitest Configuration for Artemis Angular Tests
 *
 * Uses @analogjs/vite-plugin-angular for Angular compilation with Vitest 4.
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

export default defineConfig({
    // Only show errors, suppress sourcemap warnings from node_modules packages
    logLevel: 'error',
    resolve: {
        alias: {
            'monaco-editor': path.resolve(__dirname, 'src/test/javascript/spec/helpers/mocks/mock-monaco-editor.ts'),
            app: path.resolve(__dirname, 'src/main/webapp/app'),
            test: path.resolve(__dirname, 'src/test/javascript/spec'),
        },
    },
    // JIT mode required for ng-mocks compatibility
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: ['tsconfig.app.json', 'tsconfig.spec.json'] })],
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: ['src/test/javascript/spec/vitest-test-setup.ts'],
        include: [
            'src/main/webapp/app/fileupload/**/*.spec.ts', // include fileupload tests
            'src/main/webapp/app/core/account/**/*.spec.ts', // include core account tests
            'src/main/webapp/app/core/admin/**/*.spec.ts', // include core admin tests
            'src/main/webapp/app/core/course/manage/**/*.spec.ts', // include course manage tests
            'src/main/webapp/app/buildagent/**/*.spec.ts', // include build agent tests
            'src/main/webapp/app/text/**/*.spec.ts', // include text module tests
            'src/main/webapp/app/assessment/**/*.spec.ts', // include assessment tests
            'src/main/webapp/app/tutorialgroup/**/*.spec.ts', // include tutorial group tests
            'src/main/webapp/app/quiz/**/*.spec.ts', // include quiz tests
            'src/main/webapp/app/lecture/**/*.spec.ts', // include lecture tests
            'src/main/webapp/app/lti/**/*.spec.ts', // include lti tests
            'src/main/webapp/app/modeling/**/*.spec.ts', // include modeling tests
            'src/main/webapp/app/atlas/**/*.spec.ts', // include atlas tests
            'src/main/webapp/app/iris/**/*.spec.ts', // include iris tests
            'src/main/webapp/app/shared/components/buttons/**/*.spec.ts', // include shared buttons
            'src/main/webapp/app/exercise/review/**/*.spec.ts', // include review module tests
        ],
        exclude: ['**/node_modules/**', '**/build/**'],
        testTimeout: 10000,
        reporters: ['default', 'junit'],
        outputFile: './build/test-results/vitest/junit.xml',
        server: {
            deps: {
                inline: [/@ls1intum\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
        coverage: {
            provider: 'istanbul',
            reporter: ['text', 'lcov', 'html', 'json-summary'],
            reportsDirectory: 'build/test-results/vitest/coverage',
            include: [
                'src/main/webapp/app/assessment/**/*.ts', // include assessment for code coverage
                'src/main/webapp/app/buildagent/**/*.ts', // include buildagent for code coverage
                'src/main/webapp/app/core/account/**/*.ts', // include core account for code coverage
                'src/main/webapp/app/core/admin/**/*.ts', // include core admin for code coverage
                'src/main/webapp/app/core/course/manage/**/*.ts', // include course manage for code coverage
                'src/main/webapp/app/fileupload/**/*.ts', // include fileupload for code coverage
                'src/main/webapp/app/lecture/**/*.ts', // include lecture for code coverage
                'src/main/webapp/app/quiz/**/*.ts', // include quiz for code coverage
                'src/main/webapp/app/text/**/*.ts', // include text module for code coverage
                'src/main/webapp/app/tutorialgroup/**/*.ts', // include tutorial group for code coverage
                'src/main/webapp/app/lti/**/*.ts', // include lti for code coverage
                'src/main/webapp/app/modeling/**/*.ts', // include modeling for code coverage
                'src/main/webapp/app/atlas/**/*.ts', // include atlas for code coverage
                'src/main/webapp/app/iris/**/*.ts', // include iris for code coverage
                'src/main/webapp/app/shared/components/buttons/**/*.ts', // include shared buttons for code coverage
                'src/main/webapp/app/programming/manage/services/problem-statement.service.ts',
                'src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts',
                'src/main/webapp/app/shared/monaco-editor/inline-refinement-button/inline-refinement-button.component.ts',
                'src/main/webapp/app/exercise/review/**/*.ts', // include review module for code coverage
            ],
            exclude: [
                '**/node_modules/**',   // exclude node_modules with third-party code
                '**/*.spec.ts',         // exclude test specification files
                '**/*.route.ts',        // exclude route definition files (not really testable)
                '**/*.routes.ts',       // exclude route definition files (not really testable)
                '**/*.model.ts',        // exclude data model files (not really testable)
                'src/main/webapp/app/core/config/dayjs.ts',             // exclude dayjs configuration file (not really testable)
                'src/main/webapp/app/core/config/monaco.config.ts',     // exclude monaco configuration file (not really testable)
                'src/main/webapp/app/core/config/prod.config.ts',       // exclude dayjs configuration file (not really testable)
            ],
            thresholds: {
                lines: 92.05,
                statements: 91.95,
                branches: 76.90,
                functions: 89.10,
            },
        },
    },
});
