require('jest-preset-angular/ngcc-jest-processor');

const esModules = ['ngx-treeview', 'lodash-es', 'franc-min', 'trigram-utils', 'n-gram', 'collapse-white-space', '@angular/animations', '@angular/common',
    '@angular/compiler', '@angular/core', '@angular/forms', '@angular/localize', '@angular/platform-browser', '@angular/platform-browser-dynamic', '@angular/router',
    '@ngx-translate/core', '@ngx-translate/http-loader', 'ngx-cookie-service', '@fortawesome/angular-fontawesome', '@angular/cdk',
    'rxjs/operators', '@ng-bootstrap/ng-bootstrap', 'ngx-webstorage', '@ctrl/ngx-emoji-mart', 'franc-min', 'franc'].join('|');

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
            statements: 77.1,
            branches: 63.7,
            functions: 68.2,
            lines: 76.6,
        },
    },
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-sinon', 'jest-extended/all'],
    moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
    resolver: 'jest-preset-angular/build/resolvers/ng-jest-resolver.js',
    transformIgnorePatterns: [`/node_modules/(?!.*\\.m?js$)`],
    transform: {
        '^.+\\.(ts|js|mjs|html|svg)$': 'jest-preset-angular',
    },
    modulePathIgnorePatterns: [],
    testTimeout: 2000,
    testMatch: [
        '<rootDir>/src/test/javascript/spec/component/**/*.ts',
        '<rootDir>/src/test/javascript/spec/directive/**/*.ts',
        '<rootDir>/src/test/javascript/spec/integration/**/*.ts',
        '<rootDir>/src/test/javascript/spec/pipe/**/*.ts',
        '<rootDir>/src/test/javascript/spec/service/**/*.ts',
        '<rootDir>/src/test/javascript/spec/util/**/*.ts',
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
