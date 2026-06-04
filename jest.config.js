const esModules = [
    '@angular/animations',
    '@angular/cdk',
    '@angular/common',
    '@angular/compiler',
    '@angular/core',
    '@angular/forms',
    '@angular/localize',
    '@angular/material',
    '@angular/platform-browser',
    '@angular/platform-browser-dynamic',
    '@angular/router',
    '@angular/service-worker',
    '@angular/youtube-player',
    '@ctrl/ngx-emoji-mart',
    '@danielmoncada/angular-datetime-picker',
    '@fortawesome/angular-fontawesome',
    '@tumaet/apollon',
    '@ng-bootstrap/ng-bootstrap',
    '@ngx-translate/core',
    '@ngx-translate/http-loader',
    '@primeuix',
    '@sentry/angular',
    '@siemens/ngx-datatable',
    '@stomp/rx-stomp',
    '@stomp/stompjs',
    '@swimlane/ngx-charts',
    '@swimlane/ngx-graph',
    'collapse-white-space',
    'd3-array',
    'd3-brush',
    'd3-color',
    'd3-dispatch',
    'd3-drag',
    'd3-ease',
    'd3-force',
    'd3-format',
    'd3-hierarchy',
    'd3-interpolate',
    'd3-path',
    'd3-quadtree',
    'd3-scale',
    'd3-selection',
    'd3-shape',
    'd3-time',
    'd3-transition',
    'dayjs/esm',
    'export-to-csv',
    'franc-min',
    'interactjs',
    'internmap',
    'lodash-es',
    'markdown-it-github-alerts',
    'monaco-editor',
    'n-gram',
    'ngx-device-detector',
    'ngx-extended-pdf-viewer',
    'ngx-infinite-scroll',
    'primeng',
    'rxjs/operators',
    'trigram-utils',
    'uuid',
].join('|');

const {
    compilerOptions: { baseUrl = './' },
} = require('./tsconfig.json');

