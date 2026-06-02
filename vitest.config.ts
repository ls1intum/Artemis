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
            'src/main/webapp/app/exercise/**/*.spec.ts', // include exercise tests
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
            'src/main/webapp/app/programming/**/*.spec.ts', // include entire programming module tests (signals+vitest migration)
            'src/main/webapp/app/editor/**/*.spec.ts', // include all editor module tests (markdown/monaco editor)
            'src/main/webapp/app/exercise/synchronization/**/*.spec.ts', // include exercise synchronization tests
            'src/main/webapp/app/exercise/version-history/**/*.spec.ts', // include exercise version history tests
            'src/main/webapp/app/exercise/review/**/*.spec.ts', // include review module tests
            'src/main/webapp/app/hyperion/**/*.spec.ts', // include hyperion module tests
            'src/main/webapp/app/communication/**/*.spec.ts', // include communication module tests
            'src/main/webapp/app/notification/**/*.spec.ts', // include notification module tests
            'src/main/webapp/app/exercise/additional-feedback/**/*.spec.ts', // include additional feedback tests
            'src/main/webapp/app/exercise/assessment-progress-label/**/*.spec.ts', // include assessment progress label tests
            'src/main/webapp/app/exercise/course-exercises/**/*.spec.ts', // include course exercise service tests
            'src/main/webapp/app/exercise/difficulty-level/**/*.spec.ts', // include difficulty level tests
            'src/main/webapp/app/exercise/difficulty-picker/**/*.spec.ts', // include difficulty picker tests
            'src/main/webapp/app/exercise/exam-exercise-row-buttons/**/*.spec.ts', // include exam exercise row buttons tests
            'src/main/webapp/app/exercise/example-solution/**/*.spec.ts', // include example solution tests
            'src/main/webapp/app/exercise/example-submission/**/*.spec.ts', // include example submission tests
            'src/main/webapp/app/exercise/exercise-categories/**/*.spec.ts', // include exercise categories tests
            'src/main/webapp/app/exercise/exercise-create-buttons/**/*.spec.ts', // include exercise create buttons tests
            'src/main/webapp/app/exercise/exercise-detail-common-actions/**/*.spec.ts', // include exercise detail common actions tests
            'src/main/webapp/app/exercise/exercise-headers/**/*.spec.ts', // include exercise headers tests
            'src/main/webapp/app/exercise/exercise-info/**/*.spec.ts', // include exercise info tests
            'src/main/webapp/app/exercise/exercise-scores/**/*.spec.ts', // include exercise scores tests
            'src/main/webapp/app/exercise/exercise-title-channel-name/**/*.spec.ts', // include exercise title channel name tests
            'src/main/webapp/app/exercise/exercise-title-channel-name-primeng/**/*.spec.ts', // include exercise title channel name PrimeNG tests
            'src/main/webapp/app/exercise/exercise-update-notification/**/*.spec.ts', // include exercise update notification tests
            'src/main/webapp/app/exercise/exercise-update-warning/**/*.spec.ts', // include exercise update warning tests
            'src/main/webapp/app/exercise/external-submission/**/*.spec.ts', // include external submission tests
            'src/main/webapp/app/exercise/feedback/**/*.spec.ts', // include feedback tests
            'src/main/webapp/app/exercise/feedback-suggestion/**/*.spec.ts', // include feedback suggestion tests
            'src/main/webapp/app/exercise/import/**/*.spec.ts', // include exercise import tests
            'src/main/webapp/app/exercise/mode-picker/**/*.spec.ts', // include mode picker tests
            'src/main/webapp/app/exercise/participation/**/*.spec.ts', // include participation tests
            'src/main/webapp/app/exercise/participation-submission/**/*.spec.ts', // include participation-submission tests
            'src/main/webapp/app/exercise/presentation-score/**/*.spec.ts', // include presentation score tests
            'src/main/webapp/app/exercise/rating/**/*.spec.ts', // include rating tests
            'src/main/webapp/app/exercise/result-history/**/*.spec.ts', // include result history tests
            'src/main/webapp/app/exercise/services/**/*.spec.ts', // include exercise services tests
            'src/main/webapp/app/exercise/shared/**/*.spec.ts', // include exercise shared tests
            'src/main/webapp/app/exercise/statistics/**/*.spec.ts', // include exercise statistics tests
            'src/main/webapp/app/exercise/structured-grading-criterion/**/*.spec.ts', // include structured grading criterion tests
            'src/main/webapp/app/exercise/submission/**/*.spec.ts', // include submission tests
            'src/main/webapp/app/exercise/submission-export/**/*.spec.ts', // include submission export tests
            'src/main/webapp/app/exercise/submission-policy/**/*.spec.ts', // include submission policy tests
            'src/main/webapp/app/exercise/submission-version/**/*.spec.ts', // include submission version tests
            'src/main/webapp/app/exercise/team/**/*.spec.ts', // include team tests
            'src/main/webapp/app/exercise/team-config-form-group/**/*.spec.ts', // include team config form group tests
            'src/main/webapp/app/exercise/team-submission-sync/**/*.spec.ts', // include team submission sync tests
            'src/main/webapp/app/exercise/unreferenced-feedback/**/*.spec.ts', // include unreferenced feedback tests
            'src/main/webapp/app/exercise/util/**/*.spec.ts', // include exercise util tests
            'src/main/webapp/app/exercise/exercise-scores/**/*.spec.ts', // include exercise-scores tests
            'src/main/webapp/app/shared-ui/search-filter/**/*.spec.ts', // include search-filter component tests
            'src/main/webapp/app/shared-ui/detail-overview-list/components/programming-diff-report-detail/**/*.spec.ts', // include programming diff report detail tests
            'src/test/javascript/spec/integration/code-editor/code-editor-container.integration.spec.ts', // migrated to Vitest
            'src/main/webapp/app/logos/**/*.spec.ts', // include logos tests
            'src/main/webapp/app/sharing/**/*.spec.ts', // include sharing tests
            'src/main/webapp/app/app.component.spec.ts', // include app-shell (app.component) tests
            'src/main/webapp/app/plagiarism/**/*.spec.ts', // include all plagiarism tests
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
                'src/main/webapp/app/exercise/**/*.ts', // include exercise for code coverage
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
                'src/main/webapp/app/editor/**/*.ts', // include all editor module for code coverage
                'src/main/webapp/app/exercise/additional-feedback/**/*.ts', // include additional feedback for code coverage
                'src/main/webapp/app/exercise/assessment-progress-label/**/*.ts', // include assessment progress label for code coverage
                'src/main/webapp/app/exercise/course-exercises/**/*.ts', // include course exercise service for code coverage
                'src/main/webapp/app/exercise/difficulty-level/**/*.ts', // include difficulty level for code coverage
                'src/main/webapp/app/exercise/difficulty-picker/**/*.ts', // include difficulty picker for code coverage
                'src/main/webapp/app/exercise/exam-exercise-row-buttons/**/*.ts', // include exam exercise row buttons for code coverage
                'src/main/webapp/app/exercise/example-solution/**/*.ts', // include example solution for code coverage
                'src/main/webapp/app/exercise/example-submission/**/*.ts', // include example submission for code coverage
                'src/main/webapp/app/exercise/exercise-categories/**/*.ts', // include exercise categories for code coverage
                'src/main/webapp/app/exercise/exercise-create-buttons/**/*.ts', // include exercise create buttons for code coverage
                'src/main/webapp/app/exercise/exercise-detail-common-actions/**/*.ts', // include exercise detail common actions for code coverage
                'src/main/webapp/app/exercise/exercise-headers/**/*.ts', // include exercise headers for code coverage
                'src/main/webapp/app/exercise/exercise-info/**/*.ts', // include exercise info for code coverage
                'src/main/webapp/app/exercise/exercise-scores/**/*.ts', // include exercise scores for code coverage
                'src/main/webapp/app/exercise/exercise-title-channel-name/**/*.ts', // include exercise title channel name for code coverage
                'src/main/webapp/app/exercise/exercise-title-channel-name-primeng/**/*.ts', // include exercise title channel name PrimeNG for code coverage
                'src/main/webapp/app/exercise/exercise-update-notification/**/*.ts', // include exercise update notification for code coverage
                'src/main/webapp/app/exercise/exercise-update-warning/**/*.ts', // include exercise update warning for code coverage
                'src/main/webapp/app/exercise/external-submission/**/*.ts', // include external submission for code coverage
                'src/main/webapp/app/exercise/feedback/**/*.ts', // include feedback for code coverage
                'src/main/webapp/app/exercise/feedback-suggestion/**/*.ts', // include feedback suggestion for code coverage
                'src/main/webapp/app/exercise/import/**/*.ts', // include exercise import for code coverage
                'src/main/webapp/app/exercise/mode-picker/**/*.ts', // include mode picker for code coverage
                'src/main/webapp/app/exercise/participation/**/*.ts', // include participation for code coverage
                'src/main/webapp/app/exercise/participation-submission/**/*.ts', // include participation-submission for code coverage
                'src/main/webapp/app/exercise/presentation-score/**/*.ts', // include presentation score for code coverage
                'src/main/webapp/app/exercise/rating/**/*.ts', // include rating for code coverage
                'src/main/webapp/app/exercise/result-history/**/*.ts', // include result history for code coverage
                'src/main/webapp/app/exercise/review/**/*.ts', // include review for code coverage
                'src/main/webapp/app/exercise/services/**/*.ts', // include exercise services for code coverage
                'src/main/webapp/app/exercise/shared/**/*.ts', // include exercise shared for code coverage
                'src/main/webapp/app/exercise/statistics/**/*.ts', // include exercise statistics for code coverage
                'src/main/webapp/app/exercise/structured-grading-criterion/**/*.ts', // include structured grading criterion for code coverage
                'src/main/webapp/app/exercise/submission/**/*.ts', // include submission for code coverage
                'src/main/webapp/app/exercise/submission-export/**/*.ts', // include submission export for code coverage
                'src/main/webapp/app/exercise/submission-policy/**/*.ts', // include submission policy for code coverage
                'src/main/webapp/app/exercise/submission-version/**/*.ts', // include submission version for code coverage
                'src/main/webapp/app/exercise/synchronization/**/*.ts', // include exercise synchronization for code coverage
                'src/main/webapp/app/exercise/team/**/*.ts', // include team for code coverage
                'src/main/webapp/app/exercise/team-config-form-group/**/*.ts', // include team config form group for code coverage
                'src/main/webapp/app/exercise/team-submission-sync/**/*.ts', // include team submission sync for code coverage
                'src/main/webapp/app/exercise/unreferenced-feedback/**/*.ts', // include unreferenced feedback for code coverage
                'src/main/webapp/app/exercise/util/**/*.ts', // include exercise util for code coverage
                'src/main/webapp/app/exercise/version-history/**/*.ts', // include exercise version history for code coverage
                'src/main/webapp/app/hyperion/**/*.ts', // include hyperion module for code coverage
                'src/main/webapp/app/communication/**/*.ts', // include all communication module for code coverage
                'src/main/webapp/app/notification/**/*.ts', // include all notification module for code coverage
                'src/main/webapp/app/shared-ui/search-filter/**/*.ts', // include search-filter component for code coverage
                'src/main/webapp/app/programming/**/*.ts', // include entire programming module for code coverage
                'src/main/webapp/app/shared-ui/detail-overview-list/components/programming-diff-report-detail/**/*.ts', // include programming diff report detail for code coverage
                'src/main/webapp/app/logos/**/*.ts', // include logos for code coverage
                'src/main/webapp/app/sharing/**/*.ts', // include sharing for code coverage
                'src/main/webapp/app/app.component.ts', // include app-shell (app.component) for code coverage
                'src/main/webapp/app/plagiarism/**/*.ts', // include all plagiarism for code coverage
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
                // Floors set just below current actuals to absorb Jest→Vitest migration drift; re-tune when migration completes.
                lines: 88.8,
                statements: 88.7,
                branches: 73.6,
                functions: 86.0,
            },
        },
    },
});
