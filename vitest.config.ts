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
            'src/main/webapp/app/account/**/*.spec.ts', // include all account tests
            'src/main/webapp/app/admin/**/*.spec.ts', // include all admin tests
            'src/main/webapp/app/core/**/*.spec.ts', // include all core tests
            'src/main/webapp/app/course/**/*.spec.ts', // include all course tests
            'src/main/webapp/app/calendar/**/*.spec.ts', // include all calendar tests
            'src/main/webapp/app/localci/**/*.spec.ts', // include localci tests (absorbed the buildagent UI)
            'src/main/webapp/app/localvc/**/*.spec.ts', // include localvc tests
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
            'src/main/webapp/app/exam/overview/summary/exam-request-ai-feedback-button/**/*.spec.ts', // include exam request AI feedback button tests
            'src/main/webapp/app/exam/manage/student-exams/**/*.spec.ts', // include exam manage student-exams tests
            'src/main/webapp/app/exam/manage/test-runs/**/*.spec.ts', // include exam manage test-runs tests
            'src/main/webapp/app/exam/manage/exercise-groups/**/*.spec.ts', // include exam manage exercise groups tests
            'src/main/webapp/app/exam/manage/suspicious-behavior/**/*.spec.ts', // include exam manage suspicious behavior tests
            'src/main/webapp/app/exam/manage/services/**/*.spec.ts', // include exam manage services tests
            'src/main/webapp/app/exam/manage/exam-management/**/*.spec.ts', // include exam management tests
            'src/main/webapp/app/exam/manage/exam-scores/**/*.spec.ts', // include exam scores tests
            'src/main/webapp/app/exam/manage/exam-status/**/*.spec.ts', // include exam status tests
            'src/main/webapp/app/exam/manage/exams/**/*.spec.ts', // include exams (detail/import/update/checklist/mode-picker) tests
            'src/main/webapp/app/exam/shared/**/*.spec.ts', // include exam shared tests
            'src/main/webapp/app/exam/overview/**/*.spec.ts', // include exam overview tests
            'src/main/webapp/app/shared-ui/**/*.spec.ts', // include shared-ui module tests
            'src/main/webapp/app/foundation/**/*.spec.ts', // include all foundation tests (migrated to Vitest)
            'src/main/webapp/app/exercise/dashboards/**/*.spec.ts', // include dashboards tests
            'src/main/webapp/app/programming/manage/services/problem-statement.service.spec.ts', // include problem statement service tests
            'src/main/webapp/app/programming/manage/shared/problem-statement.utils.spec.ts', // include problem statement utils tests
            'src/main/webapp/app/editor/monaco-editor/inline-refinement-button/*.spec.ts', // include inline refinement button tests
            'src/main/webapp/app/exercise/exercise-headers/**/*.spec.ts', // include exercise headers tests
            'src/main/webapp/app/exercise/synchronization/**/*.spec.ts', // include exercise synchronization tests
            'src/main/webapp/app/exercise/version-history/**/*.spec.ts', // include exercise version history tests
            'src/main/webapp/app/exercise/review/**/*.spec.ts', // include review module tests
            'src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**/*.spec.ts', // include checklist-panel tests
            'src/main/webapp/app/hyperion/**/*.spec.ts', // include hyperion module tests
            'src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**/*.spec.ts', // include build phases editor tests
            'src/main/webapp/app/programming/manage/version-history/**/*.spec.ts', // include programming version history tests
            'src/main/webapp/app/communication/**/*.spec.ts', // include all communication module tests
            'src/main/webapp/app/notification/**/*.spec.ts', // include all notification module tests
            'src/main/webapp/app/exercise/participation/**/*.spec.ts', // include participation tests
            'src/main/webapp/app/exercise/participation-submission/**/*.spec.ts', // include participation-submission tests
            'src/main/webapp/app/exercise/exercise-scores/**/*.spec.ts', // include exercise-scores tests
            'src/main/webapp/app/exercise/shared/filter-dropdown/**/*.spec.ts', // include filter-dropdown component tests
            'src/main/webapp/app/programming/shared/services/build-phases-template.service.spec.ts', // include build phases template service tests
            'src/main/webapp/app/programming/shared/entities/build-plan-phases.model.spec.ts', // include build plan phases model tests
            'src/main/webapp/app/programming/shared/services/legacy-build-plan-converter.service.spec.ts', // include legacy build plan converter service tests
            'src/main/webapp/app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-modal.component.spec.ts', // include git diff dialog tests
            'src/main/webapp/app/programming/shared/programming-exercise-update-timeline/**/*.spec.ts', // include programming exercise update timeline tests
            'src/main/webapp/app/logos/**/*.spec.ts', // include logos tests
            'src/main/webapp/app/sharing/**/*.spec.ts', // include sharing tests
            'src/main/webapp/app/app.component.spec.ts', // include app-shell (app.component) tests
        ],
        exclude: ['**/node_modules/**', '**/build/**'],
        testTimeout: 10000,
        reporters: ['default', 'junit'],
        outputFile: './build/test-results/vitest/junit.xml',
        server: {
            deps: {
                inline: [/@tumaet\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
        coverage: {
            provider: 'istanbul',
            reporter: isCI ? ['text', 'lcov', 'json-summary'] : ['text', 'lcov', 'html', 'json-summary'],
            reportsDirectory: 'build/test-results/vitest/coverage',
            include: [
                'src/main/webapp/app/account/**/*.ts', // include all account for code coverage
                'src/main/webapp/app/admin/**/*.ts', // include all admin for code coverage
                'src/main/webapp/app/assessment/**/*.ts', // include assessment for code coverage
                'src/main/webapp/app/localci/**/*.ts', // include localci for code coverage
                'src/main/webapp/app/localvc/**/*.ts', // include localvc for code coverage
                'src/main/webapp/app/core/**/*.ts', // include all core for code coverage
                'src/main/webapp/app/course/**/*.ts', // include all course for code coverage
                'src/main/webapp/app/calendar/**/*.ts', // include all calendar for code coverage
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
                'src/main/webapp/app/exam/overview/summary/exam-request-ai-feedback-button/**/*.ts', // include exam request AI feedback button for code coverage
                'src/main/webapp/app/exam/manage/student-exams/**/*.ts', // include exam manage student-exams for code coverage
                'src/main/webapp/app/exam/manage/test-runs/**/*.ts', // include exam manage test-runs for code coverage
                'src/main/webapp/app/exam/manage/exercise-groups/**/*.ts', // include exam manage exercise groups for code coverage
                'src/main/webapp/app/exam/manage/suspicious-behavior/**/*.ts', // include exam manage suspicious behavior for code coverage
                'src/main/webapp/app/exam/manage/services/**/*.ts', // include exam manage services for code coverage
                'src/main/webapp/app/exam/manage/exam-management/**/*.ts', // include exam management for code coverage
                'src/main/webapp/app/exam/manage/exam-scores/**/*.ts', // include exam scores for code coverage
                'src/main/webapp/app/exam/manage/exam-status/**/*.ts', // include exam status for code coverage
                'src/main/webapp/app/exam/manage/exams/**/*.ts', // include exams (detail/import/update/checklist/mode-picker) for code coverage
                'src/main/webapp/app/exam/overview/**/*.ts', // include exam overview for code coverage
                'src/main/webapp/app/exam/shared/**/*.ts', // include exam shared for code coverage
                'src/main/webapp/app/shared-ui/**/*.ts', // include shared-ui module for code coverage
                'src/main/webapp/app/foundation/**/*.ts', // include all foundation for code coverage
                'src/main/webapp/app/exercise/dashboards/**/*.ts', // include dashboards for code coverage
                'src/main/webapp/app/programming/manage/services/problem-statement.service.ts', // include problem statement service for code coverage
                'src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts', // include problem statement utils for code coverage
                'src/main/webapp/app/editor/monaco-editor/inline-refinement-button/*.ts', // include inline refinement button for code coverage
                'src/main/webapp/app/exercise/exercise-headers/**/*.ts', // include exercise headers for code coverage
                'src/main/webapp/app/exercise/synchronization/**/*.ts', // include exercise synchronization for code coverage
                'src/main/webapp/app/exercise/version-history/**/*.ts', // include exercise version history for code coverage
                'src/main/webapp/app/exercise/review/**/*.ts', // include review module for code coverage
                'src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**/*.ts', // include checklist-panel for code coverage
                'src/main/webapp/app/hyperion/**/*.ts', // include hyperion module for code coverage
                'src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**/*.ts', // include build phases editor for code coverage
                'src/main/webapp/app/programming/manage/version-history/**/*.ts', // include programming version history for code coverage
                'src/main/webapp/app/communication/**/*.ts', // include all communication module for code coverage
                'src/main/webapp/app/notification/**/*.ts', // include all notification module for code coverage
                'src/main/webapp/app/exercise/participation/**/*.ts', // include participation for code coverage
                'src/main/webapp/app/exercise/participation-submission/**/*.ts', // include participation-submission for code coverage
                'src/main/webapp/app/exercise/exercise-scores/**/*.ts', // include exercise-scores for code coverage
                'src/main/webapp/app/exercise/shared/filter-dropdown/**/*.ts', // include filter-dropdown component for code coverage
                'src/main/webapp/app/programming/shared/services/build-phases-template.service.ts', // include build phases template service for code coverage
                'src/main/webapp/app/programming/shared/services/legacy-build-plan-converter.service.ts', // include legacy build plan converter service for code coverage
                'src/main/webapp/app/programming/shared/entities/build-plan-phases.model.ts', // include build plan phases model for code coverage
                'src/main/webapp/app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component.ts', // include git diff dialog for code coverage
                'src/main/webapp/app/programming/shared/programming-exercise-update-timeline/**/*.ts', // include programming exercise update timeline for code coverage
                'src/main/webapp/app/logos/**/*.ts', // include logos for code coverage
                'src/main/webapp/app/sharing/**/*.ts', // include sharing for code coverage
                'src/main/webapp/app/app.component.ts', // include app-shell (app.component) for code coverage
            ],
            exclude: [
                '**/node_modules/**', // exclude node_modules with third-party code
                '**/*.spec.ts', // exclude test specification files
                '**/*.route.ts', // exclude route definition files (not really testable)
                '**/*.routes.ts', // exclude route definition files (not really testable)
                '**/*.model.ts', // exclude data model files (not really testable)
                'src/main/webapp/app/core/config/dayjs.ts', // exclude dayjs configuration file (not really testable)
                'src/main/webapp/app/core/config/monaco.config.ts', // exclude monaco configuration file (not really testable)
                'src/main/webapp/app/core/config/prod.config.ts', // exclude dayjs configuration file (not really testable)
            ],
            thresholds: {
                // Lowered ~0.5pp below current actuals to absorb further Jest→Vitest
                // migration drift. Re-tune when migration completes.
                lines: 89.6,
                statements: 89.4,
                branches: 73.6,
                functions: 87.4,
            },
        },
    },
});