module.exports = {
    testEnvironment: 'jsdom',
    testEnvironmentOptions: {
        url: 'https://artemis.fake/test',
        globalsCleanup: 'on',
    },
    roots: ['<rootDir>', `<rootDir>/${baseUrl}`],
    modulePaths: [`<rootDir>/${baseUrl}`],
    setupFiles: ['jest-date-mock'],
    cacheDirectory: '<rootDir>/build/jest-cache',
    coverageDirectory: '<rootDir>/build/test-results/jest',
    reporters: [
        'default',
        [
            'jest-junit',
            {
                outputDirectory: '<rootDir>/build/test-results/',
                outputName: 'TESTS-results-jest.xml',
            },
        ],
    ],
    collectCoverageFrom: [
        '<rootDir>/src/main/webapp/**/*.ts',
        '!<rootDir>/**/node_modules/**',
        '!<rootDir>/src/main/webapp/**/*.module.ts', // ignore modules files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.route.ts', // ignore route files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.routes.ts', // ignore routes files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/app/assessment/**', // assessment module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/localci/**', // localci module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/localvc/**', // localvc module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/communication/**', // communication module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/core/**', // core module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/calendar/**', // calendar module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/fileupload/**', // fileupload module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/iris/**', // iris module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/lecture/**', // lecture module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/lti/**', // lti module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/modeling/**', // modeling module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/notification/**', // notification module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/openapi/**', // ignore openapi files because they are generated
        '!<rootDir>/src/main/webapp/app/quiz/**', // quiz module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/text/**', // text module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/tutorialgroup/**', // tutorialgroup module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/atlas/**', // atlas module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/**', // shared-ui module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/students/**', // exam manage students module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/overview/summary/exam-request-ai-feedback-button/**', // exam request AI feedback button uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/student-exams/**', // exam manage student-exams module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/test-runs/**', // exam manage test-runs module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/exercise-groups/**', // exam manage exercise groups module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/suspicious-behavior/**', // exam manage suspicious behavior module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/services/**', // exam manage services module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/exam-management/**', // exam management module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/exam-scores/**', // exam scores module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/exam-status/**', // exam status module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/manage/exams/**', // exam manage exams (detail/import/update/checklist/mode-picker) uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/shared/**', // exam shared module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exam/overview/**', // exam overview module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/foundation/**', // foundation module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/**', // exercise module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/image-cropper/**', // image cropper uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**', // checklist-panel uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.ts', // problem-statement service uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts', // problem-statement utils uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/editor/**', // editor module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/exercise/programming-exercise.component.ts', // programming exercise component uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/hyperion/**', // hyperion module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**', // build phases editor uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/version-history/**', // programming version history module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/search-filter/**', // search-filter component uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/shared/**', // programming shared uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/detail-overview-list/components/programming-diff-report-detail/**', // programming diff report detail uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/additional-feedback/**', // additional feedback uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/assessment-progress-label/**', // assessment progress label uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/course-exercises/**', // course exercise service uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/difficulty-level/**', // difficulty level uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/difficulty-picker/**', // difficulty picker uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exam-exercise-row-buttons/**', // exam exercise row buttons uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/example-solution/**', // example solution uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/example-submission/**', // example submission uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-categories/**', // exercise categories uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-create-buttons/**', // exercise create buttons uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-detail-common-actions/**', // exercise detail common actions uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-headers/**', // exercise headers uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-info/**', // exercise info uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-scores/**', // exercise scores uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-title-channel-name/**', // exercise title channel name uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-title-channel-name-primeng/**', // exercise title channel name PrimeNG uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-update-notification/**', // exercise update notification uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-update-warning/**', // exercise update warning uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/external-submission/**', // external submission uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/feedback/**', // feedback uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/feedback-suggestion/**', // feedback suggestion uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/import/**', // exercise import uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/mode-picker/**', // mode picker uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/participation/**', // participation uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/participation-submission/**', // participation-submission uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/presentation-score/**', // presentation score uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/rating/**', // rating uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/result-history/**', // result history uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/review/**', // review uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/services/**', // exercise services uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/shared/**', // exercise shared uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/statistics/**', // exercise statistics uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/structured-grading-criterion/**', // structured grading criterion uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/submission/**', // submission uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/submission-export/**', // submission export uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/submission-policy/**', // submission policy uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/submission-version/**', // submission version uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/synchronization/**', // exercise synchronization uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/team/**', // team uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/team-config-form-group/**', // team config form group uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/team-submission-sync/**', // team submission sync uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/unreferenced-feedback/**', // unreferenced feedback uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/util/**', // exercise util uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/version-history/**', // exercise version history uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/logos/**', // logos module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/sharing/**', // sharing module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/app.component.ts', // app-shell (app.component) uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/plagiarism/**', // plagiarism module uses Vitest (see vitest.config.ts)
    ],
    // Each entry below excludes a module that has been migrated to Vitest.
    coveragePathIgnorePatterns: [
        '<rootDir>/src/main/webapp/app/account/', // account module uses Vitest
        '<rootDir>/src/main/webapp/app/admin/', // admin module uses Vitest
        '<rootDir>/src/main/webapp/app/assessment/', // assessment module uses Vitest
        '<rootDir>/src/main/webapp/app/localci/', // localci module uses Vitest
        '<rootDir>/src/main/webapp/app/localvc/', // localvc module uses Vitest
        '<rootDir>/src/main/webapp/app/communication/', // communication module uses Vitest
        '<rootDir>/src/main/webapp/app/core/', // core module uses Vitest
        '<rootDir>/src/main/webapp/app/course/', // course module uses Vitest
        '<rootDir>/src/main/webapp/app/calendar/', // calendar module uses Vitest
        '<rootDir>/src/main/webapp/app/fileupload/', // fileupload module uses Vitest
        '<rootDir>/src/main/webapp/app/iris/', // iris module uses Vitest
        '<rootDir>/src/main/webapp/app/lecture/', // lecture module uses Vitest
        '<rootDir>/src/main/webapp/app/lti/', // lti module uses Vitest
        '<rootDir>/src/main/webapp/app/modeling/', // modeling module uses Vitest
        '<rootDir>/src/main/webapp/app/notification/', // notification module uses Vitest
        '<rootDir>/src/main/webapp/app/openapi/',
        '<rootDir>/src/main/webapp/app/quiz/', // quiz module uses Vitest
        '<rootDir>/src/main/webapp/app/text/', // text module uses Vitest
        '<rootDir>/src/main/webapp/app/tutorialgroup/', // tutorialgroup module uses Vitest
        '<rootDir>/src/main/webapp/app/atlas/', // atlas module uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/', // shared-ui module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/students/', // exam manage students module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/overview/summary/exam-request-ai-feedback-button/', // exam request AI feedback button uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/student-exams/', // exam manage student-exams module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/test-runs/', // exam manage test-runs module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/exercise-groups/', // exam manage exercise groups module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/suspicious-behavior/', // exam manage suspicious behavior module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/services/', // exam manage services module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/exam-management/', // exam management module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/exam-scores/', // exam scores module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/exam-status/', // exam status module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/manage/exams/', // exam manage exams uses Vitest
        '<rootDir>/src/main/webapp/app/exam/shared/', // exam shared module uses Vitest
        '<rootDir>/src/main/webapp/app/exam/overview/', // exam overview module uses Vitest
        '<rootDir>/src/main/webapp/app/foundation/', // foundation module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/', // exercise module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.ts',
        '<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts',
        '<rootDir>/src/main/webapp/app/editor/', // editor module uses Vitest (see vitest.config.ts)
        '<rootDir>/src/main/webapp/app/programming/manage/exercise/programming-exercise.component.ts',
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/', // build phases editor uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/', // checklist-panel uses Vitest
        '<rootDir>/src/main/webapp/app/hyperion/', // hyperion module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/version-history/', // programming version history module uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/search-filter/', // search-filter uses Vitest
        '<rootDir>/src/main/webapp/app/programming/shared/', // programming shared uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/detail-overview-list/components/programming-diff-report-detail/', // programming diff report detail uses Vitest
        '<rootDir>/src/main/webapp/app/logos/', // logos module uses Vitest
        '<rootDir>/src/main/webapp/app/sharing/', // sharing module uses Vitest
        '<rootDir>/src/main/webapp/app/app.component.ts', // app-shell (app.component) uses Vitest
        '<rootDir>/src/main/webapp/app/plagiarism/', // plagiarism module uses Vitest
    ],
    // Global coverage thresholds for Jest. Modules using Vitest (e.g., fileupload) have their own
    // coverage thresholds in vitest.config.ts. Per-module thresholds are enforced by check-client-module-coverage.mjs
    // Thresholds track the current Jest-owned baseline while migrated files are covered by Vitest.
    // Per-file coverage is unchanged — migrated specs still cover the same files under Vitest.
    // Re-tune when migration completes.
    coverageThreshold: {
        global: {
            // Floors drop as modules migrate out of Jest (each removes well-covered files from the denominator).
            // Last full run: statements 82.37, branches 74.69, functions 68.49, lines 83.8 — floors set ~0.4-0.5pp below.
            statements: 81.9,
            branches: 72.9,
            functions: 68,
            lines: 83.4,
        },
    },
    // 'json-summary' reporter is used by supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs
    coverageReporters: ['clover', 'json', 'lcov', 'text-summary', 'json-summary'],
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    // Under pnpm's symlinked layout, dep files live at
    //   node_modules/.pnpm/<pkg>@<ver>/node_modules/<pkg>/...
    // Jest follows the symlinks to those real paths before checking transformIgnorePatterns,
    // so the original npm-flat negative-lookahead pattern (`node_modules/(?!${esModules})`)
    // was rejecting Angular/d3/swimlane ESM files inside .pnpm/ and producing
    // `Unexpected token 'export'` SyntaxErrors. Authoring a pattern that captures
    // both layouts proved brittle in the joined regex Jest constructs internally,
    // so we transform every file in node_modules. Cost: longer cold runs (~10-15s
    // overhead on a full suite) for guaranteed correctness with the symlink layout.
    transformIgnorePatterns: [],
    transform: {
        '^.+\\.(ts|js|mjs|html|svg)$': [
            'jest-preset-angular',
            {
                tsconfig: '<rootDir>/tsconfig.spec.json',
                stringifyContentPathRegex: '\\.html$',
                diagnostics: {
                    ignoreCodes: [151001],
                },
            },
        ],
    },
    modulePathIgnorePatterns: ['<rootDir>/src/main/resources/templates/', '<rootDir>/build/'],
    // Exclude modules migrated to Vitest (see vitest.config.ts)
    testPathIgnorePatterns: [
        '<rootDir>/src/main/webapp/app/account/', // account module
        '<rootDir>/src/main/webapp/app/admin/', // admin module
        '<rootDir>/src/main/webapp/app/assessment/', // assessment module
        '<rootDir>/src/main/webapp/app/localci/', // localci module
        '<rootDir>/src/main/webapp/app/localvc/', // localvc module
        '<rootDir>/src/main/webapp/app/communication/', // communication module
        '<rootDir>/src/main/webapp/app/core/', // core module
        '<rootDir>/src/main/webapp/app/course/', // course module
        '<rootDir>/src/main/webapp/app/calendar/', // calendar module
        '<rootDir>/src/main/webapp/app/fileupload/', // fileupload module
        '<rootDir>/src/main/webapp/app/iris/', // iris module
        '<rootDir>/src/main/webapp/app/lecture/', // lecture module
        '<rootDir>/src/main/webapp/app/lti/', // lti module
        '<rootDir>/src/main/webapp/app/modeling/', // modeling module
        '<rootDir>/src/main/webapp/app/notification/', // notification module
        '<rootDir>/src/main/webapp/app/quiz/', // quiz module
        '<rootDir>/src/main/webapp/app/text/', // text module
        '<rootDir>/src/main/webapp/app/tutorialgroup/', // tutorialgroup module
        '<rootDir>/src/main/webapp/app/atlas/', // atlas module
        '<rootDir>/src/main/webapp/app/exam/manage/students/', // exam manage students module
        '<rootDir>/src/main/webapp/app/exam/overview/summary/exam-request-ai-feedback-button/', // exam request AI feedback button
        '<rootDir>/src/main/webapp/app/exam/manage/student-exams/', // exam manage student-exams module
        '<rootDir>/src/main/webapp/app/exam/manage/test-runs/', // exam manage test-runs module
        '<rootDir>/src/main/webapp/app/exam/manage/exercise-groups/', // exam manage exercise groups module
        '<rootDir>/src/main/webapp/app/exam/manage/suspicious-behavior/', // exam manage suspicious behavior module
        '<rootDir>/src/main/webapp/app/exam/manage/services/', // exam manage services module
        '<rootDir>/src/main/webapp/app/exam/manage/exam-management/', // exam management module (vitest)
        '<rootDir>/src/main/webapp/app/exam/manage/exam-scores/', // exam scores module (vitest)
        '<rootDir>/src/main/webapp/app/exam/manage/exam-status/', // exam status module (vitest)
        '<rootDir>/src/main/webapp/app/exam/manage/exams/', // exam manage exams (detail/import/update/checklist/mode-picker) (vitest)
        '<rootDir>/src/main/webapp/app/exam/shared/', // exam shared module (vitest)
        '<rootDir>/src/main/webapp/app/exam/overview/', // exam overview module (vitest)
        '<rootDir>/src/main/webapp/app/shared-ui/', // shared-ui module (Vitest)
        '<rootDir>/src/main/webapp/app/foundation/', // foundation module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/editor/', // editor module migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/exercise/programming-exercise.component.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/exercise/', // exercise module
        '<rootDir>/src/main/webapp/app/hyperion/', // hyperion module
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/', // checklist-panel (vitest)
        '<rootDir>/src/main/webapp/app/programming/manage/version-history/', // programming version history module
        '<rootDir>/src/main/webapp/app/shared-ui/search-filter/', // search-filter (Vitest)
        '<rootDir>/src/main/webapp/app/programming/shared/', // programming shared uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/detail-overview-list/components/programming-diff-report-detail/', // programming diff report detail uses Vitest
        '<rootDir>/src/test/javascript/spec/integration/code-editor/code-editor-container.integration.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/logos/', // logos module (Vitest)
        '<rootDir>/src/main/webapp/app/sharing/', // sharing module (Vitest)
        '<rootDir>/src/main/webapp/app/app.component.spec.ts', // app-shell (app.component) (Vitest)
        '<rootDir>/src/main/webapp/app/plagiarism/', // plagiarism module (vitest)
    ],
    testTimeout: 3000,
    testMatch: ['<rootDir>/src/main/webapp/app/**/*.spec.ts', '<rootDir>/src/test/javascript/spec/**/*.integration.spec.ts'],
    moduleNameMapper: {
        '^app/(.*)': '<rootDir>/src/main/webapp/app/$1',
        '^test/(.*)': '<rootDir>/src/test/javascript/spec/$1',
        '@assets/(.*)': '<rootDir>/src/main/webapp/assets/$1',
        '@core/(.*)': '<rootDir>/src/main/webapp/app/core/$1',
        '@env': '<rootDir>/src/main/webapp/environments/environment',
        '@src/(.*)': '<rootDir>/src/src/$1',
        '@state/(.*)': '<rootDir>/src/app/state/$1',
        '^lodash-es$': 'lodash',
        '\\.css$': '<rootDir>/src/test/javascript/spec/stub.js',
        '^monaco-editor$': '<rootDir>/node_modules/monaco-editor/esm/vs/editor/editor.api.js',
        '^@tumaet/apollon$': '<rootDir>/node_modules/@tumaet/apollon/dist/index.js',
        '^@tumaet/apollon/(.*)': '<rootDir>/node_modules/@tumaet/apollon/$1',
    },
};
