const esModules = ['ngx-treeview', 'lodash-es', 'franc-min', 'trigram-utils', 'n-gram', 'collapse-white-space'].join('|');

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
            diagnostics: {
                ignoreCodes: [151001],
            },
        },
    },
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
            statements: 77.4,
            branches: 58.7,
            functions: 67.1,
            lines: 76.9,
        },
    },
    setupFilesAfterEnv: ['<rootDir>/src/test/javascript/spec/jest-test-setup.ts', 'jest-sinon'],
    transformIgnorePatterns: [`/node_modules/(?!${esModules})`],
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
    },
};
