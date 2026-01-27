const esModules = [
    '@angular/animations',
    '@angular/cdk',
    '@angular/common',
    '@angular/compiler',
    '@angular/core',
    '@angular/forms',
    '@angular/localize',
    '@angular/material',
    '@angular/platform-browser-dynamic',
    '@angular/platform-browser',
    '@angular/router',
    '@angular/service-worker',
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
    'internmap',
    'lodash-es',
    'markdown-it-github-alerts',
    'monaco-editor',
    'n-gram',
    'ngx-device-detector',
    'ngx-infinite-scroll',
    'primeng',
    'rxjs/operators',
    'trigram-utils',
].join('|');

const {
    compilerOptions: { baseUrl = './' },
} = require('./tsconfig.json');

module.exports = {
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
        '!<rootDir>/src/main/webapp/**/*.module.ts',        // ignore modules files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.routes.ts',        // ignore routes files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.route.ts',         // ignore route files because they cannot be properly tested
        '!<rootDir>/**/node_modules/**',
        '!<rootDir>/src/main/webapp/app/openapi/**',        // ignore openapi files because they are generated
        '!<rootDir>/src/main/webapp/app/assessment/**',     // assessment module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/buildagent/**',     // buildagent module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/core/account/**',   // core account module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/core/admin/**',     // core admin module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/fileupload/**',     // fileupload module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/lecture/**',        // lecture module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/modeling/**',       // modeling module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/quiz/**',           // quiz module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/text/**',           // text module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/tutorialgroup/**',  // tutorialgroup module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/lti/**',            // lti module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/iris/**',           // iris module uses Vitest (see vitest.config.ts)
        '!<rootDir>/src/main/webapp/app/core/course/manage/**', // core course manage module uses Vitest (see vitest.config.ts)
    ],
    coveragePathIgnorePatterns: [
        '<rootDir>/src/main/webapp/app/core/config/prod.config.ts',
        '<rootDir>/src/main/webapp/app/openapi/',
        '<rootDir>/src/main/webapp/app/assessment/',        // assessment module uses Vitest
        '<rootDir>/src/main/webapp/app/buildagent/',        // buildagent module uses Vitest
        '<rootDir>/src/main/webapp/app/core/account/',      // core account module uses Vitest
        '<rootDir>/src/main/webapp/app/core/admin/',        // core admin module uses Vitest
        '<rootDir>/src/main/webapp/app/fileupload/',        // fileupload module uses Vitest
        '<rootDir>/src/main/webapp/app/lecture/',           // lecture module uses Vitest
        '<rootDir>/src/main/webapp/app/modeling/',          // modeling module uses Vitest
        '<rootDir>/src/main/webapp/app/quiz/',              // quiz module uses Vitest
        '<rootDir>/src/main/webapp/app/text/',              // text module uses Vitest
        '<rootDir>/src/main/webapp/app/tutorialgroup/',     // tutorialgroup module uses Vitest
        '<rootDir>/src/main/webapp/app/lti/',               // lti module uses Vitest
        '<rootDir>/src/main/webapp/app/iris/',              // iris module uses Vitest
        '<rootDir>/src/main/webapp/app/core/course/manage/', // core course manage module uses Vitest
    ],
    // Global coverage thresholds for Jest. Modules using Vitest (e.g., fileupload) have their own
    // coverage thresholds in vitest.config.ts. Per-module thresholds are enforced by check-client-module-coverage.mjs
    coverageThreshold: {
        global: {
            statements: 89.4,
            branches: 73.5,
            functions: 83.3,
            lines: 89.5,
        },
    },
    // 'json-summary' reporter is used by supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs
    coverageReporters: ['clover', 'json', 'lcov', 'text-summary', 'json-summary'],
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
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
        '<rootDir>/src/main/webapp/app/assessment/',    // assessment module
        '<rootDir>/src/main/webapp/app/buildagent/',    // buildagent module
        '<rootDir>/src/main/webapp/app/core/account/',  // core account module
        '<rootDir>/src/main/webapp/app/core/admin/',    // core admin module
        '<rootDir>/src/main/webapp/app/fileupload/',    // fileupload module
        '<rootDir>/src/main/webapp/app/lecture/',       // lecture module
        '<rootDir>/src/main/webapp/app/modeling/',      // modeling module
        '<rootDir>/src/main/webapp/app/quiz/',          // quiz module
        '<rootDir>/src/main/webapp/app/text/',          // text module
        '<rootDir>/src/main/webapp/app/tutorialgroup/', // tutorialgroup module
        '<rootDir>/src/main/webapp/app/lti/',           // lti module
        '<rootDir>/src/main/webapp/app/iris/',          // iris module
        '<rootDir>/src/main/webapp/app/core/course/manage/', // core course manage module
    ],
    testTimeout: 3000,
    testMatch: [
        '<rootDir>/src/main/webapp/app/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/**/*.integration.spec.ts'
    ],
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
    },
};
