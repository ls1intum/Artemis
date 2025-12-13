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
    '@ls1intum/apollon',
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
    },
    roots: ['<rootDir>', `<rootDir>/${baseUrl}`],
    modulePaths: [`<rootDir>/${baseUrl}`],
    setupFiles: ['jest-date-mock'],
    cacheDirectory: '<rootDir>/build/jest-cache',
    coverageDirectory: '<rootDir>/build/test-results/',
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
        '!<rootDir>/src/main/webapp/**/*.module.ts',  // ignore modules files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.routes.ts',   // ignore routes files because they cannot be properly tested
        '!<rootDir>/src/main/webapp/**/*.route.ts',   // ignore route files because they cannot be properly tested
        '!<rootDir>/**/node_modules/**',
        '!<rootDir>/src/main/webapp/app/openapi/**', // ignore openapi files because they are generated
    ],
    coveragePathIgnorePatterns: [
        '<rootDir>/src/main/webapp/app/core/config/prod.config.ts',
        '<rootDir>/src/main/webapp/app/openapi/',
    ],
    coverageThreshold: {
        global: {
            statements: 90.20,
            branches: 76.40,
            functions: 84.30,
            lines: 90.30,
        },
    },
    // 'json-summary' reporter is used by supporting_scripts/code-coverage/module-coverage-client/check-client-module-coverage.mjs
    coverageReporters: ['clover', 'json', 'lcov', 'text-summary','json-summary'],
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
    testPathIgnorePatterns: ['<rootDir>/src/main/webapp/app/fileupload/'],
    testTimeout: 3000,
    testMatch: ['<rootDir>/src/main/webapp/app/**/*.spec.ts',
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
        '^@ls1intum/apollon$': '<rootDir>/node_modules/@ls1intum/apollon/lib/es6/index.js', // adjust if the package.json "exports" points elsewhere
        '^@ls1intum/apollon/lib/es6/(.*)': '<rootDir>/node_modules/@ls1intum/apollon/lib/es6/$1',
    },
};
