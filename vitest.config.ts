/**
 * Vitest Configuration for Artemis Angular Tests
 *
 * Uses @analogjs/vite-plugin-angular for Angular compilation with Vitest 4.
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

const isCI = process.env.CI === 'true';

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
    css: {
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(__dirname)],
                silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'if-function'],
            },
        },
    },
    // JIT mode required for ng-mocks compatibility
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: ['tsconfig.app.json', 'tsconfig.spec.json'] })],
    test: {
        globals: true,
        pool: 'forks',
        environment: 'jsdom',
        setupFiles: ['src/test/javascript/spec/vitest-test-setup.ts'],
        include: [
            'src/main/webapp/app/fileupload/**/*.spec.ts', // include fileupload tests
            'src/main/webapp/app/core/**/*.spec.ts', // include all core tests
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
            'src/main/webapp/app/exam/manage/students/**/*.spec.ts', // include exam manage students tests
            'src/main/webapp/app/shared/components/buttons/**/*.spec.ts', // include shared buttons
            'src/main/webapp/app/shared/table-view/**/*.spec.ts', // include shared table view
            'src/main/webapp/app/shared/feature-toggle/**/*.spec.ts', // include feature-toggle service tests
            'src/main/webapp/app/shared/sort/**/*.directive.spec.ts', // include sort directives
            'src/main/webapp/app/shared/user-import/util/**/*.spec.ts', // include user import util tests
            'src/main/webapp/app/programming/manage/services/problem-statement.service.spec.ts', // include problem statement service tests
            'src/main/webapp/app/programming/manage/shared/problem-statement.utils.spec.ts', // include problem statement utils tests
            'src/main/webapp/app/shared/monaco-editor/inline-refinement-button/*.spec.ts', // include inline refinement button tests
            'src/main/webapp/app/shared/category-selector-primeng/**/*.spec.ts', // include category-selector-primeng tests
            'src/main/webapp/app/shared/form/title-channel-name-primeng/**/*.spec.ts', // include title-channel-name-primeng tests
            'src/main/webapp/app/exercise/exercise-headers/**/*.spec.ts', // include exercise headers tests
            'src/main/webapp/app/exercise/synchronization/**/*.spec.ts', // include exercise synchronization tests
            'src/main/webapp/app/exercise/version-history/**/*.spec.ts', // include exercise version history tests
            'src/main/webapp/app/exercise/review/**/*.spec.ts', // include review module tests
            'src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**/*.spec.ts', // include checklist-panel tests
            'src/main/webapp/app/hyperion/**/*.spec.ts', // include hyperion module tests
            'src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**/*.spec.ts', // include build phases editor tests
            'src/main/webapp/app/programming/manage/version-history/**/*.spec.ts', // include programming version history tests
            'src/main/webapp/app/communication/**/*.spec.ts', // include all communication module tests
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
            reporter: isCI ? ['text', 'lcov', 'json-summary'] : ['text', 'lcov', 'html', 'json-summary'],
            reportsDirectory: 'build/test-results/vitest/coverage',
            include: [
                'src/main/webapp/app/assessment/**/*.ts', // include assessment for code coverage
                'src/main/webapp/app/buildagent/**/*.ts', // include buildagent for code coverage
                'src/main/webapp/app/core/**/*.ts', // include all core for code coverage
                'src/main/webapp/app/fileupload/**/*.ts', // include fileupload for code coverage
                'src/main/webapp/app/lecture/**/*.ts', // include lecture for code coverage
                'src/main/webapp/app/quiz/**/*.ts', // include quiz for code coverage
                'src/main/webapp/app/text/**/*.ts', // include text module for code coverage
                'src/main/webapp/app/tutorialgroup/**/*.ts', // include tutorial group for code coverage
                'src/main/webapp/app/lti/**/*.ts', // include lti for code coverage
                'src/main/webapp/app/modeling/**/*.ts', // include modeling for code coverage
                'src/main/webapp/app/atlas/**/*.ts', // include atlas for code coverage
                'src/main/webapp/app/iris/**/*.ts', // include iris for code coverage
                'src/main/webapp/app/exam/manage/students/**/*.ts', // include exam manage students for code coverage
                'src/main/webapp/app/shared/components/buttons/**/*.ts', // include shared buttons for code coverage
                'src/main/webapp/app/shared/feature-toggle/**/*.ts', // include feature-toggle service for code coverage
                'src/main/webapp/app/shared/user-import/util/**/*.ts', // include user import utils for code coverage
                'src/main/webapp/app/shared/table-view/**/*.ts', // include shared table view for code coverage
                'src/main/webapp/app/shared/sort/**/*.directive.ts', // include sort directives for code coverage
                'src/main/webapp/app/programming/manage/services/problem-statement.service.ts', // include problem statement service for code coverage
                'src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts', // include problem statement utils for code coverage
                'src/main/webapp/app/shared/monaco-editor/inline-refinement-button/*.ts', // include inline refinement button for code coverage
                'src/main/webapp/app/shared/category-selector-primeng/**/*.ts', // include category-selector-primeng for code coverage
                'src/main/webapp/app/shared/form/title-channel-name-primeng/**/*.ts', // include title-channel-name-primeng for code coverage
                'src/main/webapp/app/exercise/exercise-headers/**/*.ts', // include exercise headers for code coverage
                'src/main/webapp/app/exercise/synchronization/**/*.ts', // include exercise synchronization for code coverage
                'src/main/webapp/app/exercise/version-history/**/*.ts', // include exercise version history for code coverage
                'src/main/webapp/app/exercise/review/**/*.ts', // include review module for code coverage
                'src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**/*.ts', // include checklist-panel for code coverage
                'src/main/webapp/app/hyperion/**/*.ts', // include hyperion module for code coverage
                'src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**/*.ts', // include build phases editor for code coverage
                'src/main/webapp/app/programming/manage/version-history/**/*.ts', // include programming version history for code coverage
                'src/main/webapp/app/communication/**/*.ts', // include all communication module for code coverage
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
                lines: 90.67,
                statements: 90.43,
                branches: 74.43,
                functions: 88.29,
            },
        },
    },
});
