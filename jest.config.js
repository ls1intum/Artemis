const esModules = [
    '@angular/animations',
    '@angular/cdk',
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
    '@siemens/ngx-datatable',
    '@fortawesome/angular-fontawesome',
    '@ls1intum/apollon',
    '@ng-bootstrap/ng-bootstrap',
    '@ngx-translate/core',
    '@ngx-translate/http-loader',
    '@sentry/angular',
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
    'monaco-editor',
    'n-gram',
    'ngx-device-detector',
    'ngx-infinite-scroll',
    'ngx-webstorage',
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
        'src/main/webapp/**/*.ts',
        '!src/main/webapp/**/*.module.ts',  // ignore modules files because they cannot be properly tested
        '!src/main/webapp/**/*.routes.ts',   // ignore routes files because they cannot be properly tested
        '!src/main/webapp/**/*.route.ts',   // ignore route files because they cannot be properly tested
        '!**/node_modules/**',
    ],
    coveragePathIgnorePatterns: [
        'src/main/webapp/app/core/config/prod.config.ts',
    ],
    coverageThreshold: {
        global: {
            // TODO: in the future, the following values should increase to at least 90%
            statements: 88.72,
            branches: 74.4,
            functions: 82.87,
            lines: 88.76,
        },
    },
    coverageReporters: ['clover', 'json', 'lcov', 'text-summary'],
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
    transform: {
        '^.+\\.(ts|js|mjs|html|svg)$': [
            'jest-preset-angular',
            {
                tsconfig: '<rootDir>/tsconfig.spec.json',
                stringifyContentPathRegex: '\\.html$',
                isolatedModules: true,
                diagnostics: {
                    ignoreCodes: [151001],
                },
            },
        ],
    },
    modulePathIgnorePatterns: ['<rootDir>/src/main/resources/templates/', '<rootDir>/build/'],
    testTimeout: 3000,
    testMatch: [
        '<rootDir>/src/test/javascript/spec/component/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/directive/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/entities/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/integration/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/pipe/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/service/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/util/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/interceptor/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/config/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/core/**/*.spec.ts',
    ],
    moduleNameMapper: {
        '^app/(.*)': '<rootDir>/src/main/webapp/app/$1',
        'test/(.*)': '<rootDir>/src/test/javascript/spec/$1',
        '@assets/(.*)': '<rootDir>/src/main/webapp/assets/$1',
        '@core/(.*)': '<rootDir>/src/main/webapp/app/core/$1',
        '@env': '<rootDir>/src/main/webapp/environments/environment',
        '@src/(.*)': '<rootDir>/src/src/$1',
        '@state/(.*)': '<rootDir>/src/app/state/$1',
        '^lodash-es$': 'lodash',
        '\\.css$': '<rootDir>/stub.js',
        '^monaco-editor$': '<rootDir>/node_modules/monaco-editor/esm/vs/editor/editor.api.js',
    },
};
