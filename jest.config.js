require('jest-preset-angular/ngcc-jest-processor');

const esModules = ['lodash-es', 'franc-min', 'trigram-utils', 'n-gram', 'collapse-white-space', '@angular/animations', '@angular/common',
    '@angular/compiler', '@angular/core', '@angular/forms', '@angular/localize', '@angular/platform-browser', '@angular/platform-browser-dynamic', '@angular/router',
    '@ngx-translate/core', '@ngx-translate/http-loader', '@fortawesome/angular-fontawesome', '@angular/cdk', '@ls1intum/apollon',
    'rxjs/operators', '@ng-bootstrap/ng-bootstrap', 'ngx-webstorage', '@ctrl/ngx-emoji-mart', 'ngx-clipboard', 'ngx-device-detector', 'ngx-window-token'].join('|');

const {
    compilerOptions: { paths = {}, baseUrl = './' },
} = require('./tsconfig.json');
const environment = require('./webpack/environment');

module.exports = {
    globals: {
        ...environment,
        'ts-jest': {
            tsconfig: '<rootDir>/tsconfig.spec.json',
            stringifyContentPathRegex: '\\.html$',
            isolatedModules: true,
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
    testRunner: "jest-jasmine2",
    roots: ['<rootDir>', `<rootDir>/${baseUrl}`],
    modulePaths: [`<rootDir>/${baseUrl}`],
    setupFiles: ['jest-date-mock'],
    cacheDirectory: '<rootDir>/build/jest-cache',
    coverageDirectory: '<rootDir>/build/test-results/',
    reporters: ['default', ['jest-junit', { outputDirectory: '<rootDir>/build/test-results/', outputName: 'TESTS-results-jest.xml' }]],
    collectCoverageFrom: ['src/main/webapp/**/*.{js,jsx,ts,tsx}', '!src/main/webapp/**/*.module.{js,jsx,ts,tsx}'],
    coverageThreshold: {
        global: {
            // TODO: in the future, the following values should be increase to at least 80%
            statements: 76.9,
            branches: 64.8,
            functions: 68.7,
            lines: 76.5,
        },
    },
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-sinon', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    resolver: 'jest-preset-angular/build/resolvers/ng-jest-resolver.js',
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
    transform: {
        '^.+\\.(ts|js|mjs|html|svg)$': 'jest-preset-angular',
    },
    modulePathIgnorePatterns: [],
    testTimeout: 2000,
    testMatch: [
        '<rootDir>/src/test/javascript/spec/component/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/directive/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/integration/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/pipe/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/service/**/*.spec.ts',
        '<rootDir>/src/test/javascript/spec/util/**/*.spec.ts',
    ],
    moduleNameMapper: {
        '^app/(.*)': '<rootDir>/src/main/webapp/app/$1',
        'test/(.*)': '<rootDir>/src/test/javascript/spec/$1',
        '@assets/(.*)': '<rootDir>/src/main/webapp/assets/$1',
        '@core/(.*)': '<rootDir>/src/main/webapp/app/core/$1',
        '@env': '<rootDir>/src/main/webapp/environments/environment',
        '@src/(.*)': '<rootDir>/src/src/$1',
        '@state/(.*)': '<rootDir>/src/app/state/$1',
        "^lodash-es$": "lodash"
    },
};
