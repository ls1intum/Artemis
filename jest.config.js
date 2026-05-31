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
        '!<rootDir>/src/main/webapp/app/shared-ui/table-view/**', // table view module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/components/buttons/**', // buttons module uses Vitest (see vitest.config.ts)
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
        '!<rootDir>/src/main/webapp/app/shared-ui/user-import/util/**', // user import utils use Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/range-slider/**', // range slider uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/dashboards/**', // dashboards uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/image-cropper/**', // image cropper uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-headers/**', // exercise headers module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/synchronization/**', // exercise synchronization module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/version-history/**', // exercise version history module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/review/**', // review comment module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/**', // checklist-panel uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.ts', // problem-statement service uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts', // problem-statement utils uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/editor/monaco-editor/inline-refinement-button/', // inline-refinement-button uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/hyperion/**', // hyperion module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/**', // build phases editor uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/manage/version-history/**', // programming version history module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/participation/**', // participation module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/participation-submission/**', // participation-submission module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/exercise-scores/**', // exercise-scores module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/exercise/shared/filter-dropdown/**', // filter-dropdown component uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/shared-ui/search-filter/**', // search-filter component uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/shared/services/build-phases-template.service.ts', // build-phases-template uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/shared/services/legacy-build-plan-converter.service.ts', // legacy converter uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/shared/entities/build-plan-phases.model.ts', // build-plan-phases model uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/programming/shared/programming-exercise-update-timeline/**', // programming exercise update timeline uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/logos/**', // logos module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/sharing/**', // sharing module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/app.component.ts', // app-shell (app.component) uses Vitest (see vitest.config.ts)
        '<rootDir>/src/main/webapp/**/*.ts',
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
        '<rootDir>/src/main/webapp/app/shared-ui/components/buttons/', // buttons module uses Vitest
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
        '<rootDir>/src/main/webapp/app/shared-ui/user-import/util/', // user import utils use Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/table-view/', // table view module uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/range-slider/', // range slider uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/dashboards/', // dashboards uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/image-cropper/', // image cropper uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.ts',
        '<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.ts',
        '<rootDir>/src/main/webapp/app/editor/monaco-editor/inline-refinement-button/',
        '<rootDir>/src/main/webapp/app/exercise/exercise-headers/', // exercise headers module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/synchronization/', // exercise synchronization module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/', // build phases editor uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/version-history/', // exercise version history module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/review/', // review comment module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/', // checklist-panel uses Vitest
        '<rootDir>/src/main/webapp/app/hyperion/', // hyperion module uses Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/version-history/', // programming version history module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/participation/', // participation module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/participation-submission/', // participation-submission module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/exercise-scores/', // exercise-scores module uses Vitest
        '<rootDir>/src/main/webapp/app/exercise/shared/filter-dropdown/', // filter-dropdown component uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/search-filter/', // search-filter uses Vitest
        '<rootDir>/src/main/webapp/app/programming/shared/services/build-phases-template.service.ts',
        '<rootDir>/src/main/webapp/app/programming/shared/services/legacy-build-plan-converter.service.ts',
        '<rootDir>/src/main/webapp/app/programming/shared/entities/build-plan-phases.model.ts',
        '<rootDir>/src/main/webapp/app/programming/shared/programming-exercise-update-timeline/',
        '<rootDir>/src/main/webapp/app/logos/', // logos module uses Vitest
        '<rootDir>/src/main/webapp/app/sharing/', // sharing module uses Vitest
        '<rootDir>/src/main/webapp/app/app.component.ts', // app-shell (app.component) uses Vitest
    ],
    // Global coverage thresholds for Jest. Modules using Vitest (e.g., fileupload) have their own
    // coverage thresholds in vitest.config.ts. Per-module thresholds are enforced by check-client-module-coverage.mjs
    // Lowered ~0.5pp below current actuals to absorb further Jest→Vitest migration drift.
    // Per-file coverage is unchanged — migrated specs still cover the same files under Vitest.
    // Re-tune when migration completes.
    coverageThreshold: {
        global: {
            statements: 83,
            branches: 72.9,
            // Lowered (72.2 -> 72) after moving the well-covered foundation module fully out of Jest (now under Vitest),
            // which removed those functions from the Jest denominator and nudged the global average down.
            functions: 72,
            lines: 84,
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
        '<rootDir>/src/main/webapp/app/shared-ui/components/buttons/', // shared/buttons components
        '<rootDir>/src/main/webapp/app/shared-ui/table-view/', // shared/table-view component
        '<rootDir>/src/main/webapp/app/foundation/', // foundation module uses Vitest
        '<rootDir>/src/main/webapp/app/shared-ui/user-import/util/', // user import utils
        '<rootDir>/src/main/webapp/app/shared-ui/range-slider/', // range slider (vitest)
        '<rootDir>/src/main/webapp/app/exercise/dashboards/', // dashboards (vitest)
        '<rootDir>/src/main/webapp/app/shared-ui/image-cropper/', // image cropper (vitest)
        '<rootDir>/src/main/webapp/app/programming/manage/services/problem-statement.service.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/shared/problem-statement.utils.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/editor/monaco-editor/inline-refinement-button/', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/exercise/exercise-headers/', // exercise headers module
        '<rootDir>/src/main/webapp/app/exercise/synchronization/', // exercise synchronization module
        '<rootDir>/src/main/webapp/app/exercise/version-history/', // exercise version history module
        '<rootDir>/src/main/webapp/app/exercise/review/', // review comment module
        '<rootDir>/src/main/webapp/app/hyperion/', // hyperion module
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/manage/update/update-components/problem/checklist-panel/', // checklist-panel (vitest)
        '<rootDir>/src/main/webapp/app/programming/manage/version-history/', // programming version history module
        '<rootDir>/src/main/webapp/app/exercise/participation/', // participation module (Vitest)
        '<rootDir>/src/main/webapp/app/exercise/participation-submission/', // participation-submission module (Vitest)
        '<rootDir>/src/main/webapp/app/exercise/exercise-scores/', // exercise-scores module (Vitest)
        '<rootDir>/src/main/webapp/app/exercise/shared/filter-dropdown/', // filter-dropdown component (Vitest)
        '<rootDir>/src/main/webapp/app/shared-ui/search-filter/', // search-filter (Vitest)
        '<rootDir>/src/main/webapp/app/programming/shared/services/legacy-build-plan-converter.service.spec.ts', // implemented with Vitest
        '<rootDir>/src/main/webapp/app/programming/shared/services/build-phases-template.service.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/shared/entities/build-plan-phases.model.spec.ts', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/programming/shared/programming-exercise-update-timeline/', // migrated to Vitest
        '<rootDir>/src/main/webapp/app/logos/', // logos module (Vitest)
        '<rootDir>/src/main/webapp/app/sharing/', // sharing module (Vitest)
        '<rootDir>/src/main/webapp/app/app.component.spec.ts', // app-shell (app.component) (Vitest)
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
